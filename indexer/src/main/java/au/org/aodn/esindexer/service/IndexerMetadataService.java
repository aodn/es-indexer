package au.org.aodn.esindexer.service;

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

public interface IndexerMetadataService extends IndexService {

    CompletableFuture<ResponseEntity<String>> indexMetadata(String metadataValues) throws IOException, FactoryException, TransformException, JAXBException;
    ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException;
    List<BulkResponse> indexAllMetadataRecordsFromGeoNetwork(String beginWithUuid, boolean confirm, Callback callback) throws IOException;
    Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException;
    boolean isMetadataPublished(String uuid);
    boolean isGeoNetworkInstanceReinstalled(long portalIndexDocumentsCount);
}
