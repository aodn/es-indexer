package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.ArdcVocabService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/indexer/ext/")
@Tag(name="Indexer Extras", description = "The Indexer API - Ext endpoints")
@Slf4j
public class IndexerExtController {
    ArdcVocabService ardcVocabService;
    @Autowired
    public void setArdcVocabService(ArdcVocabService ardcVocabService) {
        this.ardcVocabService = ardcVocabService;
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/parameter/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get parameter vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getParameterVocab() throws IOException {
        return ResponseEntity.ok(ardcVocabService.getParameterVocabs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/platform/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get platform vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getPlatformVocabs() throws IOException {
        return ResponseEntity.ok(ardcVocabService.getPlatformVocabs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/organisation/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get organisation vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getOrganisationVocabs() throws IOException {
        return ResponseEntity.ok(ardcVocabService.getOrganisationVocabs());
    }
}
