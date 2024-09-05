package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabDto;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.DocumentNotFoundException;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
// create and inject a stub proxy to self due to the circular reference http://bit.ly/4aFvYtt
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class VocabServiceImpl implements VocabService {

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabsIndexName;

    // self-injection to avoid self-invocation problems when calling the cachable method within the same bean
    @Lazy
    @Autowired
    VocabService self;

    protected ElasticsearchClient portalElasticsearchClient;
    protected ElasticSearchIndexService elasticSearchIndexService;
    protected ObjectMapper indexerObjectMapper;
    protected ArdcVocabService ardcVocabService;

    protected boolean themeMatchConcept(ThemesModel theme, ConceptModel thatConcept) {
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
        return false;
    }

    @Autowired
    public VocabServiceImpl(
            ArdcVocabService ardcVocabService,
            ObjectMapper indexerObjectMapper,
            ElasticsearchClient portalElasticsearchClient,
            ElasticSearchIndexService elasticSearchIndexService) {

        this.indexerObjectMapper = indexerObjectMapper;
        this.ardcVocabService = ardcVocabService;
        this.portalElasticsearchClient = portalElasticsearchClient;
        this.elasticSearchIndexService = elasticSearchIndexService;
    }
    /*
    this method for analysing the vocabularies of a record aka bottom-level vocabs (found in the themes section)
    and returning the second-level vocabularies that match (1 level up from the bottom-level vocabularies)
     */
    public List<String> extractVocabLabelsFromThemes(List<ThemesModel> themes, String vocabType) throws IOException {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        List<JsonNode> vocabs = switch (vocabType) {
            case AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS -> self.getParameterVocabs();
            case AppConstants.AODN_PLATFORM_VOCABS -> self.getPlatformVocabs();
            case AppConstants.AODN_ORGANISATION_VOCABS -> self.getOrganisationVocabs();
            default -> new ArrayList<>();
        };

        if (!vocabs.isEmpty() && !themes.isEmpty()) {
            vocabs.stream().filter(Objects::nonNull).forEach(topLevelVocab -> {
                if (topLevelVocab.has("narrower") && !topLevelVocab.get("narrower").isEmpty()) {
                    for (JsonNode secondLevelVocab : topLevelVocab.get("narrower")) {
                        if (secondLevelVocab != null && secondLevelVocab.has("label") && secondLevelVocab.has("about")) {
                            String secondLevelVocabLabel = secondLevelVocab.get("label").asText().toLowerCase();
                            themes.stream().filter(Objects::nonNull).forEach(theme -> {
                                ConceptModel secondLevelVocabAsConcept = ConceptModel.builder()
                                        .id(secondLevelVocab.get("label").asText())
                                        .url(secondLevelVocab.get("about").asText())
                                        .build();

                                // if the record's theme is already second-level vocab, no need to further check
                                if (themeMatchConcept(theme, secondLevelVocabAsConcept) && !results.contains(secondLevelVocabLabel)) {
                                    results.add(secondLevelVocabLabel);
                                }

                                // if the record's theme is leaf-node (bottom-level vocab)
                                if (secondLevelVocab.has("narrower") && !secondLevelVocab.get("narrower").isEmpty()) {
                                    for (JsonNode bottomLevelVocab : secondLevelVocab.get("narrower")) {
                                        if (bottomLevelVocab != null && bottomLevelVocab.has("label") && bottomLevelVocab.has("about")) {
                                            // map the original values to a ConceptModel object for doing comparison
                                            ConceptModel leafVocabAsConcept = ConceptModel.builder()
                                                    .id(bottomLevelVocab.get("label").asText())
                                                    .url(bottomLevelVocab.get("about").asText())
                                                    .build();

                                            // Compare with themes' concepts
                                            if (themeMatchConcept(theme, leafVocabAsConcept) && !results.contains(secondLevelVocabLabel)) {
                                                results.add(secondLevelVocabLabel);
                                                // just checking 1 leaf-node of each second-level vocab is enough, because we only care second-level vocabs.
                                                break;
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            });
        }
        return results;
    }

    protected List<JsonNode> groupVocabsFromEsByKey(String key) throws IOException {
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

    @Cacheable(value = AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS)
    public List<JsonNode> getParameterVocabs() throws IOException {
        return groupVocabsFromEsByKey("parameter_vocab");
    }

    @Cacheable(value = AppConstants.AODN_PLATFORM_VOCABS)
    public List<JsonNode> getPlatformVocabs() throws IOException {
        return groupVocabsFromEsByKey("platform_vocab");
    }

    @Cacheable(value = AppConstants.AODN_ORGANISATION_VOCABS)
    public List<JsonNode> getOrganisationVocabs() throws IOException {
        return groupVocabsFromEsByKey("organisation_vocab");
    }

    @CacheEvict(value = AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS, allEntries = true)
    public void clearParameterVocabCache() {
        // Intentionally empty; the annotation does the job
    }

    @CacheEvict(value = AppConstants.AODN_PLATFORM_VOCABS, allEntries = true)
    public void clearPlatformVocabCache() {
        // Intentionally empty; the annotation does the job
    }

    @CacheEvict(value = AppConstants.AODN_ORGANISATION_VOCABS, allEntries = true)
    public void clearOrganisationVocabCache() {
        // Intentionally empty; the annotation does the job
    }

    protected void indexAllVocabs(List<VocabModel> parameterVocabs,
                                List<VocabModel> platformVocabs,
                                List<VocabModel> organisationVocabs) throws IOException {

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

        // organisation vocabs
        for (VocabModel organisationVocab : organisationVocabs) {
            VocabDto vocabDto = VocabDto.builder().organisationVocabModel(organisationVocab).build();
            vocabDtos.add(vocabDto);
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.VOCABS_INDEX_MAPPING_SCHEMA_FILE, vocabsIndexName);
        log.info("Indexing all vocabs to {}", vocabsIndexName);

        bulkIndexVocabs(vocabDtos);
    }

    protected void bulkIndexVocabs(List<VocabDto> vocabs) throws IOException {
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

    public void populateVocabsData() throws IOException, InterruptedException, ExecutionException {

        Callable<List<VocabModel>> parameterVocabs = () -> {
            log.info("Fetching parameter vocabs from ARDC");
            return ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PARAMETER_VOCAB);
        };

        Callable<List<VocabModel>> platformVocabs = () -> {
            log.info("Fetching platform vocabs from ARDC");
            return ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PLATFORM_VOCAB);
        };

        Callable<List<VocabModel>> organisationVocabs = () -> {
            log.info("Fetching organisation vocabs from ARDC");
            return ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.ORGANISATION_VOCAB);
        };
        // Make it execute with threads the same time to speed up the load.
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        List<Callable<List<VocabModel>>> tasks = List.of(parameterVocabs, platformVocabs, organisationVocabs);
        List<Future<List<VocabModel>>> completed = executorService.invokeAll(tasks);

        log.info("Indexing fetched vocabs to {}", vocabsIndexName);
        indexAllVocabs(completed.get(0).get(), completed.get(1).get(), completed.get(2).get());

        executorService.shutdown();
    }
}
