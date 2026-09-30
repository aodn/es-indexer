package au.org.aodn.esindexer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.KeyValuePair;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

@RestController
@RequestMapping(value = "/api/v1/indexer/batch")
@Tag(name = "Batch", description = "The Batch API")
@Slf4j
public class BatchController {

    @Autowired
    BatchClient batchClient;

    /**
     * index all metadata records in aws batch, it is to prevent aws to gracefully shutdown ecs instance and cause some unexpected issues.
     *
     * @param confirm       - Must set to true to begin a load
     * @param beginWithUuid - You want to start load from a particular uuid, it is useful for resume previous incomplete
     * @return - The job result
     */
    @PostMapping(path = "/metadata", consumes = "application/json", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Index all metadata records from GeoNetwork in aws batch")
    public ResponseEntity<String> indexAllMetadataRecordsInBatch(
            @RequestParam(value = "confirm", defaultValue = "false") Boolean confirm,
            @RequestParam(value = "beginWithUuid", required = false) String beginWithUuid) {

        var rejected = rejectUnlessConfirmed(confirm, "index all metadata records in batch");
        if (rejected != null) {
            return rejected;
        }

        // Build the APP_ARGS value based on parameters
        String appArgs = beginWithUuid != null
                ? "--batch --jobName=indexAllMetadataFromUuid --jobParam=" + beginWithUuid
                : "--batch --jobName=indexAllMetadata";

        var envVariables = List.of(
                KeyValuePair.builder()
                        .name("APP_ARGS")
                        .value(appArgs)
                        .build()
        );

        return submitJob(
                "index-all-metadata-records",
                "indexing-queue",
                // this is the same as in AWS batch job definition to be used
                "es-indexer-metadata-indexing-job-definition",
                builder -> builder.containerOverrides(override -> override.environment(envVariables)),
                "APP_ARGS: " + appArgs
        );
    }
    /**
     * Trigger the pmtiles generation in aws batch. The job is run by the data-access-service image, its entry_point.py
     * reads the job "parameters" (not env variables like the metadata indexing job) and dispatch on the "type" value.
     *
     * @param confirm - Must set to true to really submit the job
     * @param uuid    - Optional, generate pmtiles for this dataset only, otherwise all parquet datasets are processed
     * @return - The job result
     */
    @PostMapping(path = "/pmtiles", consumes = "application/json", produces = "application/json")
    @Operation(security = {@SecurityRequirement(name = "X-API-Key")}, description = "Generate pmtiles for the parquet datasets in aws batch")
    public ResponseEntity<String> generatePmTilesInBatch(
            @RequestParam(value = "confirm", defaultValue = "false") Boolean confirm,
            @RequestParam(value = "uuid", required = false) String uuid) {

        var rejected = rejectUnlessConfirmed(confirm, "generate the pmtiles in batch");
        if (rejected != null) {
            return rejected;
        }

        // The data-access-service entry_point.py switches on "type", and use "uuid" to limit the run to one dataset
        var parameters = new HashMap<String, String>();
        parameters.put("type", "generate-pmtiles-for-parquet");

        if (uuid != null && !uuid.isBlank()) {
            parameters.put("uuid", uuid.trim());
        }

        return submitJob(
                "generate-pmtiles",
                "pmtiles-batch-job-queue",
                "pmtiles-batch-job-definition",
                builder -> builder.parameters(parameters),
                "parameters: " + parameters
        );
    }

    private ResponseEntity<String> rejectUnlessConfirmed(Boolean confirm, String action) {
        if (Boolean.TRUE.equals(confirm)) {
            return null;
        }
        return ResponseEntity.badRequest().body("You must set confirm to true to really " + action);
    }

    private ResponseEntity<String> submitJob(
            String jobName,
            String jobQueue,
            String jobDefinition,
            Consumer<SubmitJobRequest.Builder> customize,
            String detail) {

        var builder = SubmitJobRequest.builder()
                .jobName(jobName)
                .jobQueue(jobQueue)
                .jobDefinition(jobDefinition);
        customize.accept(builder);

        SubmitJobResponse response = batchClient.submitJob(builder.build());
        return ResponseEntity.ok("Job submitted with jobId: " + response.jobId() + ", " + detail);
    }
}
