package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IndexerService {
    // Event call back to notify caller, this avoid gateway timeout as we have message back to browser
    interface Callback {
        void onProgress(Object update);
        void onComplete(Object result);
    }
    CompletableFuture<ResponseEntity<String>> indexMetadata(String metadataValues) throws IOException, FactoryException, TransformException, JAXBException;
    ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException;
    List<BulkResponse> indexAllMetadataRecordsFromGeoNetwork(boolean confirm, Callback callback) throws IOException;
    Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException;
    boolean isMetadataPublished(String uuid);
    boolean isGeoNetworkInstanceReinstalled(long portalIndexDocumentsCount);
    BulkResponse executeBulk(BulkRequest.Builder bulkRequest, Callback callback) throws IOException;
}
