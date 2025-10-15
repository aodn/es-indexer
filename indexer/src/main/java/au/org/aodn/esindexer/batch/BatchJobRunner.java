package au.org.aodn.esindexer.batch;

import au.org.aodn.esindexer.service.IndexCloudOptimizedService;
import au.org.aodn.esindexer.service.IndexerMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
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
    private IndexCloudOptimizedService indexCloudOptimizedService;

    private static final String INDEX_ALL_METADATA = "indexAllMetadata";
    private static final String INDEX_ALL_METADATA_FROM_UUID = "indexAllMetadataFromUuid";
    private static final String INDEX_ONE_METADATA = "indexMetadata";
    private static final String INDEX_ALL_CLOUD_OPTIMISED_DATASET = "indexAllCODataset";
    private static final String INDEX_ALL_CLOUD_OPTIMISED_DATASET_FROM_UUID = "indexAllCODataFromUuid";
    private static final String INDEX_ONE_CLOUD_OPTIMISED_DATASET = "indexCODataset";

    public void run(String jobName, String jobParam) throws Exception {
        log.info("Starting batch job: {}", jobName);
        switch (jobName) {
            case INDEX_ALL_METADATA:
                throw new NotImplementedException("Index All Metadata not yet implemented");

            case INDEX_ALL_METADATA_FROM_UUID:
                throw new NotImplementedException("Index All Metadata not yet implemented");

            case INDEX_ONE_METADATA:
                throw new NotImplementedException("IndexMetadata not yet implemented");

            case INDEX_ALL_CLOUD_OPTIMISED_DATASET:
                if (jobParam != null) {
                    throw new IllegalArgumentException("Job parameter not required for job: " + jobName);
                }
                indexAllCloudOptimisedDataset(null);
                break;

            case INDEX_ALL_CLOUD_OPTIMISED_DATASET_FROM_UUID:
                if (jobParam == null) {
                    throw new IllegalArgumentException("Job parameter (beginWithUuid) is required for job: " + jobName);
                }
                indexAllCloudOptimisedDataset(jobParam);
                break;

            case INDEX_ONE_CLOUD_OPTIMISED_DATASET:
                if (jobParam == null) {
                    throw new IllegalArgumentException("Job parameter (metadataUuid) is required for job: " + jobName);
                }
                throw new NotImplementedException("Index All Metadata");


            default:
                throw new IllegalArgumentException("Unknown job name: " + jobName);
        }
        log.info("Batch job completed: {}" , jobName);
    }


    private void indexAllCloudOptimisedDataset(String beginWithUuid) {
        log.info("Indexing all cloud optimised dataset");
        try{
            var loggingCallback = new LoggingCallback();
            indexCloudOptimizedService.indexAllCloudOptimizedData(beginWithUuid, loggingCallback);
        } catch (Exception e) {
            log.error("Error indexing all cloud optimised dataset", e);
        }
    }

}
