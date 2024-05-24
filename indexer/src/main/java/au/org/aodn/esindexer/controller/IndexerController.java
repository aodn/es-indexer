package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.IndexerService;
import au.org.aodn.esindexer.service.GeoNetworkService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping(value = "/api/v1/indexer/index")
@Tag(name="Indexer", description = "The Indexer API")
public class IndexerController {
    private static final Logger logger = LogManager.getLogger(IndexerController.class);

    @Autowired
    IndexerService indexerService;

    @Autowired
    GeoNetworkService geonetworkResourceService;

//    @GetMapping(path="/gn_records/{uuid}", produces = "application/json")
//    @Operation(description = "Get a document from GeoNetwork Elasticsearch by UUID")
//    public ResponseEntity getMetadataRecordFromGeoNetworkElasticsearchByUUID(@PathVariable("uuid") String uuid) {
//        logger.info("getting a document by UUID: " + uuid);
//        JSONObject response =  geonetworkResourceService.searchMetadataBy(uuid);
//        return ResponseEntity.status(HttpStatus.OK).body(response.toString());
//    }

    @GetMapping(path="/records/{uuid}", produces = "application/json")
    @Operation(description = "Get a document from GeoNetwork by UUID directly - JSON format response")
    public ResponseEntity<String> getMetadataRecordFromGeoNetworkByUUID(@PathVariable("uuid") String uuid) {
        logger.info("getting a document from geonetwork by UUID: {}", uuid);
        String response =  geonetworkResourceService.searchRecordBy(uuid);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping(path="/{uuid}", produces = "application/json")
    @Operation(description = "Get a document from portal index by UUID")
    public ResponseEntity<ObjectNode> getDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        logger.info("getting a document form portal by UUID: {}", uuid);
        ObjectNode response =  indexerService.getDocumentByUUID(uuid).source();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping(path="/all", consumes = "application/json", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Index all metadata records from GeoNetwork")
    public ResponseEntity<String> indexAllMetadataRecords(@RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) throws IOException {
        return indexerService.indexAllMetadataRecordsFromGeoNetwork(confirm, null);
    }

    @PostMapping(path="/async/all", consumes = "application/json", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Index all metadata records from GeoNetwork")
    public SseEmitter indexAllMetadataRecordsAsync(@RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) {
        final SseEmitter emitter = new SseEmitter(0L); // 0L means no timeout;

        IndexerService.Callback callback = new IndexerService.Callback() {
            @Override
            public void onProgress(Object update) {
                try {
                    emitter.send(update.toString());
                }
                catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(Object result) {
                try {
                    emitter.send(result.toString());
                    emitter.complete();
                }
                catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        };

        new Thread(() -> {
            try {
                indexerService.indexAllMetadataRecordsFromGeoNetwork(confirm, callback);
            }
            catch(IOException e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @PostMapping(path="/{uuid}", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Index a metadata record by UUID")
    public ResponseEntity<String> addDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException, FactoryException, JAXBException, TransformException {
        String metadataValues = geonetworkResourceService.searchRecordBy(uuid);
        return indexerService.indexMetadata(metadataValues);
    }

    @DeleteMapping(path="/{uuid}", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Delete a metadata record by UUID")
    public ResponseEntity<String> deleteDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        return indexerService.deleteDocumentByUUID(uuid);
    }
}
