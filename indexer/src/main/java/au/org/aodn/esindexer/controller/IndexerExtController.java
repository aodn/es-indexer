package au.org.aodn.esindexer.controller;

import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.service.VocabService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/api/v1/indexer/ext/")
@Tag(name="Indexer Extras", description = "The Indexer API - Ext endpoints")
@Slf4j
public class IndexerExtController {

    protected VocabService vocabService;
    protected ArdcVocabService ardcVocabService;

    protected ObjectMapper indexerObjectMapper;
    @Autowired
    public void setIndexerObjectMapper(ObjectMapper indexerObjectMapper) {
        this.indexerObjectMapper = indexerObjectMapper;
    }

    @Autowired
    public IndexerExtController(ArdcVocabService ardcVocabService, VocabService vocabService) {
        this.vocabService = vocabService;
        this.ardcVocabService = ardcVocabService;
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/parameter/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get parameter vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getParameterVocab() throws IOException {
        return ResponseEntity.ok(vocabService.getParameterVocabs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/platform/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get platform vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getPlatformVocabs() throws IOException {
        return ResponseEntity.ok(vocabService.getPlatformVocabs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/organisation/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get organisation vocabs from Elastic search")
    public ResponseEntity<List<JsonNode>> getOrganisationVocabs() throws IOException {
        return ResponseEntity.ok(vocabService.getOrganisationVocabs());
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/ardc/parameter/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get parameter vocabs from ARDC directly")
    public ResponseEntity<List<JsonNode>> getParameterVocabsFromArdc() {
        List<VocabModel> vocabs = ardcVocabService.getVocabTreeFromArdcByType(ardcVocabService.getResolvedPathCollection().get(VocabApiPaths.PARAMETER_VOCAB.name()));
        return ResponseEntity.ok(indexerObjectMapper.valueToTree(vocabs));
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/ardc/platform/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get platform vocabs from ARDC directly")
    public ResponseEntity<List<JsonNode>> getPlatformVocabsFromArdc() {
        List<VocabModel> vocabs = ardcVocabService.getVocabTreeFromArdcByType(ardcVocabService.getResolvedPathCollection().get(VocabApiPaths.PLATFORM_VOCAB.name()));
        return ResponseEntity.ok(indexerObjectMapper.valueToTree(vocabs));
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/ardc/organisation/vocabs")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Get organisation vocabs from ARDC directly")
    public ResponseEntity<List<JsonNode>> getOrganisationVocabsFromArdc() {
        List<VocabModel> vocabs = ardcVocabService.getVocabTreeFromArdcByType(ardcVocabService.getResolvedPathCollection().get(VocabApiPaths.ORGANISATION_VOCAB.name()));
        return ResponseEntity.ok(indexerObjectMapper.valueToTree(vocabs));
    }

    // this endpoint for debugging/development purposes
    @GetMapping(path="/vocabs/populate")
    @Operation(security = { @SecurityRequirement(name = "X-API-Key") }, description = "Populate data to the vocabs index")
    public ResponseEntity<String> populateDataToVocabsIndex() throws IOException, ExecutionException, InterruptedException {
        // clear existing caches
        vocabService.clearParameterVocabCache();
        vocabService.clearPlatformVocabCache();
        vocabService.clearOrganisationVocabCache();
        // populate new data
        vocabService.populateVocabsData(ardcVocabService.getResolvedPathCollection());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Populated data to the vocabs index");
    }
}
