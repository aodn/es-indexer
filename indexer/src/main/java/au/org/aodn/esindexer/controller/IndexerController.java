package au.org.aodn.esindexer.controller;


import au.org.aodn.esindexer.service.IndexService;
import au.org.aodn.esindexer.service.AcronymService;
import au.org.aodn.esindexer.service.IndexerMetadataService;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.*;

@RestController
@RequestMapping(value = "/api/v1/indexer/index")
@Tag(name = "Indexer", description = "The Indexer API")
@Slf4j
public class IndexerController {

    @Autowired
    IndexerMetadataService indexerMetadata;

    @Autowired
    GeoNetworkService geonetworkResourceService;

    @Autowired
    AcronymService acronymService;

    @GetMapping(path = "/records/{uuid}", produces = "application/json")
    @Operation(description = "Get a document from GeoNetwork by UUID directly - JSON format response")
    public ResponseEntity<String> getMetadataRecordFromGeoNetworkByUUID(@PathVariable("uuid") String uuid) {
        log.info("getting a document from geonetwork by UUID: {}", uuid);
        String response = geonetworkResourceService.searchRecordBy(uuid);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping(path = "/{uuid}", produces = "application/json")
    @Operation(description = "Get a document from portal index by UUID")
    public ResponseEntity<ObjectNode> getDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        log.info("getting a document form portal by UUID: {}", uuid);
        ObjectNode response = indexerMetadata.getDocumentByUUID(uuid).source();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    /**
     * Build the acronyms from the Organisation vocab (vocabs_index) and push them into the ES
     * synonyms set, live (no reindex). Overwrites the set.
     *
     * @return A confirmation message
     */
    @PostMapping(path = "/acronyms", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Build acronyms from the Organisation vocab and push them into the ES synonyms set (live, no reindex)")
    public ResponseEntity<AcronymService.AcronymSyncResult> syncAcronyms() throws IOException {
        log.info("pushing acronym synonyms set from the Organisation vocab");
        int pushed = acronymService.pushAcronymListToElasticsearch();
        String message = pushed > 0
                ? "Acronym synonyms set updated with " + pushed + " rules"
                : "No rules came from the vocab; synonyms set left unchanged";
        return ResponseEntity.ok(new AcronymService.AcronymSyncResult(
                acronymService.getSynonymSetName(), pushed, message));
    }
    /**
     * Read-only preview of the acronym rules from the Organisation vocab (vocabs_index). Nothing is
     * pushed; the synonyms set is left untouched. Use it to check the rules before POST /acronyms.
     *
     * @return The "short => long" rules
     */
    @GetMapping(path = "/acronyms/preview", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Preview acronym rules from the Organisation vocab (read-only, nothing is pushed)")
    public ResponseEntity<AcronymService.AcronymPreview> previewAcronyms() throws IOException {
        log.info("previewing acronym synonyms from the Organisation vocab");
        return ResponseEntity.ok(acronymService.previewAcronyms());
    }
    /**
     * Show the acronym rules currently live in the ES synonyms set (what search uses now);
     * /acronyms/preview shows what a push would write instead.
     *
     * @return The "short => long" rules live in the synonyms set
     */
    @GetMapping(path = "/acronyms/current", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Show the acronym rules currently live in the ES synonyms set (read-only)")
    public ResponseEntity<AcronymService.AcronymCurrent> currentAcronyms() throws IOException {
        log.info("reading the acronym synonyms set currently live in ES");
        return ResponseEntity.ok(acronymService.currentAcronyms());
    }
    /**
     * Emit result to FE so it will not result in gateway time-out. You need to run it with postman or whatever tools
     * support server side event, the content type needs to be text/event-stream in order to work
     * Noted: There is a bug in postman desktop, so either you run postman using web-browser with agent directly
     * or you need to have version 10.2 or above in order to get the emitted result
     *
     * @param confirm       - Must set to true to begin load
     * @param beginWithUuid - You want to start load with particular uuid, it is useful for resume previous incomplete reload
     * @return The SSeEmitter for status update, you can use it to tell which record is being ingested and ingest status.
     */
    @PostMapping(path = "/async/all")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Index all metadata records from GeoNetwork")
    public SseEmitter indexAllMetadataRecordsAsync(
            @RequestParam(value = "confirm", defaultValue = "false") Boolean confirm,
            @RequestParam(value = "beginWithUuid", required = false) String beginWithUuid) {

        final SseEmitter emitter = new SseEmitter(0L); // 0L means no timeout;
        final IndexService.Callback callback = createCallback(emitter);

        new Thread(() -> {
            try {
                indexerMetadata.indexAllMetadataRecordsFromGeoNetwork(beginWithUuid, confirm, callback);
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
    /**
     *
     * @param uuid - The UUID of the metadata
     * @return - No use
     * @throws IOException          - No use
     * @throws FactoryException     - No use
     * @throws JAXBException        - No use
     * @throws TransformException   - No use
     */
    @PostMapping(path = "/{uuid}", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Index a metadata record by UUID")
    public ResponseEntity<String> addDocumentByUUID(
            @PathVariable("uuid") String uuid) throws IOException, FactoryException, JAXBException, TransformException {

        String metadataValues = geonetworkResourceService.searchRecordBy(uuid);
        CompletableFuture<ResponseEntity<String>> f = indexerMetadata.indexMetadata(metadataValues);
        // Return when done make it back to sync instead of async
        return f.join();
    }

    @DeleteMapping(path = "/{uuid}", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Delete a metadata record by UUID")
    public ResponseEntity<String> deleteDocumentByUUID(@PathVariable("uuid") String uuid) throws IOException {
        return indexerMetadata.deleteDocumentByUUID(uuid);
    }

    protected IndexerMetadataService.Callback createCallback(SseEmitter emitter) {
        return new IndexService.Callback() {
            @Override
            public void onProgress(Object update) {
                try {
                    log.info("Send sse message to client - {}", update.toString());
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .data(update.toString())
                            .id(String.valueOf(update.hashCode()))
                            .name("Indexer update event");

                    emitter.send(event);
                } catch (IOException e) {
                    // In case of fail, try close the stream, if it cannot be closed. (likely stream terminated
                    // already, the load error out and we need to result from a particular uuid.
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(Object result) {
                try {
                    log.info("Flush and complete update to client");
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .data(result.toString())
                            .id(String.valueOf(result.hashCode()))
                            .name("Indexer update event");

                    emitter.send(event);
                    log.info("Close emitter in onComplete");
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(t.getMessage()));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }

            }
        };
    }
}
