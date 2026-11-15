package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.IndexerMetadataService;
import au.org.aodn.esindexer.service.IndexerSettingService;
import au.org.aodn.esindexer.service.IndexerSettingService.SkipCoUpdate;
import au.org.aodn.metadata.geonetwork.exception.MetadataNotFoundException;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * Admin endpoints for the indexer settings. The only setting for now is the skip_co uuid list: a
 * record on the list is indexed with its data-index key and not with its cloud-optimised keys.
 *
 * Add and delete update the list first and then reindex every uuid of the request, the reindex reads
 * the list so it must see the new one. The list and the record index must not disagree, so after
 * the reindex:
 *   1. Reindex ok: the change of the list stays.
 *   2. Reindex failed, for any reason: the list goes back to what it was before the request for
 *      that uuid. The undo only writes the list, it does not reindex. The admin repeats the same
 *      call after the cause is fixed.
 *   3. One exception, delete only: reindex failed but the uuid is still removed if no record depends
 *      on it, that is the uuid is not in the record index or the record is not in GeoNetwork. An
 *      old record of GeoNetwork can stay in the record index, the weekly full reindex removes it.
 * The response has the full list and the result per uuid of the request. Both calls are safe to repeat.
 */
@RestController
@RequestMapping(value = "/api/v1/indexer/config")
@Tag(name = "Indexer Config", description = "The Indexer settings API")
@Slf4j
public class IndexerSettingController {

    @Autowired
    IndexerSettingService indexerSettingService;

    @Autowired
    IndexerMetadataService indexerMetadata;

    @Autowired
    GeoNetworkService geonetworkResourceService;

    /** Body of the add and the remove request, one uuid is a list of one. */
    public record UuidsRequest(List<String> uuids) {}

    /**
     * The result of one uuid of the request
     * @param uuid - The uuid
     * @param reindexed - True if the record is reindexed
     * @param onList - True if the uuid is on the list after the call
     * @param error - Why the reindex failed, not in the response if the record is reindexed
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UuidResult(
            String uuid,
            boolean reindexed,
            @JsonProperty("on_list") boolean onList,
            String error) {}

    /**
     * @param uuids - The full list after the call
     * @param updatedAt - The time of the last write of the list, null if the list is never written
     * @param results - The result per uuid of the request, not in the GET response
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkipCoResponse(
            List<String> uuids,
            @JsonProperty("updated_at") String updatedAt,
            List<UuidResult> results) {}

    /**
     * @param error - Why the reindex failed, null if the record is reindexed
     * @param notInGeoNetwork - True if it failed because GeoNetwork does not have the record
     */
    protected record ReindexOutcome(String error, boolean notInGeoNetwork) {
        boolean ok() {
            return error == null;
        }
    }

