package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.IndexerService;
import au.org.aodn.esindexer.service.GeoNetworkResourceService;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
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
    @Operation(description = "Get a document from GeoNetwork Elasticsearch by UUID")
    public ResponseEntity getMetadataRecordFromGeoNetworkElasticsearchByUUID(@PathVariable("uuid") String uuid) {
        logger.info("getting a document by UUID: " + uuid);
        JSONObject response =  geonetworkResourceService.searchMetadataRecordByUUIDFromGNRecordsIndex(uuid);
        return ResponseEntity.status(HttpStatus.OK).body(response.toString());
    }

    @GetMapping(path="/records/{uuid}", produces = "application/json")
    @Operation(description = "Get a document from GeoNetwork by UUID directly - JSON format response")
    public ResponseEntity getMetadataRecordFromGeoNetworkByUUID(@PathVariable("uuid") String uuid) {
        logger.info("getting a document by UUID: " + uuid);
        JSONObject response =  geonetworkResourceService.searchMetadataRecordByUUIDFromGN4(uuid);
        return ResponseEntity.status(HttpStatus.OK).body(response.toString());
    }

    @GetMapping(path="/{uuid}", produces = "application/json")
    @Operation(description = "Get a document from portal index by UUID")
    public ResponseEntity getDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        logger.info("getting a document by UUID: " + uuid);
        ObjectNode response =  indexerService.getDocumentByUUID(uuid).source();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping(path="/all", consumes = "application/json", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Index all metadata records from GeoNetwork")
    public ResponseEntity<String> indexAllMetadataRecords(@RequestParam(value = "confirm", defaultValue = "false") Boolean confirm) throws IOException {
        return indexerService.indexAllMetadataRecordsFromGeoNetwork(confirm);
    }

    @PostMapping(path="/{uuid}", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Index a metadata record by UUID")
    public ResponseEntity<String> addDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        JSONObject metadataValues = geonetworkResourceService.searchMetadataRecordByUUIDFromGN4(uuid);
        return indexerService.indexMetadata(metadataValues);
    }

    @DeleteMapping(path="/{uuid}", produces = "application/json")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Delete a metadata record by UUID")
    public ResponseEntity<String> deleteDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        return indexerService.deleteDocumentByUUID(uuid);
    }
}
