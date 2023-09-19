package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.ElasticsearchResourceService;
import au.org.aodn.esindexer.service.GeonetworkResourceService;
import au.org.aodn.esindexer.service.IndexerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(value = "/api/v1/indexer/index")
@Tag(name="Indexer", description = "The Indexer API")
public class IndexerController {
    private static final Logger logger = LoggerFactory.getLogger(IndexerController.class);

    @Autowired
    ElasticsearchResourceService elasticsearchResourceService;

    @Autowired
    GeonetworkResourceService geonetworkResourceService;

    @Autowired
    IndexerService indexerService;

    @GetMapping(path="/gn_records/{uuid}", produces = "application/json")
    public ResponseEntity getDocumentFromGNRecordsIndexByUUID(@PathVariable("uuid") Long uuid) {
        logger.info("getting a document by uuid from gn_records index: " + uuid);



        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }

    @GetMapping(path="/{uuid}", produces = "application/json")
    public ResponseEntity getDocumentFromPortalIndexByUUID(@PathVariable("uuid") Long uuid) {

        logger.info("getting a document by uuid from portal_records index: " + uuid);

        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }

    @PostMapping(path="/{uuid}", produces = "application/json")
    public ResponseEntity createDocumentInPortalIndexByUUID(@PathVariable("uuid") Long uuid) {

        logger.info("creating a document to the portal_records index by uuid: " + uuid);

        return ResponseEntity.status(HttpStatus.OK).body("Hello World");
    }
}