    @GetMapping(path = "/skip_co", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Get the uuids that are indexed with the data-index key and not with the cloud-optimised keys")
    public ResponseEntity<SkipCoResponse> getSkipCoUuids() throws IOException {
        IndexerSettingService.SkipCoSetting setting = indexerSettingService.getSkipCoSetting();
        return ResponseEntity.ok(new SkipCoResponse(setting.uuids(), setting.updatedAt(), null));
    }

    @PostMapping(path = "/skip_co/uuids", consumes = "application/json", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Add uuids to the skip_co list, then reindex them. A uuid that failed to reindex is not added, see the error in results. Safe to repeat")
    public ResponseEntity<SkipCoResponse> addSkipCoUuids(@RequestBody UuidsRequest request) throws IOException {
        List<String> uuids = cleanUuids(request != null ? request.uuids() : null);
        log.info("Adding uuids to setting {}: {}", IndexerSettingService.SKIP_CO, uuids);

        // Update the list first, if it fails there is no reindex
        SkipCoUpdate update = indexerSettingService.addSkipCoUuids(uuids);
        return ResponseEntity.ok(reindexThenUndoFailed(update, uuids, true));
    }
    /**
     * It takes the same JSON body as the add.
     *
     * @param request - The uuids to remove
     * @return - The full list after the call and the reindex result per uuid
     */
    @DeleteMapping(path = "/skip_co/uuids", consumes = "application/json", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Remove uuids from the skip_co list, then reindex them. A uuid that failed to reindex is not removed if its record is in the index, see the error in results. Safe to repeat")
    public ResponseEntity<SkipCoResponse> removeSkipCoUuids(@RequestBody UuidsRequest request) throws IOException {
        List<String> cleaned = cleanUuids(request != null ? request.uuids() : null);
        log.info("Removing uuids from setting {}: {}", IndexerSettingService.SKIP_CO, cleaned);

        // Update the list first, if it fails there is no reindex
        SkipCoUpdate update = indexerSettingService.removeSkipCoUuids(cleaned);
        return ResponseEntity.ok(reindexThenUndoFailed(update, cleaned, false));
    }
    /**
     * Trim, drop blanks and duplicates.
     *
     * @param uuids - The uuids of the request
     * @return - The cleaned uuids in the order of the request
     * @throws ResponseStatusException - 400 if nothing is left
     */
    protected static List<String> cleanUuids(List<String> uuids) {
        List<String> cleaned = uuids == null ? List.of() : uuids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(uuid -> !uuid.isEmpty())
                .distinct()
                .toList();

        if (cleaned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uuids must not be empty");
        }
        return cleaned;
    }
    /**
     * Reindex the uuids one by one, a failed uuid does not stop the others. Then undo the change of
     * the list for the failed uuids, see the rules on the class. The undo only writes the list, it
     * does not reindex, so it cannot loop.
     *
     * @param update - The result of the list update done before this call
     * @param uuids - The uuids of the request
     * @param add - True for the add request, false for the remove request
     * @return - The list after the undo and the result per uuid
     * @throws ResponseStatusException - 500 if the undo failed, the list still has the change
     */
    protected SkipCoResponse reindexThenUndoFailed(SkipCoUpdate update, List<String> uuids, boolean add) {
        Map<String, ReindexOutcome> outcomes = new LinkedHashMap<>();
        for (String uuid : uuids) {
            outcomes.put(uuid, reindex(uuid));
        }

        // Undo only what this request changed. A uuid that was on the list before the add (or not
        // on the list before the remove) matches its record already, it stays as it was.
        List<String> undo = uuids.stream()
                .filter(uuid -> !outcomes.get(uuid).ok())
                .filter(update.changed()::contains)
                .filter(uuid -> add || hasRecordThatNeedsTheList(uuid, outcomes.get(uuid)))
                .toList();

        SkipCoUpdate after = undo.isEmpty() ? update : undo(undo, add);

        List<UuidResult> results = uuids.stream()
                .map(uuid -> new UuidResult(
                        uuid,
                        outcomes.get(uuid).ok(),
                        after.uuids().contains(uuid),
                        outcomes.get(uuid).error()))
                .toList();

        return new SkipCoResponse(after.uuids(), after.updatedAt(), results);
    }
    /**
     * For a remove with a failed reindex: the uuid must stay on the list only if a record was
     * indexed with it and can still be reindexed.
     *
     * @param uuid - The uuid of the record
     * @param outcome - The failed reindex of the uuid
     * @return - False if the uuid can be removed from the list anyway
     */
    protected boolean hasRecordThatNeedsTheList(String uuid, ReindexOutcome outcome) {
        if (outcome.notInGeoNetwork()) {
            // The reindex can never work. If an old record is still in the record index, the weekly
            // full reindex removes it.
            return false;
        }
        try {
            return indexerMetadata.isDocumentIndexed(uuid);
        }
        catch (Exception e) {
            // Not sure, so keep the uuid on the list, the admin repeats the call
            log.warn("Failed to check uuid {} in the record index, keep it on the list: {}", uuid, e.getMessage());
            return true;
        }
    }
    /**
     * Put the list back for these uuids, with no reindex.
     *
     * @param uuids - The uuids to take off the list (add) or to put back on the list (remove)
     * @param add - True if the request was an add
     * @return - The list after the undo
     * @throws ResponseStatusException - 500 if the list cannot be written, it still has the change
     */
    protected SkipCoUpdate undo(List<String> uuids, boolean add) {
        try {
            log.warn("Undo the {} of {} in setting {}, reindex failed", add ? "add" : "remove", uuids, IndexerSettingService.SKIP_CO);
            return add
                    ? indexerSettingService.removeSkipCoUuids(uuids)
                    : indexerSettingService.addSkipCoUuids(uuids);
        }
        catch (IOException | RuntimeException e) {
            String message = String.format(
                    "Setting %s is not restored after a failed reindex, these uuids are still %s the list: %s",
                    IndexerSettingService.SKIP_CO, add ? "on" : "off", uuids);
            log.error("{} | {}", message, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, e);
        }
    }
    /**
     * Same steps as IndexerController.addDocumentByUUID(), it reads the new list during the mapping.
     *
     * @param uuid - The uuid of the record
     * @return - The outcome, with the error if the record is not in GeoNetwork, not published or
     * the indexing failed.
     */
    protected ReindexOutcome reindex(String uuid) {
        try {
            String metadataValues = geonetworkResourceService.searchRecordBy(uuid);
            ResponseEntity<String> response = indexerMetadata.indexMetadata(metadataValues).join();

            if (response != null && response.getStatusCode() == HttpStatus.OK) {
                return new ReindexOutcome(null, false);
            }
            // 204 means the record is not published, so it is not indexed
            log.warn("Reindex of uuid {} not done, status {}", uuid, response != null ? response.getStatusCode() : null);
            return new ReindexOutcome("Record is not published in GeoNetwork, it is not indexed", false);
        }
        catch (MetadataNotFoundException e) {
            log.warn("Reindex of uuid {} not done, record not in GeoNetwork", uuid);
            return new ReindexOutcome(errorOf(e), true);
        }
        catch (Exception e) {
            log.warn("Reindex of uuid {} failed: {}", uuid, e.getMessage());
            return new ReindexOutcome(errorOf(e), false);
        }
    }
    /**
     * @param e - The exception of the reindex
     * @return - The message for the admin, never null
     */
    protected static String errorOf(Exception e) {
        // The indexing runs async, its exception comes wrapped
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
