package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.*;
import au.org.aodn.esindexer.utils.GcmdKeywordUtils;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.SearchSuggestionsModel;
import au.org.aodn.stac.model.StacCollectionModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

@Slf4j
@Service
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class IndexerMetadataServiceImpl extends IndexServiceImpl implements IndexerMetadataService {

    protected String indexName;
    protected String tokensAnalyserName;
    protected GeoNetworkService geoNetworkResourceService;
    protected ElasticsearchClient portalElasticsearchClient;
    protected ElasticSearchIndexService elasticSearchIndexService;
    protected ObjectMapper indexerObjectMapper;
    protected StacCollectionMapperService stacCollectionMapperService;
    protected JaxbUtils<MDMetadataType> jaxbUtils;
    protected RankingService rankingService;
    protected VocabService vocabService;
    protected GcmdKeywordUtils gcmdKeywordUtils;

    @Lazy
    @Autowired
    protected IndexerMetadataService self;

    @Autowired
    public IndexerMetadataServiceImpl(
            @Value("${elasticsearch.index.name}") String indexName,
            @Value("${elasticsearch.analyser.tokens.name}") String tokensAnalyserName,
            ObjectMapper indexerObjectMapper,
            JaxbUtils<MDMetadataType> jaxbUtils,
            RankingService rankingService,
            GeoNetworkService geoNetworkResourceService,
            @Qualifier("portalElasticsearchClient") ElasticsearchClient elasticsearchClient,
            ElasticSearchIndexService elasticSearchIndexService,
            StacCollectionMapperService stacCollectionMapperService,
            VocabService vocabService,
            GcmdKeywordUtils gcmdKeywordUtils
    ) {
        super(elasticsearchClient, indexerObjectMapper);

        this.indexName = indexName;
        this.tokensAnalyserName = tokensAnalyserName;
        this.indexerObjectMapper = indexerObjectMapper;
        this.jaxbUtils = jaxbUtils;
        this.rankingService = rankingService;
        this.geoNetworkResourceService = geoNetworkResourceService;
        this.portalElasticsearchClient = elasticsearchClient;
        this.elasticSearchIndexService = elasticSearchIndexService;
        this.stacCollectionMapperService = stacCollectionMapperService;
        this.vocabService = vocabService;
        this.gcmdKeywordUtils = gcmdKeywordUtils;
    }

    public Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException {
        return getDocumentByUUID(uuid, indexName);
    }

    public Hit<ObjectNode> getDocumentByUUID(String uuid, String indexName) throws IOException {
        try {
            SearchResponse<ObjectNode> response = portalElasticsearchClient
                    .search(s -> s
                            .index(indexName)
                            .query(q -> q.ids(ids -> ids.values(uuid))),
                        ObjectNode.class
                    );
            TotalHits total = Objects.requireNonNull(response.hits().total());
            if (total.value() > 0) {
                boolean isExactResult = total.relation() == TotalHitsRelation.Eq && Objects.equals(uuid, Objects.requireNonNull(Objects.requireNonNull(response.hits().hits().get(0).source()).get("id").asText()));
                if (!isExactResult) {
                    throw new DocumentNotFoundException("Document with UUID: " + uuid + " not found in index: " + indexName);
                } else {
                    return response.hits().hits().get(0);
                }
            } else {
                throw new DocumentNotFoundException("Document with UUID: " + uuid + " not found in index: " + indexName);
            }
        } catch (ElasticsearchException | IOException e) {
            throw new IOException("Failed to get document with UUID: " + uuid + " | " + e.getMessage());
        }
    }

    @Override
    public boolean isGeoNetworkInstanceReinstalled(long portalIndexDocumentsCount) {
        /*
         * compare if GeoNetwork has only one metadata (the recently added one which triggered the indexer)
         * and the portal index has more than 0 documents (the most recent metadata yet indexed to portal index at this point)
         */
        return geoNetworkResourceService.isMetadataRecordsCountLessThan(2) && portalIndexDocumentsCount > 0;
    }

    @Override
    public boolean isMetadataPublished(String uuid) {
        /* read for the published status from GN Elasticsearch index, the flag is not part of the XML body */
        try {
            geoNetworkResourceService.searchRecordBy(uuid);
            return true;
        }
        catch(MetadataNotFoundException e) {
            return false;
        }
    }

    protected List<String> extractTokensFromDescription(String description) throws IOException {
        Set<String> results = new HashSet<>();

        AnalyzeRequest request = AnalyzeRequest.of(ar -> ar.index(indexName).analyzer(tokensAnalyserName).text(description));
        AnalyzeResponse response = portalElasticsearchClient.indices().analyze(request);

        for (AnalyzeToken token : response.tokens()) {
            // tweak as needed
            String cleanedToken = token.token().replace("_", "").replaceAll("\\s{2,}", " ").trim();
            if (!cleanedToken.isEmpty() && cleanedToken.split("\\s+").length > 0) { // change to 1 for at least 2 words, 2 for at least 3 words
                results.add(cleanedToken);
            }
        }

        return new ArrayList<>(results);
    }

    protected StacCollectionModel getMappedMetadataValues(String metadataValues) throws IOException, FactoryException, TransformException, JAXBException {
        MDMetadataType metadataType = jaxbUtils.unmarshal(metadataValues);

        StacCollectionModel stacCollectionModel = stacCollectionMapperService.mapToSTACCollection(metadataType);

        // evaluate completeness
        // TODO: in future, evaluate other aspects of the data such as relevance, quality, etc using NLP

        /* expand score with other aspect of the data such as relevance, quality, etc.
        * can maintain 100 points as the maximum score by dividing the score by the number of aspects (round up/down to the nearest integer)
        * given max score is 100 for each aspect
        * e.g completeness = 80, relevance = 90, quality = 100
        * final score = (80 + 90 + 100) / 3 = 90
        */
        Integer score = rankingService.evaluateCompleteness(stacCollectionModel);

        stacCollectionModel.getSummaries().setScore(score);

        // parameter vocabs
        Set<String> mappedParameterLabels = new HashSet<>();
        List<String> processedParameterVocabs = vocabService.extractVocabLabelsFromThemes(
                stacCollectionModel.getThemes(), AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS
        );

        if (!processedParameterVocabs.isEmpty()) {
            mappedParameterLabels.addAll(processedParameterVocabs);
        } else {
            // manual mapping with custom logic when the record doesn't have existing AODN Parameter Vocabs
            mappedParameterLabels.addAll(gcmdKeywordUtils.getMappedParameterVocabsFromGcmdKeywords(stacCollectionModel.getThemes()));
        }
        stacCollectionModel.getSummaries().setParameterVocabs(new ArrayList<>(mappedParameterLabels));

        /*
        NOTE: The following implementation for platform and organization vocabularies is just a placeholder, not the final version.
        It follows the same logic as what we intended for the parameter vocabulary, where we extract the list of second-level vocabularies that a record belongs to from its bottom-level vocabularies.
        // TODO: Adjust if necessary, or remove the above comments after making a final decision.
        --------------BEGIN--------------
        */
        // platform vocabs
        List<String> processedPlatformVocabs = vocabService.extractVocabLabelsFromThemes(stacCollectionModel.getThemes(), AppConstants.AODN_PLATFORM_VOCABS);
        if (!processedPlatformVocabs.isEmpty()) {
            stacCollectionModel.getSummaries().setPlatformVocabs(processedPlatformVocabs);
        }

        // organisation vocabs
        Set<String> mappedOrganisationLabels = new HashSet<>();
        List<String> organisationLabelsFromThemes = vocabService.extractOrganisationVocabLabelsFromThemes(stacCollectionModel.getThemes());
        if (!organisationLabelsFromThemes.isEmpty()) {
            mappedOrganisationLabels.addAll(organisationLabelsFromThemes);
        } else {
            // manual mapping with custom logics when the record doesn't have existing AODN Organisation Vocabs
            List<VocabModel> mappedOrganisationVocabsFromContacts = vocabService.getMappedOrganisationVocabsFromContacts(stacCollectionModel.getContacts());
            for (VocabModel vocabModel : mappedOrganisationVocabsFromContacts) {
                mappedOrganisationLabels.addAll(extractOrderedLabels(vocabModel));
            }
        }
        stacCollectionModel.getSummaries().setOrganisationVocabs(new ArrayList<>(mappedOrganisationLabels));

        // search_as_you_type enabled fields can be extended
        SearchSuggestionsModel searchSuggestionsModel = SearchSuggestionsModel.builder()
                .abstractPhrases(this.extractTokensFromDescription(stacCollectionModel.getDescription()))
                .parameterVocabs(!processedParameterVocabs.isEmpty() ? processedParameterVocabs : null)
                .platformVocabs(!processedPlatformVocabs.isEmpty() ? processedPlatformVocabs : null)
                .build();
        stacCollectionModel.setSearchSuggestionsModel(searchSuggestionsModel);

        return stacCollectionModel;
    }

    private List<String> extractOrderedLabels(VocabModel vocabModel) {
        // Priority: DisplayLabel > AltLabels > PrefLabel
        if (safeGet(vocabModel::getDisplayLabel).isPresent()) {
            return List.of(vocabModel.getDisplayLabel());
        } else if (safeGet(vocabModel::getAltLabels).isPresent()) {
            return vocabModel.getAltLabels();
        } else if (safeGet(vocabModel::getLabel).isPresent()) {
            return List.of(vocabModel.getLabel());
        }
        return List.of();
    }

    /**
     * Use to index a particular UUID, the async is used to limit the number of same function call to avoid flooding
     * the system.
     * @param metadataValues - The XML of the metadata
     * @return - The STAC doc in string format.
     */
    @Async("asyncIndexMetadata")
    public CompletableFuture<ResponseEntity<String>> indexMetadata(String metadataValues) {
        try {
            StacCollectionModel mappedMetadataValues = this.getMappedMetadataValues(metadataValues);
            String uuid = mappedMetadataValues.getUuid();

            // index the metadata if it is published
            if (this.isMetadataPublished(uuid)) {
                IndexRequest<JsonData> req;

                try (InputStream is = new ByteArrayInputStream(indexerObjectMapper.writeValueAsBytes(mappedMetadataValues))) {
                    log.info("Ingesting a new metadata with UUID: {} to index: {}", uuid, indexName);
                    log.debug("{}", mappedMetadataValues);

                    // With the id in place, it will always update the same doc given the same id
                    req = IndexRequest.of(b -> b
                            .id(uuid)
                            .index(indexName)
                            .withJson(is));

                    IndexResponse response = portalElasticsearchClient.index(req);
                    log.info("Metadata with UUID: {} indexed with version: {}", uuid, response.version());
                    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.OK).body(response.toString()));
                } catch (ElasticsearchException e) {
                    String fullError = String.format("%s -> %s", e.getMessage(), e.error().causedBy());
                    log.error(fullError);
                    throw new IndexingRecordException(fullError);
                }
            } else {
                log.info("Metadata with UUID: {} is not published yet, skip indexing", uuid);
                return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));
            }
        } catch (IOException | FactoryException | TransformException | JAXBException e) {
            log.error(e.getMessage());
            throw new MappingValueException(e.getMessage());
        }
    }

    public ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException {
        log.info("Deleting document with UUID: {} from index: {}", uuid, indexName);
        try {
            Hit<ObjectNode> doc = this.getDocumentByUUID(uuid);
            DeleteResponse response = portalElasticsearchClient.delete(b -> b
                    .index(indexName)
                    .id(doc.id())
            );

            log.info("Document with UUID: {} deleted from index: {}", uuid, indexName);

            // Flush after insert, otherwise you need to wait for next auto-refresh. It is
            // especially a problem with autotest, where assert happens very fast.
            portalElasticsearchClient.indices().refresh();

            return ResponseEntity.status(HttpStatus.OK).body(response.toString());
        }
        catch (DocumentNotFoundException e) {
            log.info("Document with UUID: {} not found in index: {}, skip deleting", uuid, indexName);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    public List<BulkResponse> indexAllMetadataRecordsFromGeoNetwork(
            String beginWithUuid, boolean confirm, final Callback callback) throws IOException {

        if (!confirm) {
            throw new IndexAllRequestNotConfirmedException("Please confirm that you want to index all metadata records from GeoNetwork");
        }

        if(beginWithUuid == null) {
            log.info("Indexing all metadata records from GeoNetwork");
            // recreate index from mapping JSON file
            elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);
        }
        else {
            log.info("Resume indexing records from GeoNetwork at {}", beginWithUuid);
        }

        Function<BulkResponseItem, Optional<StacCollectionModel>> mapper = (item) ->
        {
            try {
                return Optional.of(this.getMappedMetadataValues(
                        geoNetworkResourceService.searchRecordBy(item.id())
                ));
            } catch (IOException | FactoryException | TransformException | JAXBException e) {
                return Optional.empty();
            }
        };

        List<BulkResponse> results = new ArrayList<>();
        BulkRequestProcessor<StacCollectionModel> bulkRequestProcessor = new BulkRequestProcessor<>(indexName, mapper, self, callback);

        // We need to keep sending messages to client to avoid timeout on long processing
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (String metadataRecord : geoNetworkResourceService.getAllMetadataRecords(beginWithUuid)) {
                if (metadataRecord != null) {
                    // get mapped metadata values from GeoNetwork to STAC collection schema
                    final CountDownLatch countDown = new CountDownLatch(1);
                    Callable<StacCollectionModel> task = () ->  {
                        try {
                            return this.getMappedMetadataValues(metadataRecord);
                        }
                        catch (FactoryException | JAXBException | TransformException | NullPointerException e) {
                            /*
                             * it will reach here if cannot extract values of all the keys in GeoNetwork metadata JSON
                             * or ID is not found, which is fatal.
                             */
                            log.error("Error extracting values from GeoNetwork metadata JSON: {}", metadataRecord);
                            if (callback != null) {
                                callback.onProgress(
                                        String.format(
                                                "WARNING - Skip %s due to transform error -> %s",
                                                metadataRecord,
                                                e.getMessage()
                                        ));
                            }
                        }
                        finally {
                            countDown.countDown();
                        }
                        return null;
                    };

                    Callable<Void> msg = () -> {
                        // Make sure gateway not timeout on long processing
                        while(countDown.getCount() != 0) {
                            if (callback != null) {
                                callback.onProgress("Processing.... ");
                            }
                            countDown.await(30, TimeUnit.SECONDS);
                        }
                        return null;
                    };
                    // Submit a task to keep sending client message to avoid timeout
                    executor.submit(msg);
                    Future<StacCollectionModel> value = executor.submit(task);

                    final StacCollectionModel mappedMetadataValues = value.get();

                    if(mappedMetadataValues != null) {
                        bulkRequestProcessor
                                .processItem(mappedMetadataValues.getUuid(), mappedMetadataValues)
                                .ifPresent(results::add);
                    }
                }
            }

            // In case there are residual, just report error
            bulkRequestProcessor
                    .flush()
                    .ifPresent(response -> {
                        results.add(response);

                        if(callback != null) {
                            callback.onComplete(response);
                        }
                    });


            // TODO now processing for record_suggestions index
            log.info("Finished execute bulk indexing records to index: {}",indexName);
        }
        catch(Exception e) {
            log.error("Failed", e);

            if (callback != null) {
                callback.onComplete(
                        String.format(
                                "WARNING - Cannot process due to error -> %s, need to run 'Delete index and reindex in geonetwork?'",
                                e.getMessage()
                        ));
            }
        }
        finally {
            executor.shutdown();
        }
        return results;
    }
}
