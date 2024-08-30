package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.service.FIFOCache;
import au.org.aodn.esindexer.service.GeoNetworkServiceImpl;
import au.org.aodn.esindexer.utils.UrlUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
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
            @Qualifier("gn4ElasticsearchClient") ElasticsearchClient gn4ElasticsearchClient,
            FIFOCache<String, Map<String, ?>> cache) {

        RestTemplate template = Mockito.spy(new RestTemplate());
        GeoNetworkServiceImpl impl = new GeoNetworkServiceImpl(
                server,
                indexName,
                gn4ElasticsearchClient,
                template,
                cache
        );

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Spy the object and only create fake return on geonetwork extension api we created, this
        // is because the image we use for testing is a generic geonetwork image without any customization
        doAnswer((answer) -> {
            if (answer.getArgument(4) instanceof Map<?, ?>) {
                Map<String, Object> param = answer.getArgument(4);

                log.debug("Request mock record info for record uuid {}", param.get(GeoNetworkServiceImpl.UUID));
                if(param.containsKey(GeoNetworkServiceImpl.UUID)) {
                    try {
                        String json = BaseTestClass.readResourceFile(
                                String.format(
                                        "classpath:canned/extrainfo/%s.json",
                                        param.get(GeoNetworkServiceImpl.UUID)
                                )
                        );

                        return ResponseEntity.ok(
                                objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {})
                        );
                    }
                    catch(FileNotFoundException fileNotFoundException) {
                        return ResponseEntity.notFound().<Map<String, Object>>build();
                    }
                }
            }
            return ResponseEntity.notFound().<Map<String, Object>>build();
        })
                .when(template)
                .exchange(
                        argThat(s -> s.contains("/geonetwork/srv/api/aodn/records/") && s.endsWith("/info")),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        isA(ParameterizedTypeReference.class),
                        anyMap());

        doAnswer((answer) -> {
            if (answer.getArgument(4) instanceof Map<?, ?>) {
                Map<String, Object> param = answer.getArgument(4);

                log.debug("Request mock related info for record uuid {}", param.get(GeoNetworkServiceImpl.UUID));
                if(param.containsKey(GeoNetworkServiceImpl.UUID)) {
                    try {
                        String json = BaseTestClass.readResourceFile(
                                String.format(
                                        "classpath:canned/related/%s.json",
                                        param.get(GeoNetworkServiceImpl.UUID)
                                )
                        );

                        return ResponseEntity.ok(
                                objectMapper.readValue(json, Map.class)
                        );
                    }
                    catch(FileNotFoundException fileNotFoundException) {
                        return ResponseEntity.notFound().<Map<?,?>>build();
                    }
                }
            }
            return ResponseEntity.notFound().<Map<?,?>>build();
        })
                .when(template)
                .exchange(
                        argThat(a -> a.contains("/geonetwork/srv/api/records/") && a.endsWith("/related")),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(Map.class),
                        anyMap());

        return impl;
    }
    /**
     * Hardcode the result here for testing, please add more if you need to return different result
     * @return
     */
    @Bean
    public UrlUtils createUrlUtils() {
        UrlUtils urlUtils = Mockito.mock(UrlUtils.class);

        final Map<String, Boolean> status = new HashMap<>();
        status.put("https://catalogue-imos.aodn.org.au/geonetwork/images/logos/dbee258b-8730-4072-96d4-2818a69a4afd.png", Boolean.TRUE);

        doAnswer(answer -> {
            String url = answer.getArgument(0);
            return status.getOrDefault(url, Boolean.FALSE);
        })
                .when(urlUtils)
                .checkUrlExists(anyString());

        return urlUtils;
    }
}
