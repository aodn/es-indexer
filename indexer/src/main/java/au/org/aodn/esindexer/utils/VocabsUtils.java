package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.model.VocabDto;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.ardcvocabs.service.ParameterVocabProcessor;
import au.org.aodn.ardcvocabs.service.PlatformVocabProcessor;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.DocumentNotFoundException;
import au.org.aodn.esindexer.service.ElasticSearchIndexService;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
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
import java.io.IOException;


@Slf4j
// create and inject a stub proxy to self due to the circular reference http://bit.ly/4aFvYtt
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class VocabsUtils {
    @Value(AppConstants.ARDC_VOCAB_API_BASE)
    protected String vocabApiBase;

    @Autowired
    ParameterVocabProcessor parameterVocabProcessor;

    @Autowired
    PlatformVocabProcessor platformVocabsProcessor;

    // self-injection to avoid self-invocation problems when calling the cachable method within the same bean
    @Lazy
    @Autowired
    VocabsUtils self;

    @Autowired
    ElasticSearchIndexService elasticSearchIndexService;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Value("${elasticsearch.vocabs_index.name}")
    String vocabsIndexName;

    @Autowired
    ObjectMapper indexerObjectMapper;

    private void indexAllVocabs(List<VocabModel> parameterVocabs,
                                  List<VocabModel> platformVocabs) throws IOException {

        List<VocabDto> vocabDtos = new ArrayList<>();

        // parameter vocabs
        for (VocabModel parameterVocab : parameterVocabs) {
            VocabDto vocabDto = VocabDto.builder().parameterVocabModel(parameterVocab).build();
            vocabDtos.add(vocabDto);
        }

        // platform vocabs
        for (VocabModel platformVocab : platformVocabs) {
            VocabDto vocabDto = VocabDto.builder().platformVocabModel(platformVocab).build();
            vocabDtos.add(vocabDto);
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.VOCABS_INDEX_MAPPING_SCHEMA_FILE, vocabsIndexName);
        log.info("Indexing all vocabs to {}", vocabsIndexName);

        bulkIndexVocabs(vocabDtos);
    }

    private void bulkIndexVocabs(List<VocabDto> vocabs) throws IOException {
        // count portal index documents, or create index if not found from defined mapping JSON file
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

        for (VocabDto vocab : vocabs) {
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
    fetch vocabularies from ARDC and index to the vocabs index once the bean is created
     */
    @PostConstruct
    public void refreshVocabsIndex() throws IOException {
        log.info("Fetching parameterVocabs from ARDC");
        List<VocabModel> parameterVocabs = parameterVocabProcessor.getParameterVocabs(vocabApiBase);
        log.info("Fetching platformVocabs from ARDC");
        List<VocabModel> platformVocabs = platformVocabsProcessor.getPlatformVocabs(vocabApiBase);
        indexAllVocabs(parameterVocabs, platformVocabs);
    }

    @Cacheable(AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_CACHE)
    public List<JsonNode> getParameterVocabs() throws IOException {
        return this.groupVocabsByKey("parameter_vocab");
    }

    @Cacheable(AppConstants.AODN_PLATFORM_VOCABS_CACHE)
    public List<JsonNode> getPlatformVocabs() throws IOException {
        return this.groupVocabsByKey("platform_vocab");
    }

    protected List<JsonNode> groupVocabsByKey(String key) throws IOException {
        List<JsonNode> vocabs = new ArrayList<>();
        log.info("Fetching {} vocabularies from {}", key, vocabsIndexName);
        try {
            long totalHits = elasticSearchIndexService.getDocumentsCount(vocabsIndexName);
            if (totalHits == 0) {
                throw new DocumentNotFoundException("No documents found in " + vocabsIndexName);
            } else {
                SearchResponse<JsonNode> response = portalElasticsearchClient.search(s -> s
                    .index(vocabsIndexName)
                    .size((int) totalHits), JsonNode.class
                );
                response.hits().hits().stream()
                        .map(Hit::source)
                        .map(hitSource -> hitSource != null ? hitSource.get(key) : null)
                        .filter(Objects::nonNull)
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
        self.clearParameterVocabsCache();
        self.clearPlatformVocabsCache();
        self.refreshVocabsIndex();
        self.getParameterVocabs();
        self.getPlatformVocabs();
    }

    @CacheEvict(value = AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_CACHE, allEntries = true)
    public void clearParameterVocabsCache() {
        // Intentionally empty; the annotation does the job
    }

    @CacheEvict(value = AppConstants.AODN_PLATFORM_VOCABS_CACHE, allEntries = true)
    public void clearPlatformVocabsCache() {
        // Intentionally empty; the annotation does the job
    }

    public static boolean themesMatchConcept(List<ThemesModel> themes, ConceptModel thatConcept) {
        for (ThemesModel theme : themes) {
            for (ConceptModel thisConcept : theme.getConcepts()) {
                /*
                comparing by combined values (id and url) of the concept object
                this will prevent cases where bottom-level vocabs are the same in text, but their parent vocabs are different
                e.g "car -> parts" vs "bike -> parts" ("parts" is the same but different parent)
                 */
                if (thisConcept.equals(thatConcept)) {
                    /* thisConcept is the extracted from the themes of the record...theme.getConcepts()
                    thatConcept is the object created by iterating over the parameter_vocabs cache...ConceptModel thatConcept = ConceptModel.builder()
                    using overriding equals method to compare the two objects, this is not checking instanceof ConceptModel class
                     */
                    return true;
                }
            }
        }
        return false;
    }
}
