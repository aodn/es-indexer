package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.*;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.RecordSuggest;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class IndexerServiceImpl implements IndexerService {

    protected String indexName;
    protected String tokensAnalyserName;
    protected GeoNetworkService geoNetworkResourceService;
    protected ElasticsearchClient portalElasticsearchClient;
    protected ElasticSearchIndexService elasticSearchIndexService;
    protected ObjectMapper indexerObjectMapper;
    protected StacCollectionMapperService stacCollectionMapperService;
    protected JaxbUtils<MDMetadataType> jaxbUtils;
    protected RankingService rankingService;
    protected ArdcVocabsService ardcVocabsService;

    protected static final long DEFAULT_BACKOFF_TIME = 3000L;

    @Lazy
    @Autowired
    protected IndexerService self;

    @Autowired
    public IndexerServiceImpl(
            @Value("${elasticsearch.index.name}") String indexName,
            @Value("${elasticsearch.analyser.tokens.name}") String tokensAnalyserName,
            ObjectMapper indexerObjectMapper,
            JaxbUtils<MDMetadataType> jaxbUtils,
            RankingService rankingService,
            GeoNetworkService geoNetworkResourceService,
            ElasticsearchClient portalElasticsearchClient,
            ElasticSearchIndexService elasticSearchIndexService,
            StacCollectionMapperService stacCollectionMapperService,
            ArdcVocabsService ardcVocabsService
    ) {
        this.indexName = indexName;
        this.tokensAnalyserName = tokensAnalyserName;
        this.indexerObjectMapper = indexerObjectMapper;
        this.jaxbUtils = jaxbUtils;
        this.rankingService = rankingService;
        this.geoNetworkResourceService = geoNetworkResourceService;
        this.portalElasticsearchClient = portalElasticsearchClient;
        this.elasticSearchIndexService = elasticSearchIndexService;
        this.stacCollectionMapperService = stacCollectionMapperService;
        this.ardcVocabsService = ardcVocabsService;
    }

    public Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException {
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
        Integer completeness = rankingService.evaluateCompleteness(stacCollectionModel);
        // TODO: in future, evaluate other aspects of the data such as relevance, quality, etc using NLP

        /* expand score with other aspect of the data such as relevance, quality, etc.
        * can maintain 100 points as the maximum score by dividing the score by the number of aspects (round up/down to the nearest integer)
        * given max score is 100 for each aspect
        * e.g completeness = 80, relevance = 90, quality = 100
        * final score = (80 + 90 + 100) / 3 = 90
        */
        Integer score = completeness;

        stacCollectionModel.getSummaries().setScore(score);

        List<String> aodnDiscoveryParameters = ardcVocabsService.getDiscoveryParameters(stacCollectionModel.getThemes());
        if (!aodnDiscoveryParameters.isEmpty()) {
            stacCollectionModel.getSummaries().setParameterVocabs(aodnDiscoveryParameters);
        }

        // categories suggest using a different index
        // extendable for other aspects of the records data. eg. title, description, etc. something that are unique to the record and currently using "text" type
        RecordSuggest recordSuggest = RecordSuggest.builder()
                .abstractPhrases(this.extractTokensFromDescription(stacCollectionModel.getDescription()))
                .build();
        stacCollectionModel.setRecordSuggest(recordSuggest);

        return stacCollectionModel;
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

    public List<BulkResponse> indexAllMetadataRecordsFromGeoNetwork(boolean confirm, Callback callback) throws IOException {
        if (!confirm) {
            throw new IndexAllRequestNotConfirmedException("Please confirm that you want to index all metadata records from GeoNetwork");
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);

        log.info("Indexing all metadata records from GeoNetwork");

        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
        List<BulkResponse> results = new ArrayList<>();

        long dataSize = 0;
        for (String metadataRecord : geoNetworkResourceService.getAllMetadataRecords()) {
            if(metadataRecord != null) {
                try {
                    // get mapped metadata values from GeoNetwork to STAC collection schema
                    final StacCollectionModel mappedMetadataValues = this.getMappedMetadataValues(metadataRecord);
                    int size = indexerObjectMapper.writeValueAsBytes(mappedMetadataValues).length;

                    // We need to split the batch into smaller size to avoid data too large error in ElasticSearch,
                    // the limit is 10mb, so to make check before document add and push batch if size is too big
                    //
                    // dataSize = 0 is init case, just in case we have a very big doc that exceed the limit
                    // and we have not add it to the bulkRequest, hardcode to 5M which should be safe,
                    // usually it is 5M - 15M
                    //
                    if(dataSize + size > 5242880 && dataSize != 0) {
                        if(callback != null) {
                            callback.onProgress(String.format("Execute batch as bulk request is big enough %s", dataSize + size));
                        }

                        results.add(self.executeBulk(bulkRequest, callback));

                        dataSize = 0;
                        bulkRequest = new BulkRequest.Builder();
                    }
                    // Add item to  bulk request to Elasticsearch
                    bulkRequest.operations(op -> op
                            .index(idx -> idx
                                    .id(mappedMetadataValues.getUuid())
                                    .index(indexName)
                                    .document(mappedMetadataValues)
                            )
                    );
                    dataSize += size;

                    if(callback != null) {
                        callback.onProgress(
                                String.format(
                                        "Adding uuid %s to batch, current batch size is %s",
                                        mappedMetadataValues.getUuid(),
                                        dataSize)
                        );
                    }

                } catch (FactoryException | JAXBException | TransformException e) {
                    /*
                     * it will reach here if cannot extract values of all the keys in GeoNetwork metadata JSON
                     * or ID is not found, which is fatal.
                     */
                    log.error("Error extracting values from GeoNetwork metadata JSON: {}", metadataRecord);
                    if(callback != null) {
                        callback.onProgress(
                                String.format(
                                        "Skip %s due to transform error -> %s",
                                        metadataRecord,
                                        e.getMessage()
                                ));
                    }
                }
            }
        }

        // In case there are residual
        BulkResponse temp = self.executeBulk(bulkRequest, callback);
        results.add(temp);

        if(callback != null) {
            callback.onComplete(temp);
        }

        // TODO now processing for record_suggestions index
        log.info("Finished execute bulk indexing records to index: {}",indexName);

        return results;
    }
    /**
     * Keep retry until success, it is ok to insert docs to elastic again because we use _id as identifier.
     * In case any service is not available, we will keep retry many times, with 100 retry we try 25 mins which is
     * big enough for aws process restart.
     *
     * @param bulkRequest - The bulk request
     * @param callback - The event call back to avoid timeout
     * @return - The bulk insert result
     * @throws IOException - Exceptions on error
     * @throws HttpServerErrorException.ServiceUnavailable - Exceptions on geonetwork die or elastic not available
     */
    @Retryable(
            retryFor = {Exception.class, HttpServerErrorException.ServiceUnavailable.class},
            maxAttempts = 1000,
            backoff = @Backoff(delay = DEFAULT_BACKOFF_TIME)
    )
    @Override
    public BulkResponse executeBulk(BulkRequest.Builder bulkRequest, Callback callback) throws IOException, HttpServerErrorException.ServiceUnavailable {
        try {
            // Keep retry until success
            BulkResponse result = portalElasticsearchClient.bulk(bulkRequest.build());

            // Flush after insert, otherwise you need to wait for next auto-refresh. It is
            // especially a problem with autotest, where assert happens very fast.
            portalElasticsearchClient.indices().refresh();

            // Report status if success
            if(callback != null) {
                callback.onProgress(result);
            }

            // Log errors, if any
            if (!result.items().isEmpty()) {
                log.error("Bulk load have errors? {}", result.errors());
                for (BulkResponseItem item : result.items()) {
                    if (item.error() != null) {
                        try {
                            log.error("UUID {} {} {} {}",
                                    item.id(),
                                    item.error().reason(),
                                    item.error().causedBy(),
                                    indexerObjectMapper
                                            .writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(
                                                    this.getMappedMetadataValues(
                                                            geoNetworkResourceService.searchRecordBy(item.id())
                                                    )
                                            )
                            );
                        } catch (FactoryException | TransformException | JAXBException e) {
                            log.warn("Parse error on display stac record");
                        }
                    }
                }
            }
            return result;
        }
        catch(Exception e) {
            // Report status if not success, this help to keep connection
            if(callback != null) {
                callback.onProgress("Exception on bulk save, will retry : " + e.getMessage());
            }
            throw e;
        }
    }
}
