package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public interface IndexService {
    <T> BulkResponse executeBulk(BulkRequest.Builder bulkRequest, Function<BulkResponseItem, Optional<T>> mapper, IndexerMetadataService.Callback callback) throws IOException;
}
