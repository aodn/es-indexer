package au.org.aodn.esindexer.configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
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

@Configuration
public class ElasticSearchConfig {

    @Bean(name = "portalElasticsearchClient")
    public ElasticsearchClient createElasticsearchClient(@Qualifier("portalElasticTransport") RestClientTransport transport) {
        // Create the API client
        return new ElasticsearchClient(transport);
    }

    @Bean(name = "portalElasticTransport")
    @ConditionalOnMissingBean(name = "portalElasticTransport")
    public RestClientTransport createRestClientTransport(@Value("${elasticsearch.serverUrl}") String serverUrl,
                                                         @Value("${elasticsearch.apiKey}") String apiKey) {
        // Create the low-level client
        RestClient restClient = RestClient
                .builder(HttpHost.create(serverUrl))
                .setCompressionEnabled(true)
                .setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
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

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }
}
