package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.service.IndexerMetadataService;
import au.org.aodn.esindexer.service.IndexerSettingService;
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
 * the list so it must see the new one. Both are safe to repeat, repeat the same call to fix a uuid
 * reported in reindex_failed.
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
     * @param uuids - The full list after the update
     * @param updatedAt - The time of the last write of the list, null if the list is never written
     * @param reindexed - The uuids of the request that are reindexed, not in the GET response
     * @param reindexFailed - The uuids of the request that failed to reindex, not in the GET response
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkipCoResponse(
            List<String> uuids,
            @JsonProperty("updated_at") String updatedAt,
            List<String> reindexed,
            @JsonProperty("reindex_failed") List<String> reindexFailed) {}

    @GetMapping(path = "/skip_co", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Get the uuids that are indexed with the data-index key and not with the cloud-optimised keys")
    public ResponseEntity<SkipCoResponse> getSkipCoUuids() throws IOException {
        IndexerSettingService.SkipCoSetting setting = indexerSettingService.getSkipCoSetting();
        return ResponseEntity.ok(new SkipCoResponse(setting.uuids(), setting.updatedAt(), null, null));
    }

    @PostMapping(path = "/skip_co/uuids", consumes = "application/json", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Add uuids to the skip_co list, then reindex them. Safe to repeat")
    public ResponseEntity<SkipCoResponse> addSkipCoUuids(@RequestBody UuidsRequest request) throws IOException {
        List<String> uuids = cleanUuids(request != null ? request.uuids() : null);
        log.info("Adding uuids to setting {}: {}", IndexerSettingService.SKIP_CO, uuids);

        // Update the list first, if it fails there is no reindex
        IndexerSettingService.SkipCoSetting setting = indexerSettingService.addSkipCoUuids(uuids);
        return ResponseEntity.ok(reindex(setting, uuids));
    }
    /**
     * It takes a query param and not a body, some clients and proxies drop the body of a DELETE.
     *
     * @param uuids - Comma separated uuids
     * @return - The full list after the update and the reindex result per uuid
     */
    @DeleteMapping(path = "/skip_co/uuids", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Remove uuids (comma separated) from the skip_co list, then reindex them. Safe to repeat")
    public ResponseEntity<SkipCoResponse> removeSkipCoUuids(@RequestParam("uuids") List<String> uuids) throws IOException {
        List<String> cleaned = cleanUuids(uuids);
        log.info("Removing uuids from setting {}: {}", IndexerSettingService.SKIP_CO, cleaned);

        // Update the list first, if it fails there is no reindex
        IndexerSettingService.SkipCoSetting setting = indexerSettingService.removeSkipCoUuids(cleaned);
        return ResponseEntity.ok(reindex(setting, cleaned));
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
     * Reindex the uuids one by one. A failed uuid does not stop the others, the list is updated
     * already so the uuid stays as is on the list and is reported in reindex_failed.
     */
    protected SkipCoResponse reindex(IndexerSettingService.SkipCoSetting setting, List<String> uuids) {
        List<String> reindexed = new ArrayList<>();
        List<String> reindexFailed = new ArrayList<>();

        for (String uuid : uuids) {
            if (reindex(uuid)) {
                reindexed.add(uuid);
            }
            else {
                reindexFailed.add(uuid);
            }
        }
        return new SkipCoResponse(setting.uuids(), setting.updatedAt(), reindexed, reindexFailed);
    }
    /**
     * Same steps as IndexerController.addDocumentByUUID(), it reads the new list during the mapping.
     *
     * @param uuid - The uuid of the record
     * @return - True if the record is indexed. False if it is not found in GeoNetwork, not published
     * or the indexing failed.
     */
    protected boolean reindex(String uuid) {
        try {
            String metadataValues = geonetworkResourceService.searchRecordBy(uuid);
            ResponseEntity<String> response = indexerMetadata.indexMetadata(metadataValues).join();

            if (response != null && response.getStatusCode() == HttpStatus.OK) {
                return true;
            }
            // 204 means the record is not published, so it is not indexed
            log.warn("Reindex of uuid {} not done, status {}", uuid, response != null ? response.getStatusCode() : null);
            return false;
        }
        catch (Exception e) {
            log.warn("Reindex of uuid {} failed: {}", uuid, e.getMessage());
            return false;
        }
    }
}
