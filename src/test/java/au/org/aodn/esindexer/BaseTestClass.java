package au.org.aodn.esindexer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;

public class BaseTestClass {

    protected Logger logger = LoggerFactory.getLogger(BaseTestClass.class);

    @LocalServerPort
    private int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    protected RestClientTransport transport;

    @Autowired
    protected ElasticsearchClient client;

    @Autowired
    protected ElasticsearchContainer container;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

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
}
