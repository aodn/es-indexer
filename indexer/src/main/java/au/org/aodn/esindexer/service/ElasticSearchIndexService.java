package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.CreateIndexException;
import au.org.aodn.esindexer.exception.DeleteIndexException;
import au.org.aodn.esindexer.exception.IndexNotFoundException;
import au.org.aodn.esindexer.exception.MultipleIndicesException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.inference.ElasticsearchServiceType;
import co.elastic.clients.elasticsearch.inference.GetInferenceRequest;
import co.elastic.clients.elasticsearch.inference.GetInferenceResponse;
import co.elastic.clients.elasticsearch.inference.TaskType;
import co.elastic.clients.elasticsearch.ml.GetTrainedModelsStatsResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    /**
     * This function is used to create the inference endpoint for the elasticsearch cluster, which is
     * use by the semantic search service to get the embeddings. Value are very specific to the model
     * you choose
     * @param inferenceId - The inference id use in schema file.
     * @throws Exception - If there is any error in creating the inference endpoint.
     */
    public void setupE5SmallInferenceEndpoint(String inferenceId) throws Exception {
        // You can define the service settings payload as a Map,
        // which the Java API Client will automatically serialize to JSON
        String modelId = ".multilingual-e5-small";
        Map<String, Object> serviceSettings = Map.of(
                "model_id", modelId,
                "num_allocations", 1,
                "num_threads", 1
        );
        // Call the inference().put() builder
        portalElasticsearchClient.inference().put(request -> request
                .inferenceId(inferenceId)
                .taskType(TaskType.TextEmbedding)
                .inferenceConfig(config -> config
                        .service(ElasticsearchServiceType.Elasticsearch.jsonValue())
                        .serviceSettings(JsonData.of(serviceSettings))
                )
                .timeout(Time.of(f -> f.time("5m")))
        );
        boolean isReady = waitModelReady(modelId);
        if (!isReady) {
            throw new Exception(String.format("Model %s is not ready", modelId));
        }
    }

    public boolean inferenceIdExists(String inferenceId) {
        try {
            // Attempt to get the specific inference endpoint
            portalElasticsearchClient.inference().get(g -> g.inferenceId(inferenceId));
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                return false;
            }
            throw e; // Rethrow if it's a different error (e.g., 500 or connection issue)
        } catch (IOException e) {
            throw new RuntimeException("Communication error with Elasticsearch", e);
        }
    }

    protected boolean waitModelReady(String modelId) {
        for (int i = 0; i < 30; i++) {
            try {
                GetTrainedModelsStatsResponse stats = portalElasticsearchClient
                        .ml()
                        .getTrainedModelsStats(s -> s
                                .modelId(modelId)
                        );

                // Check if the model is fully deployed
                boolean done = stats.trainedModelStats().stream()
                        .anyMatch(m -> m.deploymentStats() != null
                                && m.deploymentStats().allocationStatus() != null
                                && m.deploymentStats().allocationStatus().state() != null
                                && "fully_allocated".equals(m.deploymentStats().allocationStatus().state().jsonValue()));

                if (done) {
                    log.info("Model {} is ready!", modelId);
                    return true;
                }
            }
            catch (IOException e) {
                log.info("Model {} not yet created, waiting...", modelId);
            }

            try {
                TimeUnit.SECONDS.sleep(15);
            } catch (InterruptedException e) {
                // Ignore and keep waiting
            }
        }
        return false;
    }
    /**
     * Create an index from a JSON file.
     * @param indexMappingFile - The name of the JSON file containing the index schema.
     * @param indexName - The name of the index to be created.
     */
    public void recreateIndexFromMappingJSONFile(String indexMappingFile, String indexName) {
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
    protected String getAvailableIndexSuffix(String baseIndexName) {

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
                // if more than one index is pointed to by the alias, it's an error
                if (aliasedIndices.size() > 1) {
                    throw new MultipleIndicesException("Multiple indices found for alias: " + baseIndexName + ". Expected only one.");
                }

                if (aliasedIndices.contains(baseIndexName + indexSuffix1)) {
                    log.info("Index: {} is currently pointed to by alias: {}. Using the other index suffix: {}", baseIndexName + indexSuffix1, baseIndexName, indexSuffix2);
                    return indexSuffix2;
                } else {
                    log.info("Index: {} is currently pointed to by alias: {}. Using the other index suffix: {}", baseIndexName + indexSuffix2, baseIndexName, indexSuffix1);
                    return indexSuffix1;
                }
            } catch (ElasticsearchException | IOException e) {
                throw new IndexNotFoundException("Failed to get alias information for index: " + baseIndexName + " | " + e.getMessage());
            }
        }
    }
    /**
     * This function is used to get all indices (including aliases) in the portal Elasticsearch cluster.
     * @return - The list of all indices (including aliases) in the portal Elasticsearch cluster.
     */
    protected List<String> getAllIndexNames() {
        try {
            IndicesResponse response = portalElasticsearchClient.cat().indices(i -> i);
            return response.valueBody().stream().map(IndicesRecord::index).distinct().collect(Collectors.toList());
        } catch ( ElasticsearchException | IOException e) {
            //If no any indices, this function will return an empty list. This catch is for other Exceptions.
            throw new RuntimeException("Failed to get indices from Elasticsearch | " + e.getMessage());
        }
    }
    /**
     * Update the alias to point to the new index.
     * @param alias - The alias to update.
     * @param newIndexName - The new index name to point to.
     */
    protected void updateAliasToNewIndex(String alias, String newIndexName) {
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
            throw new RuntimeException("Failed to switch alias: " + alias + " to new index: " + newIndexName + " | " + e.getMessage());
        }
    }
    /**
     * Get the index name from the alias.
     * @param alias - The alias to get the index name from.
     * @return - The index name associated with the alias.
     */
    protected String getIndexNameFromAlias(String alias) {
        try {
            GetAliasResponse aliasResponse = portalElasticsearchClient.indices().getAlias(ga -> ga.name(alias));
            var aliasedIndices = aliasResponse.result().keySet();
            if (aliasedIndices.isEmpty()) {
                // It is possible that no index is found for the given alias because sometimes developers may modify indices manually in Kibana,
                // or the first time indexing from non-alias indices to alias-based indices.
                log.warn("No index found for alias: {}." , alias);
                return null;
            }
            // if more than one index is pointed to by the alias, it's an error
            if (aliasedIndices.size() > 1) {
                throw new MultipleIndicesException("Multiple indices found for alias: " + alias + ". Expected only one.");
            }

            return aliasedIndices.iterator().next();
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                // no index found for the given alias. It is ok for some scenarios so we can just log a warning and return null
                log.warn("No index found for alias: {}." , alias);
                return null;
            }
            throw new RuntimeException("Failed to get index name from alias: " + alias + " | " + e.getMessage());
        }catch ( IOException e) {
            throw new RuntimeException("Failed to get index name from alias: " + alias + " | " + e.getMessage());
        }
    }
    /**
     * Remove the alias from the index.
     * @param alias - The alias to remove.
     * @param indexName - The index name to remove the alias from.
     */
    protected void removeAliasFromIndex(String alias, String indexName) {
        try {
            log.info("Removing alias: {} from index: {}", alias, indexName);
            portalElasticsearchClient.indices().updateAliases(ua -> ua
                    .actions(a -> a
                            .remove(r -> r.alias(alias).index(indexName))
                    )
            );
            log.info("Alias: {} removed from index: {}", alias, indexName);
        } catch (ElasticsearchException | IOException e) {
            throw new RuntimeException("Failed to remove alias: " + alias + " from index: " + indexName + " | " + e.getMessage());
        }
    }
    /**
     * Get the number of documents in the index.
     * @param indexName - The name of the index.
     * @return - The number of documents in the index.
     */
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
