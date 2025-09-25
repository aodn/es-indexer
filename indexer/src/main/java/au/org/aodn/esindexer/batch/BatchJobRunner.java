package au.org.aodn.esindexer.batch;

import au.org.aodn.esindexer.controller.IndexerController;
import au.org.aodn.esindexer.service.IndexCloudOptimizedService;
import au.org.aodn.esindexer.service.IndexService;
import au.org.aodn.esindexer.service.IndexerMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
public class BatchJobRunner {

    @Autowired
    private IndexerMetadataService indexerMetadataService;

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


            default:
                throw new IllegalArgumentException("Unknown job name: " + jobName);
        }
        log.info("Batch job completed: {}" , jobName);
    }


    private void indexAllMetadata(String beginWithUuid) {
        log.info("Indexing all metadata");
        try{
            var loggingCallback = new LoggingCallback();
            indexerMetadataService.indexAllMetadataRecordsFromGeoNetwork(beginWithUuid, true, loggingCallback);
        } catch (Exception e) {
            log.error("Error indexing all metadata", e);
        }
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
