package au.org.aodn.esindexer.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IndexerControllerTest {

    @Mock
    private BatchClient batchClient;

    @InjectMocks
    private IndexerController indexerController;

    @Captor
    private ArgumentCaptor<SubmitJobRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    void mockSubmitJob() {
        when(batchClient.submitJob(any(SubmitJobRequest.class)))
                .thenReturn(SubmitJobResponse.builder().jobId("job-1").build());
    }

    @Test
    void generatePmTilesInBatchWithoutConfirmShouldNotSubmit() {
        var response = indexerController.generatePmTilesInBatch(false, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(batchClient, never()).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    void generatePmTilesInBatchWithoutUuidShouldSubmitForAllDatasets() {
        mockSubmitJob();

        var response = indexerController.generatePmTilesInBatch(true, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("job-1"));

        verify(batchClient).submitJob(requestCaptor.capture());
        var request = requestCaptor.getValue();

        assertEquals("generate-pmtiles", request.jobName());
        assertEquals("pmtiles-batch-job-queue", request.jobQueue());
        assertEquals("pmtiles-batch-job-definition", request.jobDefinition());
        // entry_point.py switches on "type", no "uuid" means every parquet dataset
        assertEquals("generate-pmtiles-for-parquet", request.parameters().get("type"));
        assertFalse(request.parameters().containsKey("uuid"));
    }

    @Test
    void generatePmTilesInBatchWithUuidShouldSubmitForThatUuidOnly() {
        mockSubmitJob();

        var response = indexerController.generatePmTilesInBatch(true, "  abc-123  ");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(batchClient).submitJob(requestCaptor.capture());
        var parameters = requestCaptor.getValue().parameters();

        assertEquals("generate-pmtiles-for-parquet", parameters.get("type"));
        assertEquals("abc-123", parameters.get("uuid"));
    }

    @Test
    void generatePmTilesInBatchWithBlankUuidShouldSubmitForAllDatasets() {
        mockSubmitJob();

        indexerController.generatePmTilesInBatch(true, "   ");

        verify(batchClient).submitJob(requestCaptor.capture());
        assertFalse(requestCaptor.getValue().parameters().containsKey("uuid"));
    }
}
