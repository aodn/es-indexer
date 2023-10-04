package au.org.aodn.esindexer.service;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

public interface IndexerService {
    ResponseEntity<String> indexMetadata(String metadataValues) throws IOException, FactoryException, TransformException, JAXBException;
    ResponseEntity<String> deleteDocumentByUUID(String uuid) throws IOException;
    ResponseEntity<String> indexAllMetadataRecordsFromGeoNetwork(boolean confirm) throws IOException;
    Hit<ObjectNode> getDocumentByUUID(String uuid) throws IOException;
}
