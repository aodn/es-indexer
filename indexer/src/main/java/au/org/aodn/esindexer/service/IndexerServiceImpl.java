package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.*;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.*;
import au.org.aodn.stac.model.RecordSuggest;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.*;

@Service
public class IndexerServiceImpl implements IndexerService {

    @Autowired
    GeoNetworkService geoNetworkResourceService;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Autowired
    ElasticSearchIndexService elasticSearchIndexService;

    @Value("${elasticsearch.index.name}")
    private String indexName;

    @Autowired
    ObjectMapper indexerObjectMapper;

    @Autowired
    protected StacCollectionMapperService stacCollectionMapperService;

    @Autowired
    JaxbUtils<MDMetadataType> jaxbUtils;

    @Autowired
    RankingService rankingService;

    @Autowired
    AodnDiscoveryParameterVocabService aodnDiscoveryParameterVocabService;

    private static final Logger logger = LogManager.getLogger(IndexerServiceImpl.class);

    public Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException {
        try {
            SearchResponse<ObjectNode> response = portalElasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q
                    .match(t -> t
                        .field("id")
                        .query(uuid)
                    )
                ), ObjectNode.class
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
                .title(stacCollectionModel.getTitle())
                .description(null) // purely for demonstrating the extendability, manage extra suggest field in RecordSuggest class, and the index JSON schema
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
                } else {
                    // delete the existing document if found first
                    this.deleteDocumentByUUID(uuid);
                }
            } catch (IndexNotFoundException e) {
                logger.info("Index: " + indexName + " not found, creating index");
                elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);
            }

            // index the metadata if it is published
            if (this.isMetadataPublished(uuid)) {
                try(InputStream is = new ByteArrayInputStream(indexerObjectMapper.writeValueAsBytes(mappedMetadataValues))) {
                    logger.info("Ingesting a new metadata with UUID: " + uuid + " to index: " + indexName);
                    logger.info("{}", mappedMetadataValues);
                    req = IndexRequest.of(b -> b
                            .index(indexName)
                            .withJson(is));

                    IndexResponse response = portalElasticsearchClient.index(req);
                    logger.info("Metadata with UUID: " + uuid + " indexed with version: " + response.version());
                    return ResponseEntity.status(HttpStatus.OK).body(response.toString());
                }
                catch (ElasticsearchException e) {
                    String fullError = String.format("%s -> %s", e.getMessage(), e.error().causedBy());
                    logger.error(fullError);
                    throw new IndexingRecordException(fullError);
                }
            } else {
                logger.info("Metadata with UUID: " + uuid + " is not published yet, skip indexing");
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
        } catch (IOException | FactoryException | TransformException | JAXBException e) {
            logger.error(e.getMessage());
            throw new MappingValueException(e.getMessage());
        }
    }


    public ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException {
        logger.info("Deleting document with UUID: " + uuid + " from index: " + indexName);
        try {
            Hit<ObjectNode> doc = this.getDocumentByUUID(uuid);
            DeleteResponse response = portalElasticsearchClient.delete(b -> b
                    .index(indexName)
                    .id(doc.id())
            );

            logger.info("Document with UUID: " + uuid + " deleted from index: " + indexName);

            // Flush after insert, otherwise you need to wait for next auto-refresh. It is
            // especially a problem with autotest, where assert happens very fast.
            portalElasticsearchClient.indices().refresh();

            return ResponseEntity.status(HttpStatus.OK).body(response.toString());
        }
        catch (DocumentNotFoundException e) {
            logger.info("Document with UUID: " + uuid + " not found in index: " + indexName + ", skip deleting");
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }

    public ResponseEntity<String> indexAllMetadataRecordsFromGeoNetwork(boolean confirm) throws IOException {
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
        if (!confirm) {
            throw new IndexAllRequestNotConfirmedException("Please confirm that you want to index all metadata records from GeoNetwork");
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE, indexName);

        logger.info("Indexing all metadata records from GeoNetwork");

        for (String metadataRecord : geoNetworkResourceService.getAllMetadataRecords()) {
            if(metadataRecord != null) {
                try {
                    // get mapped metadata values from GeoNetwork to STAC collection schema
                    StacCollectionModel mappedMetadataValues = this.getMappedMetadataValues(metadataRecord);

                    // convert mapped values to binary data
                    logger.debug("Ingested json is {}", indexerObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedMetadataValues));

                    // send bulk request to Elasticsearch
                    bulkRequest.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .document(mappedMetadataValues)
                            )
                    );

                } catch (FactoryException | JAXBException | TransformException e) {
                /* it will reach here if cannot extract values of all the keys in GeoNetwork metadata JSON
                or ID is not found, which is fatal.
                * */
                    logger.error("Error extracting values from GeoNetwork metadata JSON: " + metadataRecord);
                }
            }
        }

        BulkResponse result = portalElasticsearchClient.bulk(bulkRequest.build());

        // Flush after insert, otherwise you need to wait for next auto-refresh. It is
        // especially a problem with autotest, where assert happens very fast.
        portalElasticsearchClient.indices().refresh();

        // Log errors, if any
        if (result.errors()) {
            logger.error("Bulk had errors");
            for (BulkResponseItem item: result.items()) {
                if (item.error() != null) {
                    logger.error("{} {}", item.error().reason(), item.error().causedBy());
                }
            }
        } else {
            logger.info("Finished bulk indexing records to index: " + indexName);
        }

        // TODO now processing for record_suggestions index

        return ResponseEntity.status(HttpStatus.OK).body(result.toString());
    }
}
