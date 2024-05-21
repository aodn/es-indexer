package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.service.GeoNetworkServiceImpl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Configuration
public class GeoNetworkSearchTestConfig {

    public static final String GN_ELASTIC_NAME = "elastic";
    public static final String GN_NAME = "geonetwork";

    public static final int GN_ELASTIC_PORT = 9200;
    public static final int GN_PORT = 8080;

    @Bean
    public DockerComposeContainer createCompose() {
        // ES is set to use password for authenticate, hence if your connection to "/" for ES will result in 401
        // if server start correctly
        DockerComposeContainer container = new DockerComposeContainer(new File("src/test/resources/compose-gn4-test.yml"))
                // Technically we do not need the Elastic port expose as query is done via geonetwork. Here
                // we just use the port for wait purpose
                .withExposedService(GN_ELASTIC_NAME, 1, GN_ELASTIC_PORT,
                        Wait.forHttp("/").forStatusCode(401).withStartupTimeout(Duration.ofMinutes(5)))
                .withExposedService(GN_NAME, 1, GN_PORT,
                        Wait.forHttp("/geonetwork").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

        container.start();
        return container;
    }

    @Bean(name = "gn4ElasticRestClient")
    public RestClient createRestClientTransport1(
            @Value("${geonetwork.search.api.endpoint}") String path,
            DockerComposeContainer dockerComposeContainer) {

        // Create the low-level client, noted the elastic search 7 is behind geonetwork and
        // is therefore elastic api is expose via geonetwork with slightly different search path
        return RestClient
                .builder(HttpHost.create(String.format("%s:%s",
                        dockerComposeContainer.getServiceHost(GN_NAME, GN_PORT),
                        dockerComposeContainer.getServicePort(GN_NAME, GN_PORT))))
                .setPathPrefix(path)
                .setDefaultHeaders(new BasicHeader[] {
                        new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                })
                .build();
    }

    @Bean
    public GeoNetworkServiceImpl createGeoNetworkServiceImpl(
            @Value("${geonetwork.host}") String server,
            @Value("${geonetwork.search.api.index}") String indexName,
            @Qualifier("gn4ElasticsearchClient") ElasticsearchClient gn4ElasticsearchClient) {

        RestTemplate template = Mockito.spy(new RestTemplate());
        GeoNetworkServiceImpl impl = new GeoNetworkServiceImpl(server, indexName, gn4ElasticsearchClient, template);

        // Spy the object and only create fake return on geonetwork extension api we created, this
        // is because the image we use for testing is a generic geonetwork image without any customization
        doAnswer(answer -> {
            Map<String, Object> map = new HashMap<>();
            return ResponseEntity.ok(map);
        })
                .when(template)
                .exchange(
                        eq(impl.getAodnExtRecordsEndpoint()),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        any(ParameterizedTypeReference.class),
                        anyMap());

        return impl;
    }
}
