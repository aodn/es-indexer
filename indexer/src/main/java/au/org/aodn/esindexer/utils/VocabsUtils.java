package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.model.ArdcVocabModel;
import au.org.aodn.ardcvocabs.model.ParameterVocabModel;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.stream.StreamSupport;


@Slf4j
// create and inject a stub proxy to self due to the circular reference http://bit.ly/4aFvYtt
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class VocabsUtils {
    @Value(AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_API)
    protected String vocabApi;

    @Autowired
    ArdcVocabsService ardcVocabsService;

    // self-injection to avoid self-invocation problems when calling the cachable method within the same bean
    @Lazy
    @Autowired
    VocabsUtils self;

    @Autowired
    ElasticSearchIndexService elasticSearchIndexService;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Value("${elasticsearch.index.vocabs_index.name}")
    String vocabsIndexName;

    @Autowired
    ObjectMapper indexerObjectMapper;


    // indexArdcVocabs's input parameters are extendable (e.g platformVocabs, organisationVocabs etc.), currently just parameterVocabs.
    protected void indexAllVocabs(List<ParameterVocabModel> parameterVocabs) throws IOException {
        List<ArdcVocabModel> allVocabs = new ArrayList<>();

        for (ParameterVocabModel parameterVocab : parameterVocabs) {
            ParameterVocabModel lowerCaseParameterVocab = ParameterVocabModel.builder()
                    .label(parameterVocab.getLabel().toLowerCase())
                    .broader(parameterVocab.getBroader().stream().peek(item -> item.setLabel(item.getLabel().toLowerCase())).collect(Collectors.toList()))
                    .narrower(parameterVocab.getNarrower().stream().peek(item -> item.setLabel(item.getLabel().toLowerCase())).collect(Collectors.toList()))
                    .build();
            ArdcVocabModel aVocab = ArdcVocabModel.builder().parameterVocabModel(lowerCaseParameterVocab).build();
            allVocabs.add(aVocab);
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.VOCABS_INDEX_MAPPING_SCHEMA_FILE, vocabsIndexName);
        log.info("Indexing all vocabs to {}", vocabsIndexName);

        bulkIndexVocabs(allVocabs);
    }

    protected void bulkIndexVocabs(List<ArdcVocabModel> allVocabs) throws IOException {
        // count portal index documents, or create index if not found from defined mapping JSON file
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

        for (ArdcVocabModel vocab : allVocabs) {
            try {
                // convert vocab values to binary data
                log.debug("Ingested json is {}", indexerObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vocab));
                // send bulk request to Elasticsearch
                bulkRequest.operations(op -> op
                        .index(idx -> idx
                                .index(vocabsIndexName)
                                .document(vocab)
                        )
                );
            } catch (JsonProcessingException e) {
                log.error("Failed to ingest parameterVocabs to {}", vocabsIndexName);
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
            log.info("Finished bulk indexing items to index: {}", vocabsIndexName);
        }
        log.info("Total documents in index: {} is {}", vocabsIndexName, elasticSearchIndexService.getDocumentsCount(vocabsIndexName));
    }

    /*
    fetch vocabularies from ARDC and index to discovery parameter vocabs index once the bean is created
     */
    @PostConstruct
    public void refreshVocabsIndex() throws IOException {
        log.info("Fetching vocabularies from ARDC");
        List<ParameterVocabModel> parameterVocabs = ardcVocabsService.getParameterVocab(vocabApi);
        indexAllVocabs(parameterVocabs);
    }

    @Cacheable(AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_CACHE)
    public List<JsonNode> getParameterVocabs() throws IOException {
        return this.getVocabsByKey("parameter_vocabs");
    }

    protected List<JsonNode> getVocabsByKey(String key) throws IOException {
        List<JsonNode> vocabs = new ArrayList<>();
        log.info("Fetching {} vocabularies from {}", key, vocabsIndexName);
        try {
            double totalHits = elasticSearchIndexService.getDocumentsCount(vocabsIndexName);
            if (totalHits == 0) {
                throw new DocumentNotFoundException("No documents found in " + vocabsIndexName);
            } else {
                SearchResponse<JsonNode> response = portalElasticsearchClient.search(s -> s
                    .index(vocabsIndexName)
                    .size((int) totalHits), JsonNode.class
                );
                response.hits().hits().stream()
                        .map(hit -> Objects.requireNonNull(hit.source()).get(key))
                        .filter(Objects::nonNull)
                        .flatMap(vocabArray -> StreamSupport.stream(vocabArray.spliterator(), false)) // process all elements as if they were in a single list.
                        .forEach(vocabs::add);
            }
        } catch (ElasticsearchException | IOException e) {
            throw new IOException("Failed to get documents from " + vocabsIndexName + " | " + e.getMessage());
        }
        return vocabs;
    }

    // TODO: A smarter refresh and check if the values are diff before evict cache
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshCache() throws IOException {
        log.info("Refreshing ARDC vocabularies cache");
        self.clearCache();
        self.refreshVocabsIndex();
        self.getParameterVocabs();
    }

    @CacheEvict(value = AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_CACHE, allEntries = true)
    public void clearCache() {
        // Intentionally empty; the annotation does the job
    }
}
