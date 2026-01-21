package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.CreateIndexException;
import au.org.aodn.esindexer.exception.DeleteIndexException;
import au.org.aodn.esindexer.exception.IndexNotFoundException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;


@Slf4j
@Service
public class ElasticSearchIndexService {

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    protected void deleteIndexStore(String indexName) {
        try {
            BooleanResponse response = portalElasticsearchClient.indices().exists(b -> b.index(indexName));
            if (response.value()) {
                log.info("Deleting index: {}", indexName);
                portalElasticsearchClient.indices().delete(b -> b.index(indexName));
                log.info("Index: {} deleted", indexName);
            }
        } catch (ElasticsearchException | IOException e) {
            throw new DeleteIndexException("Failed to delete index: " + indexName + " | " + e.getMessage());
        }
    }

    public Long count(String indexName, String field, String value) throws IOException, ElasticsearchException {
        CountRequest request = CountRequest.of(r -> r
                .index(indexName)
                .query(q -> q.term(t -> t.field(field).value(value)))
        );

        CountResponse response = portalElasticsearchClient.count(request);
        return  (response != null) ? response.count() : null;
    }

    public String getFirstMatchId(String indexName, String field, String value) {
        var request = SearchRequest.of( search -> search
                .index(indexName)
                .query(q -> q.term(t -> t.field(field).value(value)))
                .size(1)
                .source(src -> src.fetch(false))
        );
        try{
            var response = portalElasticsearchClient.search(request, Void.class);
            return response.hits().hits().isEmpty()? null: response.hits().hits().get(0).id();
        } catch (ElasticsearchException | IOException e) {
            log.error("Failed to search index: {} for field: {} with value: {} | {}", indexName, field, value, e.getMessage());
            return null;
        }
    }

    public void createIndexFromMappingJSONFile(String indexMappingFile, String indexName) {
        // delete the existing index if found first
        this.deleteIndexStore(indexName);

        // AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE
        log.info("Reading index schema definition from JSON file: {}", indexMappingFile);

        // https://www.baeldung.com/java-classpath-resource-cannot-be-opened#resources
        try (InputStream inputStream = getClass().getResourceAsStream("/config_files/" + indexMappingFile)) {
            log.info("Creating index: {}", indexName);
            CreateIndexRequest req = CreateIndexRequest.of(b -> b
                    .index(indexName)
                    .withJson(inputStream)
            );
            CreateIndexResponse response = portalElasticsearchClient.indices().create(req);
            log.info(response.toString());
        }
        catch (ElasticsearchException | IOException e) {
            log.error("Failed to create index: {} | {}", indexName, e.getMessage());
            throw new CreateIndexException("Failed to elastic index from schema file: " + indexName + " | " + e.getMessage());
        }
    }
    /**
     * Generate a versioned index name by appending the current date and time to the base index name.
     * @param baseIndexName the base index name
     * @return the versioned index name in the format: baseIndexName__yyyyMMdd_HHmmssZ
     */
    protected String getVersionedIndexName(String baseIndexName) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssZ");
        String dateTime = ZonedDateTime.now().format(formatter);
        return baseIndexName + "__" + dateTime;
    }

    protected void switchAliasToNewIndex(String alias, String newIndexName) {
        try {
            log.info("Switching alias: {} to point to new index: {}", alias, newIndexName);
            portalElasticsearchClient.indices().updateAliases(ua -> ua
                    .actions(a -> a
                            .remove(r -> r.alias(alias).index("*"))
                    )
                    .actions(a -> a
                            .add(ad -> ad.alias(alias).index(newIndexName))
                    )
            );
            log.info("Alias: {} now points to index: {}", alias, newIndexName);
        } catch (ElasticsearchException | IOException e) {
            throw new IndexNotFoundException("Failed to switch alias: " + alias + " to new index: " + newIndexName + " | " + e.getMessage());
        }
    }


    public long getDocumentsCount(String indexName) {
        try {
            return portalElasticsearchClient.count(s -> s
                    .index(indexName)
            ).count();
        } catch (ElasticsearchException | IOException e) {
            throw new IndexNotFoundException("Failed to get documents count from index: " + indexName + " | " + e.getMessage());
        }
    }
}
