package au.org.aodn.esindexer.configuration;

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

@Configuration
public class GeoNetworkSearchConfig {

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
                .setDefaultHeaders(new BasicHeader[] {
                        new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                })
                .build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }
}
