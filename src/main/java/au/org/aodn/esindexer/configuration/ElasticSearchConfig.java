package au.org.aodn.esindexer.configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
            .setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "ApiKey " + apiKey)
            })
            .build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean(name = "gn4ElasticsearchClient")
    public ElasticsearchClient createGN4ElasticsearchClient(@Qualifier("gn4ElasticTransport") RestClientTransport transport) {
        // Create the API client
        return new ElasticsearchClient(transport);
    }

    @Bean(name = "gn4ElasticTransport")
    @ConditionalOnMissingBean(name = "gn4ElasticTransport")
    public RestClientTransport createRestClientTransport1(@Value("${geonetwork.host}") String host,
                                                          @Value("${geonetwork.search.api.endpoint}") String path) {
        // Create the low-level client
        RestClient restClient = RestClient
                .builder(HttpHost.create(host))
                .setPathPrefix(path)
                .build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }
}
