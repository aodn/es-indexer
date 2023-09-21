package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.DocumentExistingException;
import au.org.aodn.esindexer.exception.IndexExistingException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.JsonData;
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

@Service
public class IndexerServiceImpl implements IndexerService {

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    @Value("${elasticsearch.index.name}")
    private String indexName;

    private static final String PORTAL_RECORDS_MAPPING_JSON_FILE = "portal_records_mapping.json";

    private static final Logger logger = LoggerFactory.getLogger(IndexerServiceImpl.class);

    protected JSONObject mapMetadataValuesForPortalIndex(JSONObject metadataValues) {
        JSONObject mappedMetadataValues = new JSONObject();
        mappedMetadataValues.put("metadataIdentifier", metadataValues.get("metadataIdentifier"));
        return mappedMetadataValues;
    }

    public void ingestNewDocument(JSONObject metadataValues) {
        IndexRequest<JsonData> req;
        JSONObject mappedMetadataValues = mapMetadataValuesForPortalIndex(metadataValues);

        // change to if?
        try {
            // TODO: check duplicate docs with same UUID
            // TODO: check if metadata is published or not
            logger.info("Ingesting a new metadata with UUID: " + metadataValues.get("metadataIdentifier") + " to index: " + indexName);
            req = IndexRequest.of(b -> b.index(indexName).withJson(new ByteArrayInputStream(mappedMetadataValues.toString().getBytes())));
            IndexResponse response = portalElasticsearchClient.index(req);
            logger.info("Metadata with UUID: " + metadataValues.get("metadataIdentifier") + " indexed with version: " + response.version());
        } catch (IOException e) {
            throw new DocumentExistingException("Metadata with UUID: " + metadataValues.get("metadataIdentifier") + " already exists in index: " + indexName);
        }
    }

    public void createIndexFromMappingJSONFile() {
        ClassPathResource resource = new ClassPathResource("index_mapping_jsons/" + PORTAL_RECORDS_MAPPING_JSON_FILE);
        try (InputStream input = resource.getInputStream()) {
            logger.info("Creating index: " + indexName);
            CreateIndexRequest req = CreateIndexRequest.of(b -> b
                .index(indexName)
                .withJson(input)
            );
            CreateIndexResponse response = portalElasticsearchClient.indices().create(req);
            logger.info(response.toString());

            /* if the index is created successfully,
            it means nothing in there yet,
            so we need to try bulk indexing all metadata records from GeoNetwork (if any) */
            logger.info("Indexing all metadata records from GeoNetwork");
            this.indexAllMetadataRecordsFromGeoNetwork();
        } catch (ElasticsearchException e) {
            throw new IndexExistingException("Index with name: " + indexName + " already exists");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteDocumentByUUID(String uuid) {

    }

    public void updateDocumentByUUID(String uuid, JSONObject metadataValues) {

    }

    public void indexAllMetadataRecordsFromGeoNetwork() {
        // TODO: look for reindexing strategy instead? Note: need to create a new index first with selected mapping
        logger.info("All metadata records from GeoNetwork have been indexed to index: " + indexName);
    }
}
