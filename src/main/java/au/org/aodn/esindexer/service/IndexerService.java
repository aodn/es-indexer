package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

public interface IndexerService {
    void indexMetadata(JSONObject metadataValues) throws IOException;
    void deleteDocumentByUUID(String uuid) throws IOException;
    void indexAllMetadataRecordsFromGeoNetwork(Boolean confirm);
    Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException;
}
