package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.core.BulkResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface IndexCloudOptimizedService extends IndexService {
    List<BulkResponse> indexCloudOptimizedData(String uuid, LocalDate startDate, LocalDate endDate, IndexService.Callback callback);
    List<BulkResponse> indexAllCloudOptimizedData(IndexService.Callback callback) throws IOException;
}
