package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.service.GeoNetworkServiceImpl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeoNetworkSearchConfig {

    @Bean(name = "gn4ElasticsearchClient")
    public ElasticsearchClient createGN4ElasticsearchClient(@Qualifier("gn4ElasticRestClient") RestClient restClient) {

        RestClientTransport c = new RestClientTransport(restClient, new JacksonJsonpMapper());

        // Create the API client
        return new ElasticsearchClient(c);
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
                        new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                })
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(GeoNetworkServiceImpl.class)
    public GeoNetworkServiceImpl createGeoNetworkServiceImpl(
            @Value("${geonetwork.host}") String server,
            @Value("${geonetwork.search.api.index}") String indexName,
            @Qualifier("gn4ElasticsearchClient") ElasticsearchClient gn4ElasticsearchClient,
            RestTemplate indexerRestTemplate) {

        return new GeoNetworkServiceImpl(server, indexName, gn4ElasticsearchClient, indexerRestTemplate);
    }
}
