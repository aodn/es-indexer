package au.org.aodn.esindexer.configuration;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.junit.Rule;
import org.mockito.Mockito;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class GeoNetworkSearchTestConfig {

    static final DockerImageName MOCKSERVER_IMAGE = DockerImageName
            .parse("mockserver/mockserver")
            .withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion());

    @Rule
    protected MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE);

    @Bean(name = "gn4ElasticTransport")
    public RestClientTransport createRestClientTransport1(@Value("${geonetwork.search.api.endpoint}") String path) {
        // Create the low-level client
        RestClient restClient = RestClient
                .builder(HttpHost.create(String.format("%s:%s", mockServer.getHost(), mockServer.getServerPort())))
                .setPathPrefix(path)
                .setDefaultHeaders(new BasicHeader[] {
                        new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                })
                .build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }


}