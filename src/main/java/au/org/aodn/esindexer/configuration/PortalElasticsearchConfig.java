package au.org.aodn.esindexer.configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortalElasticsearchConfig {

    @Value("${elasticsearch.serverUrl}")
    private String serverUrl;

    @Value("${elasticsearch.apiKey}")
    private String apiKey;

    @Bean(name = "portalElasticsearchClient")
    public ElasticsearchClient geoNetworkElasticsearchClient() {
        // Create the low-level client
        RestClient restClient = RestClient
            .builder(HttpHost.create(serverUrl))
            .setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "ApiKey " + apiKey)
            })
            .build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        // Create the API client
        return new ElasticsearchClient(transport);
    }
}
