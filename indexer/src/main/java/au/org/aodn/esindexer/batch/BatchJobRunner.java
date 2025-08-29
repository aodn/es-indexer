package au.org.aodn.esindexer.batch;

import au.org.aodn.esindexer.controller.IndexerController;
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
    private IndexerController indexerController;

    private static final String INDEX_ALL_METADATA = "indexAllMetadata";
    private static final String INDEX_ALL_METADATA_FROM_UUID = "indexAllMetadataFromUuid";
    private static final String INDEX_METADATA = "indexMetadata";



    public void run(String jobName) throws Exception {
        log.info("Starting batch job: {}", jobName);
        switch (jobName) {
            case INDEX_ALL_METADATA:
                indexAllMetadata();
                break;
            case INDEX_ALL_METADATA_FROM_UUID:
                throw new NotImplementedException("IndexAllMetadataFromUuid not yet implemented");
            case INDEX_METADATA:
                throw new NotImplementedException("IndexMetadata not yet implemented");
            default:
                throw new IllegalArgumentException("Unknown job name: " + jobName);
        }
        log.info("Batch job completed: " + jobName);
    }


    private void indexAllMetadata() {
        log.info("Indexing all metadata");
        try{
            indexerController.indexAllMetadataRecords(true, "toxicity_hm_sw");
        } catch (Exception e) {
            log.error("Error indexing all metadata", e);
        }
    }

}
