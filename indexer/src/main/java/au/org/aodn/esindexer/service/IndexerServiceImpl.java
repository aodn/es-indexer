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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

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
    protected AodnDiscoveryParameterVocabService aodnDiscoveryParameterVocabService;

    private static final Logger logger = LogManager.getLogger(IndexerServiceImpl.class);

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
            AodnDiscoveryParameterVocabService aodnDiscoveryParameterVocabService
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
        this.aodnDiscoveryParameterVocabService = aodnDiscoveryParameterVocabService;
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

    protected boolean isGeoNetworkInstanceReinstalled(long portalIndexDocumentsCount) {
        /**
         * compare if GeoNetwork has 1 only metadata (the recently added one which triggered the indexer)
         * and the portal index has more than 0 documents (the most recent metadata yet indexed to portal index at this point)
         */
        return geoNetworkResourceService.isMetadataRecordsCountLessThan(2) && portalIndexDocumentsCount > 0;
    }

    protected boolean isMetadataPublished(String uuid) {
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
            String cleanedToken = token.token().replace("_", "").trim();
            if (cleanedToken.split("\\s+").length > 2) {
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

        List<String> aodnDiscoveryCategories = aodnDiscoveryParameterVocabService.getAodnDiscoveryCategories(stacCollectionModel.getThemes());
        if (!aodnDiscoveryCategories.isEmpty()) {
            stacCollectionModel.getSummaries().setDiscoveryCategories(aodnDiscoveryCategories);
        }

        // categories suggest using a different index
        // extendable for other aspects of the records data. eg. title, description, etc. something that are unique to the record and currently using "text" type
        RecordSuggest recordSuggest = RecordSuggest.builder()
                .abstractPhrases(this.extractTokensFromDescription(stacCollectionModel.getDescription()))
                .build();
        stacCollectionModel.setRecordSuggest(recordSuggest);

        return stacCollectionModel;
    }

    public ResponseEntity<String> indexMetadata(String metadataValues) {
        try {
            StacCollectionModel mappedMetadataValues = this.getMappedMetadataValues(metadataValues);
            IndexRequest<JsonData> req;

            String uuid = mappedMetadataValues.getUuid();
            long portalIndexDocumentsCount;

            // count portal index documents, or create index if not found from defined mapping JSON file
            try {
                portalIndexDocumentsCount = elasticSearchIndexService.getDocumentsCount(indexName);

                // check if GeoNetwork instance has been reinstalled
                if (this.isGeoNetworkInstanceReinstalled(portalIndexDocumentsCount)) {
                    logger.info("GeoNetwork instance has been reinstalled, recreating portal index: " + indexName);
                    elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);
                }
            } catch (IndexNotFoundException e) {
                logger.info("Index: {} not found, creating index", indexName);
                elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);
            }

            // index the metadata if it is published
            if (this.isMetadataPublished(uuid)) {
                try (InputStream is = new ByteArrayInputStream(indexerObjectMapper.writeValueAsBytes(mappedMetadataValues))) {
                    logger.info("Ingesting a new metadata with UUID: {} to index: {}", uuid, indexName);
                    logger.debug("{}", mappedMetadataValues);

                    // With the id in place, it will always update the same doc given the same id
                    req = IndexRequest.of(b -> b
                            .id(uuid)
                            .index(indexName)
                            .withJson(is));

                    IndexResponse response = portalElasticsearchClient.index(req);
                    logger.info("Metadata with UUID: {} indexed with version: {}", uuid, response.version());
                    return ResponseEntity.status(HttpStatus.OK).body(response.toString());
                } catch (ElasticsearchException e) {
                    String fullError = String.format("%s -> %s", e.getMessage(), e.error().causedBy());
                    logger.error(fullError);
                    throw new IndexingRecordException(fullError);
                }
            } else {
                logger.info("Metadata with UUID: {} is not published yet, skip indexing", uuid);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
        } catch (IOException | FactoryException | TransformException | JAXBException e) {
            logger.error(e.getMessage());
            throw new MappingValueException(e.getMessage());
        }
    }


    public ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException {
        logger.info("Deleting document with UUID: {} from index: {}", uuid, indexName);
        try {
            Hit<ObjectNode> doc = this.getDocumentByUUID(uuid);
            DeleteResponse response = portalElasticsearchClient.delete(b -> b
                    .index(indexName)
                    .id(doc.id())
            );

            logger.info("Document with UUID: {} deleted from index: {}", uuid, indexName);

            // Flush after insert, otherwise you need to wait for next auto-refresh. It is
            // especially a problem with autotest, where assert happens very fast.
            portalElasticsearchClient.indices().refresh();

            return ResponseEntity.status(HttpStatus.OK).body(response.toString());
        }
        catch (DocumentNotFoundException e) {
            logger.info("Document with UUID: {} not found in index: {}, skip deleting", uuid, indexName);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    public List<BulkResponse> indexAllMetadataRecordsFromGeoNetwork(boolean confirm, Callback callback) throws IOException {
        if (!confirm) {
            throw new IndexAllRequestNotConfirmedException("Please confirm that you want to index all metadata records from GeoNetwork");
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);

        logger.info("Indexing all metadata records from GeoNetwork");

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
                        BulkResponse temp = executeBulk(bulkRequest);
                        results.add(temp);

                        if(callback != null) {
                            callback.onProgress(String.format("Execute batch as bulk request is big enough %s", dataSize + size));
                            callback.onProgress(temp);
                        }

                        dataSize = 0;
                        bulkRequest = new BulkRequest.Builder();
                    }
                    // send bulk request to Elasticsearch
                    bulkRequest.operations(op -> op
                            .index(idx -> idx
                                    .id(mappedMetadataValues.getUuid())
                                    .index(indexName)
                                    .document(mappedMetadataValues)
                            )
                    );
                    dataSize += size;

                    if(callback != null) {
                        callback.onProgress(String.format("Current batch size %s byte", dataSize));
                    }

                } catch (FactoryException | JAXBException | TransformException e) {
                /* it will reach here if cannot extract values of all the keys in GeoNetwork metadata JSON
                or ID is not found, which is fatal.
                * */
                    logger.error("Error extracting values from GeoNetwork metadata JSON: {}", metadataRecord);
                }
            }
        }

        // In case there are residual
        BulkResponse temp = executeBulk(bulkRequest);
        results.add(temp);

        if(callback != null) {
            callback.onComplete(temp);
        }

        // TODO now processing for record_suggestions index
        logger.info("Finished execute bulk indexing records to index: {}",indexName);

        return results;
    }

    protected BulkResponse executeBulk(BulkRequest.Builder bulkRequest) throws IOException {
        BulkResponse result = portalElasticsearchClient.bulk(bulkRequest.build());

        // Flush after insert, otherwise you need to wait for next auto-refresh. It is
        // especially a problem with autotest, where assert happens very fast.
        portalElasticsearchClient.indices().refresh();

        // Log errors, if any
        if (!result.items().isEmpty()) {
            logger.error("Bulk load have errors? {}", result.errors());
            for (BulkResponseItem item: result.items()) {
                if (item.error() != null) {
                    try {
                        logger.error("UUID {} {} {} {}",
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
                    }
                    catch (FactoryException | TransformException | JAXBException e) {
                        logger.warn("Parse error on display stac record");
                    }
                }
            }
        }
        return result;
    }
}
