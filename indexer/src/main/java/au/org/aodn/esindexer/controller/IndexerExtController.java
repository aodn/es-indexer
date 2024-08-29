package au.org.aodn.esindexer.controller;

import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.service.VocabService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/indexer/ext/")
@Tag(name="Indexer Extras", description = "The Indexer API - Ext endpoints")
@Slf4j
public class IndexerExtController {
    VocabService vocabService;
    @Autowired
    public void setArdcVocabService(VocabService vocabService) {
        this.vocabService = vocabService;
    }

    @Value(AppConstants.ARDC_VOCAB_API_BASE)
    protected String vocabApiBase;

    // this endpoint for debugging/development purposes
    @GetMapping(path="/parameter/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get parameter vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getParameterVocab() throws IOException {
        return ResponseEntity.ok(vocabService.getParameterVocabsFromEs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/platform/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get platform vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getPlatformVocabs() throws IOException {
        return ResponseEntity.ok(vocabService.getPlatformVocabsFromEs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/organisation/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get organisation vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getOrganisationVocabs() throws IOException {
        return ResponseEntity.ok(vocabService.getOrganisationVocabsFromEs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/ardc/parameter/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get parameter vocabs from ARDC directly")
    public ResponseEntity<List<VocabModel>> getParameterVocabsFromArdc() {
        return ResponseEntity.ok(vocabService.getParameterVocabsFromArdc(vocabApiBase));
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/ardc/platform/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get platform vocabs from ARDC directly")
    public ResponseEntity<List<VocabModel>> getPlatformVocabsFromArdc() {
        return ResponseEntity.ok(vocabService.getPlatformVocabsFromArdc(vocabApiBase));
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/ardc/organisation/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get organisation vocabs from ARDC directly")
    public ResponseEntity<List<VocabModel>> getOrganisationVocabsFromArdc() {
        return ResponseEntity.ok(vocabService.getOrganisationVocabsFromArdc(vocabApiBase));
    }
}
