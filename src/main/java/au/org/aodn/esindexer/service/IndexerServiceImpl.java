package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.CreateIndexException;
import au.org.aodn.esindexer.exception.DeleteIndexException;
import au.org.aodn.esindexer.exception.DocumentNotFoundException;
import au.org.aodn.esindexer.exception.IndexAllRequestNotConfirmedException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

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

    private static final Logger logger = LoggerFactory.getLogger(IndexerServiceImpl.class);

    protected JSONObject mapMetadataValuesForPortalIndex(JSONObject metadataValues) {
        JSONObject mappedMetadataValues = new JSONObject();
        mappedMetadataValues.put("metadataIdentifier", metadataValues.get("metadataIdentifier"));
        return mappedMetadataValues;
    }

    protected long getDocumentsCount() {
        try {
            return portalElasticsearchClient.count(s -> s
                    .index(indexName)
            ).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException {
        try {
            SearchResponse<ObjectNode> response = portalElasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q
                    .match(t -> t
                        .field("metadataIdentifier")
                        .query(uuid)
                    )
                ), ObjectNode.class
            );
            TotalHits total = Objects.requireNonNull(response.hits().total());
            if (total.value() > 0) {
                boolean isExactResult = total.relation() == TotalHitsRelation.Eq && Objects.equals(uuid, Objects.requireNonNull(Objects.requireNonNull(response.hits().hits().get(0).source()).get("metadataIdentifier").asText()));
                if (!isExactResult) {
                    throw new DocumentNotFoundException("Document with UUID: " + uuid + " not found in index: " + indexName);
                } else {
                    return response.hits().hits().get(0);
                }
            } else {
                throw new DocumentNotFoundException("Document with UUID: " + uuid + " not found in index: " + indexName);
            }
        } catch (IOException e) {
            throw new IOException("Failed to get document with UUID: " + uuid + " | " + e.getMessage());
        }
    }

    protected boolean isGeoNetworkInstanceReinstalled() {
        /* compare if GeoNetwork has 1 only metadata (the recently added one which triggered the indexer)
            and the portal index has more than 0 documents (the most recent metadata yet indexed to portal index at this point)
            */
        int geoNetworkResourceServiceMetadataRecordsCount = geoNetworkResourceService.getMetadataRecordsCount();
        long portalIndexDocumentsCount = this.getDocumentsCount();
        logger.info("GeoNetwork metadata records count: " + geoNetworkResourceServiceMetadataRecordsCount);
        logger.info("Portal index documents count: " + portalIndexDocumentsCount);
        return geoNetworkResourceServiceMetadataRecordsCount == 1 && portalIndexDocumentsCount > 0;
    }

    protected boolean isMetadataPublished(JSONObject metadataValues) {
        return true;
    }

    protected void deleteIndexStore() {
        try {
            BooleanResponse response = portalElasticsearchClient.indices().exists(b -> b.index(indexName));
            if (response.value()) {
                logger.info("Deleting index: " + indexName);
                portalElasticsearchClient.indices().delete(b -> b.index(indexName));
                logger.info("Index: " + indexName + " deleted");
            } else {
                logger.info("Index: " + indexName + " not found");
            }
        } catch (IOException e) {
            throw new DeleteIndexException("Failed to delete index: " + indexName + " | " + e.getMessage());
        }
    }


    public void indexMetadata(JSONObject metadataValues) throws IOException {
        IndexRequest<JsonData> req;
        JSONObject mappedMetadataValues = mapMetadataValuesForPortalIndex(metadataValues);

        // check if GeoNetwork instance has been reinstalled
        if (this.isGeoNetworkInstanceReinstalled()) {
            logger.info("GeoNetwork instance has been reinstalled, recreating index: " + indexName);
            this.createIndexFromMappingJSONFile();
        }

        // delete the existing document if found first
        this.deleteDocumentByUUID((String) metadataValues.get("metadataIdentifier"));

        // index the metadata if it is published
        if (this.isMetadataPublished(metadataValues)) {
            logger.info("Ingesting a new metadata with UUID: " + metadataValues.get("metadataIdentifier") + " to index: " + indexName);
            req = IndexRequest.of(b -> b.index(indexName).withJson(new ByteArrayInputStream(mappedMetadataValues.toString().getBytes())));
            IndexResponse response = portalElasticsearchClient.index(req);
            logger.info("Metadata with UUID: " + metadataValues.get("metadataIdentifier") + " indexed with version: " + response.version());
        } else {
            logger.info("Metadata with UUID: " + metadataValues.get("metadataIdentifier") + " is not published yet, skip indexing");
        }
    }

    protected void createIndexFromMappingJSONFile() {
        ClassPathResource resource = new ClassPathResource("index_mapping_jsons/" + AppConstants.PORTAL_RECORDS_MAPPING_JSON_FILE);

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
        } catch (IOException e) {
            throw new CreateIndexException("Failed to create index: " + indexName + " | " + e.getMessage());
        }
    }

    public void deleteDocumentByUUID(String uuid) throws IOException {
        logger.info("Deleting document with UUID: " + uuid + " from index: " + indexName);
        try {
            Hit<ObjectNode> doc = this.getDocumentByUUID(uuid);
            portalElasticsearchClient.delete(b -> b
                    .index(indexName)
                    .id(doc.id())
            );
            logger.info("Document with UUID: " + uuid + " deleted from index: " + indexName);
        } catch (DocumentNotFoundException e) {
            logger.info("Document with UUID: " + uuid + " not found in index: " + indexName + ", skip deleting");
        }
    }

    public void indexAllMetadataRecordsFromGeoNetwork(Boolean confirm) {
        if (!confirm) {
            throw new IndexAllRequestNotConfirmedException("Please confirm that you want to index all metadata records from GeoNetwork");
        }

        this.createIndexFromMappingJSONFile();

        logger.info("Indexing all metadata records from GeoNetwork");
        // TODO : reindex all metadata records from GeoNetwork
        logger.info("All metadata records from GeoNetwork have been indexed to index: " + indexName);
    }
}
