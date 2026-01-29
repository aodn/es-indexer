package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.CreateIndexException;
import au.org.aodn.esindexer.exception.DeleteIndexException;
import au.org.aodn.esindexer.exception.IndexNotFoundException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ElasticSearchIndexService {

    @Autowired
    ElasticsearchClient portalElasticsearchClient;


    // Naming below follows the blue-green deployment pattern which is the pattern we are using for index updates and are recommended naming convention.
    private static final String indexSuffix1 = "-blue";
    private static final String indexSuffix2 = "-green";

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
     *
     */
    protected String getWorkingIndexSuffix(String baseIndexName) {

        // get all indices (nothing to do with aliases)
        var indices = getAllIndexNames();
        if (!indices.contains(baseIndexName + indexSuffix1)) {
            return indexSuffix1;
        } else if (!indices.contains(baseIndexName + indexSuffix2)) {
            return indexSuffix2;
        } else {
            // both indices exist, find out which one is not currently pointed to by the alias
            log.warn("Both blue and green indices exist for base index name: {}. Determining the inactive index.", baseIndexName);
            try {
                GetAliasResponse aliasResponse = portalElasticsearchClient.indices().getAlias(ga -> ga.name(baseIndexName));
                var aliasedIndices = aliasResponse.result().keySet();

                if (aliasedIndices.contains(baseIndexName + indexSuffix1)) {
                    return indexSuffix1;
                } else {
                    return indexSuffix2;
                }
            } catch (ElasticsearchException | IOException e) {
                throw new IndexNotFoundException("Failed to get alias information for index: " + baseIndexName + " | " + e.getMessage());
            }
        }
    }

    private List<String> getAllIndexNames() {
        try {
            IndicesResponse response = portalElasticsearchClient.cat().indices(i -> i);
            return response.valueBody().stream().map(IndicesRecord::index).distinct().collect(Collectors.toList());
        } catch ( ElasticsearchException | IOException e) {
            throw new IndexNotFoundException("Failed to get indices from Elasticsearch | " + e.getMessage());
        }
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
