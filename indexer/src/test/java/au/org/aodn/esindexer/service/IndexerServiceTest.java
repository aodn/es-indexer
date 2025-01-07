package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit test that do not require other dependent software running goes here
 */
@Slf4j
public class IndexerServiceTest {

    /**
     * Verify this function, it should only return error if items contains status no CREATED or OK
     */
    @Test
    public void verifyReduceResponse() {
        BulkResponse bulkResponse = BulkResponse.of(b ->
                b.items(
                        BulkResponseItem.of(i -> i
                                .status(HttpStatus.CREATED.value())
                                .operationType(OperationType.Create)
                                .index("A")
                        ),
                        BulkResponseItem.of(i -> i
                                .status(HttpStatus.OK.value())
                                .operationType(OperationType.Update)
                                .index("A")
                        ))
                        .errors(false).took(1)
        );

        bulkResponse = IndexerMetadataServiceImpl.reduceResponse(bulkResponse);
        Assertions.assertFalse(bulkResponse.errors(), "Should not contain error");

        bulkResponse = BulkResponse.of(b ->
                b.items(
                        BulkResponseItem.of(i -> i
                                .status(HttpStatus.CREATED.value())
                                .operationType(OperationType.Create)
                                .index("B")
                        ),
                        BulkResponseItem.of(i -> i
                                .status(HttpStatus.NOT_EXTENDED.value())
                                .operationType(OperationType.Index)
                                .index("B")
                        ))
                        .errors(false).took(1)
        );

        bulkResponse = IndexerMetadataServiceImpl.reduceResponse(bulkResponse);
        Assertions.assertTrue(bulkResponse.errors(), "Should contain error");
    }
}
