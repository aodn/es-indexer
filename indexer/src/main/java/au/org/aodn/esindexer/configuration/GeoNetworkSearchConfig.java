package au.org.aodn.esindexer.configuration;

import au.org.aodn.metadata.geonetwork.service.FIFOCache;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import au.org.aodn.metadata.geonetwork.utils.UrlUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransportBase;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

@Configuration
public class GeoNetworkSearchConfig {

    @Bean(name = "gn4ElasticsearchClient")
    public ElasticsearchClient createGN4ElasticsearchClient(@Qualifier("gn4ElasticRestClient") RestClient restClient) throws NoSuchFieldException, IllegalAccessException {

        RestClientTransport c = new RestClientTransport(restClient, new JacksonJsonpMapper());
        /*
         TODO: You may need to revisit this setup when elastic-java-client upgrade
         Fix due to elastic search api update
         Need a hack to work around an issue with geonetwork, geonetwork exposed the ElasticSearch api via endpoint _search
         however this is a proxy to the underlying api and unfortunately the proxy do not populate all the header back, namely the
         "X-Elastic-Product", which is need in the elastic client api check. This will cause the fail check in the
         ElasticTransportBase.checkProductHeader.

         To workaround it you need to set the endpointsMsssingProductHeader with value "es/search"
        */
        Field endpointsMissingProductHeader = ElasticsearchTransportBase.class.getDeclaredField("endpointsMissingProductHeader");

        endpointsMissingProductHeader.setAccessible(true);
        Set<String> v = (Set<String>)endpointsMissingProductHeader.get(c);
        v.add("es/search");

        // Create the API client, the transport options is needed because the header from the default elastic client
        // call is application/vnd.elasticsearch.... which is not something the geonetwork wants. So override it here
        // otherwise the function call will always fail due to header value.
        return new ElasticsearchClient(c)
                .withTransportOptions(
                        f -> f.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                );
    }

    @Bean(name = "gn4ElasticRestClient")
    @ConditionalOnMissingBean(name = "gn4ElasticRestClient")
    public RestClient createRestClientTransport1(@Value("${geonetwork.host}") String host,
                                                          @Value("${geonetwork.search.api.endpoint}") String path) {
        // Create the low-level client
        return RestClient
                .builder(HttpHost.create(host))
                .setPathPrefix(path)
                .setDefaultHeaders(new BasicHeader[] {
                        new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                        new BasicHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                })
                // Avoid issue 2024-08-25 07:17:25.862 WARN org.apache.http.client.protocol.ResponseProcessCookies -
                // Invalid cookie header: "Set-Cookie: AWSALB=R21FGZ5zfcmfEoTzPXcvYYgIVrPX5I7qmbzhltwyuGTQLQ5jrn9uvU8
                // spUPEFELYK1yZQLtfaQoLBu/tE451zrEaTlD5L6kaSnPvkR+OrhljaMAyG2cHhuiwtRxS;
                // Expires=Sun, 01 Sep 2024 07:17:25 GMT; Path=/".
                // Invalid 'expires' attribute: Sun, 01 Sep 2024 07:17:25 GMT
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setCookieSpec(CookieSpecs.STANDARD)
                                .build()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(GeoNetworkServiceImpl.class)
    public GeoNetworkServiceImpl createGeoNetworkServiceImpl(
            @Value("${geonetwork.host}") String server,
            @Value("${geonetwork.search.api.index}") String indexName,
            @Qualifier("gn4ElasticsearchClient") ElasticsearchClient gn4ElasticsearchClient,
            RestTemplate indexerRestTemplate,
            FIFOCache<String, Map<String, ?>> cache) {

        return new GeoNetworkServiceImpl(
                server,
                indexName,
                gn4ElasticsearchClient,
                indexerRestTemplate,
                cache
        );
    }

    @Bean
    @ConditionalOnMissingBean(UrlUtils.class)
    public UrlUtils createUrlUtils() {
        return new UrlUtils();
    }
    /**
     * This cache is use to reduce load to query geonetwork
     * @return - A first in first out cache
     */
    @Bean
    public FIFOCache<String, Map<String, ?>> createFIFOCache(
            @Value("${geonetwork.search.fifoCacheSize:50}") int cacheSize) {
        return new FIFOCache<>(cacheSize);
    }
}
