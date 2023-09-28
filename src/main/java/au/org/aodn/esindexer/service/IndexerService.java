package au.org.aodn.esindexer.service;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

public interface IndexerService {
    ResponseEntity<String> indexMetadata(JSONObject metadataValues) throws IOException;
    ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException;
    ResponseEntity<String> indexAllMetadataRecordsFromGeoNetwork(Boolean confirm) throws IOException;
    Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException;
}
