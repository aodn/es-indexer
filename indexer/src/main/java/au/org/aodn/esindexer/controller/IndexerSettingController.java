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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Admin endpoints for the indexer settings. The only setting for now is the skip_co uuid list: a
 * record on the list is indexed with its data-index key and not with its cloud-optimised keys.
 *
 * Add and delete update the list first and then reindex the uuids of the request, the reindex reads
 * the list so it must see the new one. The list and the indexed record must not disagree, so after
 * the reindex:
 *   - reindex ok: the change of the list stays
 *   - reindex failed: the change of the list is undone, with no other reindex. The uuid is reported
 *     in reindex_failed, the admin repeats the same call after the cause is fixed.
 *   - record not in GeoNetwork: the uuid is not kept on the list (not added, or removed), there is
 *     no indexed record that can disagree with the list. The uuid is reported in not_found.
 * Both calls are safe to repeat.
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

    /** Body of the add request, one uuid is a list of one. */
    public record UuidsRequest(List<String> uuids) {}

    /**
     * reindexed, reindexFailed and notFound are the result per uuid of the request, they are not in
     * the GET response.
     *
     * @param uuids - The full list after the call
     * @param updatedAt - The time of the last write of the list, null if the list is never written
     * @param reindexed - Reindexed, the uuid is added or removed as asked
     * @param reindexFailed - Reindex failed, the uuid is not added or not removed
     * @param notFound - Record not in GeoNetwork, the uuid is not on the list
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkipCoResponse(
            List<String> uuids,
            @JsonProperty("updated_at") String updatedAt,
            List<String> reindexed,
            @JsonProperty("reindex_failed") List<String> reindexFailed,
            @JsonProperty("not_found") List<String> notFound) {}

    protected enum ReindexStatus { REINDEXED, NOT_FOUND, FAILED }

    @GetMapping(path = "/skip_co", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Get the uuids that are indexed with the data-index key and not with the cloud-optimised keys")
    public ResponseEntity<SkipCoResponse> getSkipCoUuids() throws IOException {
        IndexerSettingService.SkipCoSetting setting = indexerSettingService.getSkipCoSetting();
        return ResponseEntity.ok(new SkipCoResponse(setting.uuids(), setting.updatedAt(), null, null, null));
    }

    @PostMapping(path = "/skip_co/uuids", consumes = "application/json", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Add uuids to the skip_co list, then reindex them. A uuid that failed to reindex or is not in GeoNetwork is not added. Safe to repeat")
    public ResponseEntity<SkipCoResponse> addSkipCoUuids(@RequestBody UuidsRequest request) throws IOException {
        List<String> uuids = cleanUuids(request != null ? request.uuids() : null);
        log.info("Adding uuids to setting {}: {}", IndexerSettingService.SKIP_CO, uuids);

        // Update the list first, if it fails there is no reindex
        SkipCoUpdate update = indexerSettingService.addSkipCoUuids(uuids);
        return ResponseEntity.ok(reindexThenUndoFailed(update, uuids, true));
    }
    /**
     * It takes a query param and not a body, some clients and proxies drop the body of a DELETE.
     *
     * @param uuids - Comma separated uuids
     * @return - The full list after the call and the reindex result per uuid
     */
    @DeleteMapping(path = "/skip_co/uuids", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Remove uuids (comma separated) from the skip_co list, then reindex them. A uuid that failed to reindex is not removed. Safe to repeat")
    public ResponseEntity<SkipCoResponse> removeSkipCoUuids(@RequestParam("uuids") List<String> uuids) throws IOException {
        List<String> cleaned = cleanUuids(uuids);
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
     * the list for the uuids that are not reindexed, see the rules on the class. The undo only
     * writes the list, it does not reindex, so it cannot loop.
     *
     * @param update - The result of the list update done before this call
     * @param uuids - The uuids of the request
     * @param add - True for the add request, false for the remove request
     * @return - The list after the undo and the reindex result per uuid
     * @throws ResponseStatusException - 500 if the undo failed, the list still has the change
     */
    protected SkipCoResponse reindexThenUndoFailed(SkipCoUpdate update, List<String> uuids, boolean add) {
        List<String> reindexed = new ArrayList<>();
        List<String> reindexFailed = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        for (String uuid : uuids) {
            switch (reindex(uuid)) {
                case REINDEXED -> reindexed.add(uuid);
                case NOT_FOUND -> notFound.add(uuid);
                case FAILED -> reindexFailed.add(uuid);
            }
        }

        // Undo only what this request changed. A uuid that was on the list before the add (or not
        // on the list before the remove) matches its indexed record already, it stays as it was.
        List<String> undo = new ArrayList<>(reindexFailed.stream().filter(update.changed()::contains).toList());
        if (add) {
            // Never keep a uuid with no record on the list. For the remove it is gone already.
            undo.addAll(notFound);
        }

        if (undo.isEmpty()) {
            return new SkipCoResponse(update.uuids(), update.updatedAt(), reindexed, reindexFailed, notFound);
        }

        try {
            log.warn("Undo the {} of {} in setting {}, not reindexed", add ? "add" : "remove", undo, IndexerSettingService.SKIP_CO);
            SkipCoUpdate restored = add
                    ? indexerSettingService.removeSkipCoUuids(undo)
                    : indexerSettingService.addSkipCoUuids(undo);
            return new SkipCoResponse(restored.uuids(), restored.updatedAt(), reindexed, reindexFailed, notFound);
        }
        catch (IOException | RuntimeException e) {
            String message = String.format(
                    "Setting %s is not restored after a failed reindex, these uuids are still %s the list: %s",
                    IndexerSettingService.SKIP_CO, add ? "on" : "off", undo);
            log.error("{} | {}", message, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, e);
        }
    }
    /**
     * Same steps as IndexerController.addDocumentByUUID(), it reads the new list during the mapping.
     *
     * @param uuid - The uuid of the record
     * @return - REINDEXED if the record is indexed, NOT_FOUND if it is not in GeoNetwork, FAILED if
     * it is not published or the indexing failed.
     */
    protected ReindexStatus reindex(String uuid) {
        try {
            String metadataValues = geonetworkResourceService.searchRecordBy(uuid);
            ResponseEntity<String> response = indexerMetadata.indexMetadata(metadataValues).join();

            if (response != null && response.getStatusCode() == HttpStatus.OK) {
                return ReindexStatus.REINDEXED;
            }
            // 204 means the record is not published, so it is not indexed
            log.warn("Reindex of uuid {} not done, status {}", uuid, response != null ? response.getStatusCode() : null);
            return ReindexStatus.FAILED;
        }
        catch (MetadataNotFoundException e) {
            log.warn("Reindex of uuid {} not done, record not in GeoNetwork", uuid);
            return ReindexStatus.NOT_FOUND;
        }
        catch (Exception e) {
            log.warn("Reindex of uuid {} failed: {}", uuid, e.getMessage());
            return ReindexStatus.FAILED;
        }
    }
}
