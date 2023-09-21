package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.exception.IndexExistingException;
import au.org.aodn.esindexer.service.IndexerService;
import au.org.aodn.esindexer.service.GeoNetworkResourceService;
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
    public ResponseEntity getDocumentFromPortalIndexByUUID(@PathVariable("uuid") Long uuid) {

        logger.info("getting a document by UUID: " + uuid);

        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }

    @PostMapping(path="/{uuid}", produces = "application/json")
    public ResponseEntity addDocumentToIndexByUUID(@PathVariable("uuid") String uuid) {
        JSONObject metadataValues = geonetworkResourceService.searchMetadataRecordByUUID(uuid);
        /* we don't know if the destination index exists or not when this request is called
        given the Elasticsearch instance can be reset/reinstalled at any time
        do try to create a new destination index every time this request is called */
        try {
            /* successfully created a new destination index will trigger bulk indexing all metadata records from GeoNetwork
            which includes the one received in this request,
            so it's not necessary to call ingestNewDocument() from indexerService here */
            indexerService.createIndexFromMappingJSONFile();
        } catch (IndexExistingException e) {
            logger.info("** Skip creating new index ** - " + e.getMessage());
            // ingest the GeoNetwork metadata as a new document to the existing destination index
            indexerService.ingestNewDocument(metadataValues);
        }
        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }
}
