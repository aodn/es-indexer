package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.ElasticsearchResourceService;
import au.org.aodn.esindexer.service.GeoNetworkResourceService;
import au.org.aodn.esindexer.service.IndexerService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api/v1/indexer/index")
@Tag(name="Indexer", description = "The Indexer API")
public class IndexerController {
    private static final Logger logger = LoggerFactory.getLogger(IndexerController.class);

    @Autowired
    ElasticsearchResourceService elasticsearchResourceService;

    @Autowired
    GeoNetworkResourceService geonetworkResourceService;

    @Autowired
    IndexerService indexerService;

    @GetMapping(path="/gn_records/{uuid}", produces = "application/json")
    public ResponseEntity getDocumentFromGNRecordsIndexByUUID(@PathVariable("uuid") String uuid) {
        logger.info("getting a document by uuid from gn_records index: " + uuid);
        JSONObject response =  geonetworkResourceService.searchMetadataRecordByUUID(uuid);
        return ResponseEntity.status(HttpStatus.OK).body(response.toString());
    }

    @GetMapping(path="/{uuid}", produces = "application/json")
    public ResponseEntity getDocumentFromPortalIndexByUUID(@PathVariable("uuid") Long uuid) {

        logger.info("getting a document by uuid from portal_records index: " + uuid);

        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }

    @PostMapping(path="/{uuid}", produces = "application/json")
    public ResponseEntity createDocumentInPortalIndexByUUID(@PathVariable("uuid") Long uuid) {

        logger.info("creating a document to the portal_records index by uuid: " + uuid);

        // TODO: create a document in portal_records index by uuid

        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }
}
