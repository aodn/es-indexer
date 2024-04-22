package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.model.CategoryVocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabsService;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.DocumentNotFoundException;
import au.org.aodn.esindexer.service.ElasticSearchIndexService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
// create and inject a stub proxy to self due to the circular reference http://bit.ly/4aFvYtt
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CacheArdcVocabsUtils {
    @Value(AppConstants.AODN_DISCOVERY_PARAMETER_VOCAB_API)
    protected String vocabApi;

    @Autowired
    ArdcVocabsService ardcVocabsService;

    // self-injection to avoid self-invocation problems when calling the cachable method within the same bean
    @Autowired
    CacheArdcVocabsUtils self;

    @Autowired
    ElasticSearchIndexService elasticSearchIndexService;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Value("${elasticsearch.index.categories.name}")
    String categoriesIndexName;

    @Autowired
    ObjectMapper indexerObjectMapper;


    protected void indexingDiscoveryCategoriesIndex(List<CategoryVocabModel> categoryVocabModels) throws IOException {
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.AODN_DISCOVERY_PARAMETER_VOCABULARIES_MAPPING_JSON_FILE, categoriesIndexName);
        log.info("Indexing all categoryVocabModel to {}", categoriesIndexName);
        for (CategoryVocabModel categoryVocabModel : categoryVocabModels) {
            try {
                // convert categoryVocabModel values to binary data
                log.debug("Ingested json is {}", indexerObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(categoryVocabModel));
                // send bulk request to Elasticsearch
                bulkRequest.operations(op -> op
                    .index(idx -> idx
                        .index(categoriesIndexName)
                        .document(categoryVocabModel)
                    )
                );
            } catch (JsonProcessingException e) {
                log.error("Failed to ingest categoryVocabModel to {}", categoriesIndexName);
                throw new RuntimeException(e);
            }
        }

        BulkResponse result = portalElasticsearchClient.bulk(bulkRequest.build());

        // Flush after insert, otherwise you need to wait for next auto-refresh. It is
        // especially a problem with autotest, where assert happens very fast.
        portalElasticsearchClient.indices().refresh();

        // Log errors, if any
        if (result.errors()) {
            log.error("Bulk had errors");
            for (BulkResponseItem item: result.items()) {
                if (item.error() != null) {
                    log.error("{} {}", item.error().reason(), item.error().causedBy());
                }
            }
        } else {
            log.info("Finished bulk indexing categoryVocabModel items to index: " + categoriesIndexName);
        }
        log.info("Total documents in index: " + categoriesIndexName + " is " + elasticSearchIndexService.getDocumentsCount(categoriesIndexName));
    }


    /*
    fetch vocabularies from ARDC and index to discovery categories index once the bean is created
     */
    @PostConstruct
    public void refreshDiscoveryCategoriesIndex() throws IOException {
        log.info("Fetching AODN Discovery Parameter Vocabularies from ARDC");
        List<CategoryVocabModel> categoryVocabModels = ardcVocabsService.getParameterCategory(vocabApi);
        indexingDiscoveryCategoriesIndex(categoryVocabModels);
    }

    @Cacheable(AppConstants.AODN_DISCOVERY_CATEGORIES_CACHE)
    // TODO research strategy to avoid multiple refresh runs at the same schedule by multiple indexer instances
    // A way to do it is read the value from Elastic search, if it has updated within say 24 hrs then use it
    public List<JsonNode> getDiscoveryCategories() throws IOException {
        List<JsonNode> categories = new ArrayList<>();
        log.info("Fetching AODN Discovery Parameter Vocabularies from {}", categoriesIndexName);
        try {
            double totalHits = elasticSearchIndexService.getDocumentsCount(categoriesIndexName);
            if (totalHits == 0) {
                throw new DocumentNotFoundException("No documents found in " + categoriesIndexName);
            } else {
                SearchResponse<JsonNode> response = portalElasticsearchClient.search(s -> s
                    .index(categoriesIndexName)
                    .size((int) totalHits), JsonNode.class
                );
                response.hits().hits().forEach(hit -> {
                    categories.add(hit.source());
                });
            }
        } catch (ElasticsearchException | IOException e) {
            throw new IOException("Failed to get documents from " + categoriesIndexName + " | " + e.getMessage());
        }
        return categories;
    }

    // refresh every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshCache() throws IOException {
        log.info("Refreshing AODN Discovery Parameter Vocabularies cache");
        clearCache();
        this.refreshDiscoveryCategoriesIndex();
        self.getDiscoveryCategories();
    }

    @CacheEvict(value = AppConstants.AODN_DISCOVERY_CATEGORIES_CACHE, allEntries = true)
    public void clearCache() {
        // Intentionally empty; the annotation does the job
    }
}
