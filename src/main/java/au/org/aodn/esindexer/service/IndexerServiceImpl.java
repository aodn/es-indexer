package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.*;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.BinaryData;
import co.elastic.clients.util.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import au.org.aodn.esindexer.model.StacCollectionModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Service
public class IndexerServiceImpl implements IndexerService {

    @Autowired
    GeoNetworkResourceService geoNetworkResourceService;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Value("${elasticsearch.index.name}")
    private String indexName;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    protected StacCollectionMapperService mapper;

    @Autowired
    JaxbUtils<MDMetadataType> jaxbUtils;

    @Autowired
    EvaluateCompletenessService evaluateCompletenessService;

    private static final Logger logger = LoggerFactory.getLogger(IndexerServiceImpl.class);

    protected long getDocumentsCount() {
        try {
            return portalElasticsearchClient.count(s -> s
                    .index(indexName)
            ).count();
        } catch (ElasticsearchException | IOException e) {
            throw new IndexNotFoundException("Failed to get documents count from index: " + indexName + " | " + e.getMessage());
        }
    }

    public Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException {
        try {
            /* Return JSONObject type won't work, hence return ObjectNode type which supported by Elasticsearch JAVA API,
            there's no need to convert returned ObjectNode to JSONObject as well */
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
        /* compare if GeoNetwork has 1 only metadata (the recently added one which triggered the indexer)
            and the portal index has more than 0 documents (the most recent metadata yet indexed to portal index at this point)
            */
        int geoNetworkResourceServiceMetadataRecordsCount = geoNetworkResourceService.getMetadataRecordsCount();
        return geoNetworkResourceServiceMetadataRecordsCount == 1 && portalIndexDocumentsCount > 0;
    }

    protected boolean isMetadataPublished(String uuid) {
        /* read for the published status from GN Elasticsearch index, the flag is not part of the XML body */
        JSONObject elasticSearchMetadataValues = geoNetworkResourceService.searchMetadataRecordByUUIDFromGNRecordsIndex(uuid);
        return elasticSearchMetadataValues.get("isPublishedToAll").equals("true");
    }

    protected void deleteIndexStore() {
        try {
            BooleanResponse response = portalElasticsearchClient.indices().exists(b -> b.index(indexName));
            if (response.value()) {
                logger.info("Deleting index: " + indexName);
                portalElasticsearchClient.indices().delete(b -> b.index(indexName));
                logger.info("Index: " + indexName + " deleted");
            }
        } catch (ElasticsearchException | IOException e) {
            throw new DeleteIndexException("Failed to delete index: " + indexName + " | " + e.getMessage());
        }
    }

    protected JSONObject getMappedMetadataValues(String metadataValues) throws IOException, FactoryException, TransformException, JAXBException {
        MDMetadataType metadataType = jaxbUtils.unmarshal(metadataValues);

        StacCollectionModel stacCollectionModel = mapper.mapToSTACCollection(metadataType);

        // evaluate completeness
        Integer completeness = evaluateCompletenessService.evaluate(stacCollectionModel);
        // TODO: in future, evaluate other aspects of the data such as relevance, quality, etc using NLP

        // expand score with other aspect of the data such as relevance, quality, etc.
        Integer score = completeness;

        stacCollectionModel.getSummaries().setScore(score);

        return new JSONObject(objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(stacCollectionModel));
    }

    public ResponseEntity<String> indexMetadata(String metadataValues) {
        try {
            JSONObject mappedMetadataValues = this.getMappedMetadataValues(metadataValues);
            IndexRequest<JsonData> req;

            String uuid = mappedMetadataValues.getString("id");
            long portalIndexDocumentsCount;

            // count portal index documents, or create index if not found from defined mapping JSON file
            try {
                portalIndexDocumentsCount = this.getDocumentsCount();

                // check if GeoNetwork instance has been reinstalled
                if (this.isGeoNetworkInstanceReinstalled(portalIndexDocumentsCount)) {
                    logger.info("GeoNetwork instance has been reinstalled, recreating portal index: " + indexName);
                    this.createIndexFromMappingJSONFile();
                } else {
                    // delete the existing document if found first
                    this.deleteDocumentByUUID(uuid);
                }
            } catch (IndexNotFoundException e) {
                logger.info("Index: " + indexName + " not found, creating index");
                this.createIndexFromMappingJSONFile();
            }

            // index the metadata if it is published
            if (this.isMetadataPublished(uuid)) {
                try {
                    logger.info("Ingesting a new metadata with UUID: " + uuid + " to index: " + indexName);
                    logger.info("{}", mappedMetadataValues);
                    req = IndexRequest.of(b -> b.index(indexName).withJson(new ByteArrayInputStream(mappedMetadataValues.toString().getBytes())));

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

    protected void createIndexFromMappingJSONFile() {
        ClassPathResource resource = new ClassPathResource("config_files/" + AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE);

        // delete the existing index if found first
        this.deleteIndexStore();

        try (InputStream input = resource.getInputStream()) {
            logger.info("Creating index: " + indexName);
            CreateIndexRequest req = CreateIndexRequest.of(b -> b
                .index(indexName)
                .withJson(input)
            );
            CreateIndexResponse response = portalElasticsearchClient.indices().create(req);
            logger.info(response.toString());
        }
        catch (ElasticsearchException | IOException e) {
            throw new CreateIndexException("Failed to elastic index from schema file: " + indexName + " | " + e.getMessage());
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
            return ResponseEntity.status(HttpStatus.OK).body(response.toString());
        } catch (DocumentNotFoundException e) {
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
        this.createIndexFromMappingJSONFile();

        logger.info("Indexing all metadata records from GeoNetwork");

        for (String metadataRecord : geoNetworkResourceService.getAllMetadataRecords()) {
            try {
                // get mapped metadata values from GeoNetwork to STAC collection schema
                JSONObject mappedMetadataValues = this.getMappedMetadataValues(metadataRecord);

                logger.debug("Final output json is {}", mappedMetadataValues);

                // convert mapped values to binary data
                ByteArrayInputStream input = new ByteArrayInputStream(mappedMetadataValues.toString().getBytes());
                BinaryData data = BinaryData.of(IOUtils.toByteArray(input), ContentType.APPLICATION_JSON);

                // send bulk request to Elasticsearch
                bulkRequest.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)
                        .document(data)
                    )
                );

                logger.info("Ingested a new metadata document with UUID: " + mappedMetadataValues.getString("id"));

            } catch (FactoryException | JAXBException | TransformException e) {
                /* it will reach here if cannot extract values of all the keys in GeoNetwork metadata JSON
                or ID is not found, which is fatal.
                * */
                logger.error("Error extracting values from GeoNetwork metadata JSON: " + metadataRecord);
            }
        }

        BulkResponse result = portalElasticsearchClient.bulk(bulkRequest.build());

        // Log errors, if any
        if (result.errors()) {
            logger.error("Bulk had errors");
            for (BulkResponseItem item: result.items()) {
                if (item.error() != null) {
                    logger.error(item.error().reason());
                }
            }
        } else {
            logger.info("Finished bulk indexing records to index: " + indexName);
        }

        return ResponseEntity.status(HttpStatus.OK).body(result.toString());
    }
}
