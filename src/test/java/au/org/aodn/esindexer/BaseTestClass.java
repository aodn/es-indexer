package au.org.aodn.esindexer;

import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class BaseTestClass {

    protected final Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

    protected String xsrfToken = null;

    @LocalServerPort
    private int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    @Qualifier("portalElasticTransport")
    protected RestClientTransport transport;

    @Autowired
    @Qualifier("portalElasticsearchClient")
    protected ElasticsearchClient client;

    @Autowired
    protected ElasticsearchContainer container;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @Autowired
    protected DockerComposeContainer dockerComposeContainer;

    protected void clearElasticIndex() throws IOException {
        logger.debug("Clear elastic index");
        try {
            client.deleteByQuery(f -> f
                    .index(INDEX_NAME)
                    .query(QueryBuilders.matchAll().build()._toQuery())
            );
            // Must all, otherwise index is not rebuild immediately
            client.indices().refresh();
        }
        catch(ElasticsearchException e) {
            // It is ok to ignore exception if the index is not found
        }
    }

    protected HttpEntity<String> getRequestEntity(String body) {
        return getRequestEntity(Optional.empty(), null, body);
    }

    protected HttpEntity<String> getRequestEntity(Optional<Map<String, String>> oh, MediaType contentType, String body) {
        HttpHeaders headers = new HttpHeaders();

        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL, MediaType.TEXT_PLAIN));
        headers.setContentType(contentType == null ? MediaType.APPLICATION_XML : contentType);
        headers.setCacheControl(CacheControl.empty());

        headers.add(HttpHeaders.USER_AGENT, "TestRestTemplate");
        headers.add(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");

        if(xsrfToken != null) {
            // This is very important and is needed to login geonetwork4, the logic is first you need to
            // do a REST call, it will come back with the XSRF-TOKEN, and subsequence call require
            // the following to be set in order to authenticate correctly
            headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfToken);
            headers.add("X-XSRF-TOKEN", xsrfToken);
        }

        // This is use for test container only, so it is ok to hardcode
        headers.setBasicAuth("admin", "admin");

        if(oh.isPresent()) {
            oh.get()
                    .entrySet()
                    .stream()
                    .forEach(i -> headers.add(i.getKey(), i.getValue()));
        }

        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    protected String getLoginUrl() {
        return String.format("http://%s:%s/geonetwork/srv/eng/info?type=me",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT));

    }

    protected String getGeoNetworkRecordsInsertUrl() {
        String host = String.format("http://%s:%s/geonetwork/srv/api/records?metadataType=METADATA&transformWith=_none_&group=2&uuidProcessing=OVERWRITE&category=",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT));

        logger.info("Geonetwork url on docker is {}", host);
        return host;
    }
    /**
     * Must call to get the XSRF token
     */
    @PostConstruct
    public void login() {
        if(xsrfToken == null) {
            HttpEntity<String> requestEntity = getRequestEntity(Optional.empty(), MediaType.APPLICATION_JSON, null);

            ResponseEntity<String> responseEntity = testRestTemplate
                    .exchange(
                            getLoginUrl(),
                            HttpMethod.POST,
                            requestEntity,
                            String.class
                    );

            if (responseEntity.getStatusCode() == HttpStatus.FORBIDDEN) {

                // This is the behavior of geonetwork that you need to setup the session id, once you make the request
                // with correct username password, it will return with a session id
                String set_cookie = responseEntity.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

                // The string in set_cookie will be like XSRF-TOKEN=xxxxx ; Path=/geonetwork
                xsrfToken = set_cookie.split(";")[0].split("=")[1].trim();
                logger.info("XSRF token {}", xsrfToken);

                HttpEntity<String> re = getRequestEntity(Optional.empty(), MediaType.APPLICATION_JSON, null);

                ResponseEntity<String> answer = testRestTemplate
                        .exchange(
                                getLoginUrl(),
                                HttpMethod.POST,
                                re,
                                String.class
                        );

                assertEquals("Login and get XSRF token", HttpStatus.OK, answer.getStatusCode());
            }
        }
    }

    public void insertMetadataRecords(String xmlContent) throws RestClientException {

        HttpEntity<String> requestEntity = getRequestEntity(Optional.empty(), null, xmlContent);

        ResponseEntity<String> responseEntity = testRestTemplate
                .exchange(
                        getGeoNetworkRecordsInsertUrl(),
                        HttpMethod.PUT,
                        requestEntity,
                        String.class
                );

        logger.info("insertMetadataRecords -> {}", responseEntity);
        responseEntity.getStatusCode();
    }

}
