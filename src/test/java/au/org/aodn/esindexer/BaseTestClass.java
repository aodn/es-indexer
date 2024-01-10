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

import java.io.IOException;
import java.util.*;

public class BaseTestClass {

    protected final Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

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
        return getRequestEntity(Optional.empty(), body);
    }

    protected HttpEntity<String> getRequestEntity(Optional<Map<String, String>> oh, String body) {
        HttpHeaders headers = new HttpHeaders();

        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL, MediaType.TEXT_PLAIN));
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setBasicAuth("admin", "admin");

        if(oh.isPresent()) {
            oh.get()
                    .entrySet()
                    .stream()
                    .forEach(i -> headers.add(i.getKey(), i.getValue()));
        }

        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    protected String getGeoNetworkRecordsInsert() {
        String host = String.format("http://%s:%s/geonetwork/srv/api/records?metadataType=METADATA&transformWith=_none_&group=2&uuidProcessing=OVERWRITE&category=",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT));

        logger.info("Geonetwork url on docker is {}", host);
        return host;
    }
    /**
     * In order to call geonetwork on privileged operation, you need to authenticate with username / password plus this token
     * and the way to get this token is by making a POST call.
     * @return token
     */
    protected <T> ResponseEntity<T> exchangeWithXSRF(String query, HttpMethod method, String body, Class<T> type) {

        HttpEntity<String> requestEntity = getRequestEntity(body);

        ResponseEntity<T> responseEntity  = testRestTemplate
                .exchange(
                        query,
                        method,
                        requestEntity,
                        type
                );

        if(responseEntity.getStatusCode() == HttpStatus.FORBIDDEN) {
            // This is the behavior of geonetwork that you need to setup the session id, once you make the request
            // with correct username password, it will return with a session id
            String set_cookie = responseEntity.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

            // The string in set_cookie will be like XSRF-TOKEN=xxxxx ; Path=/geonetwork
            String xsrfToken = set_cookie.split(";")[0].split("=")[1].trim();
            logger.info("XSRF token {}", xsrfToken);

            // Now you can make the request again with the session id
            Map<String, String> optionalHeaders = new HashMap<>();
            optionalHeaders.put("X-XSRF-TOKEN", xsrfToken);

            requestEntity = getRequestEntity(Optional.of(optionalHeaders), body);

            responseEntity  = testRestTemplate
                    .exchange(
                            query,
                            method,
                            requestEntity,
                            type
                    );
        }

        return responseEntity;
    }

    public void insertMetadataRecords(String xmlContent) throws RestClientException {

        ResponseEntity<String> responseEntity = exchangeWithXSRF(
                getGeoNetworkRecordsInsert(),
                HttpMethod.PUT,
                xmlContent,
                String.class);

        logger.info("insertMetadataRecords -> {}", responseEntity);
        responseEntity.getStatusCode();
    }

}
