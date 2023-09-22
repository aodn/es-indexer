package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.IndexerService;
import au.org.aodn.esindexer.service.GeoNetworkResourceService;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;

@RestController
@RequestMapping(value = "/api/v1/indexer/index")
@Tag(name="Indexer", description = "The Indexer API")
public class IndexerController {
    private static final Logger logger = LoggerFactory.getLogger(IndexerController.class);

    @Autowired
    IndexerService indexerService;

    @Autowired
    GeoNetworkResourceService geonetworkResourceService;

    @GetMapping(path="/gn_records/{uuid}", produces = "application/json")
    public ResponseEntity getMetadataRecordFromGeoNetworkByUUID(@PathVariable("uuid") String uuid) {
        logger.info("getting a document by UUID: " + uuid);
        JSONObject response =  geonetworkResourceService.searchMetadataRecordByUUID(uuid);
        return ResponseEntity.status(HttpStatus.OK).body(response.toString());
    }

    @GetMapping(path="/{uuid}", produces = "application/json")
    public ResponseEntity getDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        logger.info("getting a document by UUID: " + uuid);
        ObjectNode doc =  indexerService.getDocumentByUUID(uuid).source();
        return ResponseEntity.status(HttpStatus.OK).body(doc);
    }

    @PostMapping(path="/all", consumes = "application/json", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") })
    public ResponseEntity indexAllMetadataRecords(@RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) {
        indexerService.indexAllMetadataRecordsFromGeoNetwork(confirm);
        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }

    @PostMapping(path="/{uuid}", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") })
    public ResponseEntity addDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        JSONObject metadataValues = geonetworkResourceService.searchMetadataRecordByUUID(uuid);
        indexerService.indexMetadata(metadataValues);
        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }

    @DeleteMapping(path="/{uuid}", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") })
    public ResponseEntity deleteDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        indexerService.deleteDocumentByUUID(uuid);
        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }
}
