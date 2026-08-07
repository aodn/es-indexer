package au.org.aodn.esindexer.batch;

import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.service.IndexerMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * BatchJobRunner is responsible for executing batch jobs based on the provided job name and parameters.
 * It supports various indexing operations, including indexing metadata and cloud-optimized datasets.
 * <p>
 * Usage:
 * When submitting a batch job, override the environment variable "APP_ARGS" with 2 or 3 arguments seperated by space;
 * The first argument is "batch" to indicate a batch job.
 * The second argument is the job name, which can be one of the following:
 * <ul>
 *     <li>indexAllMetadata</li>
 *     <li>indexAllMetadataFromUuid</li>
 *     <li>indexMetadata</li>
 *     <li>indexAllCODataset</li>
 *     <li>indexAllCODataFromUuid</li>
 *     <li>indexCODataset</li>
 * </ul>
 *     The third argument is optional and depends on the job name;
 *
 * <p>
 * Note: Some jobs are not yet implemented and will throw a NotImplementedException if invoked.
 */
@Slf4j
@Component
@Order(0)
public class BatchJobRunner {

    @Autowired
    private IndexerMetadataService indexerMetadataService;

    @Autowired
    protected DataAccessService dataAccessService;

    private static final String INDEX_ALL_METADATA = "indexAllMetadata";
    private static final String INDEX_ALL_METADATA_FROM_UUID = "indexAllMetadataFromUuid";

    public void run(String jobName, String jobParam) throws Exception {
        log.info("Starting batch job: {}", jobName);
        switch (jobName) {
            case INDEX_ALL_METADATA:
                if (jobParam != null) {
                    throw new IllegalArgumentException("Job parameter not required for job: " + jobName);
                }
                indexAllMetadata(null);
                break;

            case INDEX_ALL_METADATA_FROM_UUID:
                if (jobParam == null) {
                    throw new IllegalArgumentException("Job parameter (beginWithUuid) is required for job: " + jobName);
                }
                indexAllMetadata(jobParam);
                break;

            default:
                throw new IllegalArgumentException("Unknown job name: " + jobName);
        }
        log.info("Batch job completed: {}" , jobName);
    }

    private void indexAllMetadata(String beginWithUuid) {
        log.info("Indexing all metadata");
        if (beginWithUuid != null) {
            log.info("Beginning with uuid: {}", beginWithUuid);
        }

        try{
            var loggingCallback = new LoggingCallback();
            indexerMetadataService.indexAllMetadataRecordsFromGeoNetwork(beginWithUuid, true, loggingCallback);
        } catch (Exception e) {
            log.error("Error indexing all metadata", e);
        }
    }

}
