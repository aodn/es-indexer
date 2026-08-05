package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.CreateIndexException;
import au.org.aodn.esindexer.exception.DeleteIndexException;
import au.org.aodn.esindexer.exception.IndexNotFoundException;
import au.org.aodn.esindexer.exception.MultipleIndicesException;
import au.org.aodn.stac.util.JsonUtil;
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
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ElasticSearchIndexService {

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Autowired
    ObjectMapper indexerObjectMapper;

    /*
        semantic_text fields need the licensed `inference` feature. Off by default so a basic-licence
        cluster (integration tests, local dev) still gets a usable index - see
        JsonUtil.stripSemanticTextFields. Turn it on for the edge/prod clusters, which are licensed.
    */
    @Value("${elasticsearch.semantic.enabled:false}")
    boolean semanticEnabled;

    // Naming below follows the blue-green deployment pattern which is the pattern we are using for index updates and are recommended naming convention.
    public static final String INDEX_SUFFIX_BLUE = "-blue";
    public static final String INDEX_SUFFIX_GREEN = "-green";

    // Alias marking an index that is built but not yet promoted to serve traffic.
    public final static String RUNNING_ALIAS_SUFFIX = "-running";

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

    public void recreateIndexFromMappingJSONFile(String indexMappingFile, String indexName, Map<String, String> indexSettings) {
        // delete the existing index if found first
        this.deleteIndexStore(indexName);

        // AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE
        log.info("Reading index schema definition from JSON file: {}", indexMappingFile);

        // https://www.baeldung.com/java-classpath-resource-cannot-be-opened#resources
        try (Reader reader = JsonUtil.createJsonStream(indexMappingFile, indexSettings, semanticEnabled)) {
            log.info("Creating index: {}", indexName);
            CreateIndexRequest req = CreateIndexRequest.of(b -> b
                    .index(indexName)
                    .withJson(reader)
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
        if (!indices.contains(baseIndexName + INDEX_SUFFIX_BLUE)) {
            return INDEX_SUFFIX_BLUE;
        } else if (!indices.contains(baseIndexName + INDEX_SUFFIX_GREEN)) {
            return INDEX_SUFFIX_GREEN;
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

                if (aliasedIndices.contains(baseIndexName + INDEX_SUFFIX_BLUE)) {
                    log.info("Index: {} is currently pointed to by alias: {}. Using the other index suffix: {}", baseIndexName + INDEX_SUFFIX_BLUE, baseIndexName, INDEX_SUFFIX_GREEN);
                    return INDEX_SUFFIX_GREEN;
                } else {
                    log.info("Index: {} is currently pointed to by alias: {}. Using the other index suffix: {}", baseIndexName + INDEX_SUFFIX_GREEN, baseIndexName, INDEX_SUFFIX_BLUE);
                    return INDEX_SUFFIX_BLUE;
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
            //If no any indices, this function will return an empty list. This catch is for other Exceptions.
            throw new RuntimeException("Failed to get indices from Elasticsearch | " + e.getMessage());
        }
    }

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


    public long getDocumentsCount(String indexName) {
        try {
            return portalElasticsearchClient.count(s -> s
                    .index(indexName)
            ).count();
        } catch (ElasticsearchException | IOException e) {
            throw new IndexNotFoundException("Failed to get documents count from index: " + indexName + " | " + e.getMessage());
        }
    }

    /**
     * Check of the schema file against the live mapping for a alias or an index. Treating the schema file as a ground truth, the difference can be a declared field, or a common field but with a different type (e.g. concept_semantic absent, or text instead of semantic_text).
     *
     * @param indexMappingFile schema resource used to create the index
     * @param aliasOrIndex alias or concrete index whose mapping is checked
     * @return true when the index is missing or does not contain the schema's declared field types
     */
    public boolean isMappingOutdated(String indexMappingFile, String aliasOrIndex) {
        try (Reader reader = JsonUtil.createJsonStream(indexMappingFile, null, semanticEnabled)) {
            JsonNode expected = indexerObjectMapper.readTree(reader);
            var result = portalElasticsearchClient.indices().getMapping(g -> g.index(aliasOrIndex)).result();
            if (result == null || result.isEmpty()) {
                return true;
            }

            // getMapping results are keyed by concrete index even when queried through an alias.
            JsonNode live = indexerObjectMapper.readTree(JsonpUtils.toJsonString(
                    result.values().iterator().next().mappings(), portalElasticsearchClient._jsonpMapper()));
            return mappingPropertiesOutdated(expected.path("mappings").path("properties"), live.path("properties"));
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                return true;
            }
            throw new RuntimeException("Failed to compare mapping for " + aliasOrIndex + " | " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compare mapping for " + aliasOrIndex + " | " + e.getMessage(), e);
        }
    }

    protected static boolean mappingPropertiesOutdated(JsonNode expectedProperties, JsonNode liveProperties) {
        // Extra live properties are intentionally ignored; only recurse into structures explicitly declared by the schema.
        if (!expectedProperties.isObject()) {
            return false;
        }
        if (!liveProperties.isObject()) {
            return true;
        }

        var fields = expectedProperties.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            JsonNode expectedField = field.getValue();
            JsonNode liveField = liveProperties.get(field.getKey());
            if (liveField == null || !liveField.isObject()) {
                return true;
            }

            JsonNode expectedType = expectedField.get("type");
            if (expectedType != null && !expectedType.asText().equals(liveField.path("type").asText())) {
                return true;
            }

            if (mappingPropertiesOutdated(expectedField.path("properties"), liveField.path("properties"))
                    || mappingPropertiesOutdated(expectedField.path("fields"), liveField.path("fields"))) {
                return true;
            }
        }
        return false;
    }
}
