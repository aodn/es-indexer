package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.model.MetadataEntity;
import co.elastic.clients.elasticsearch.core.BulkResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface IndexCloudOptimizedService extends IndexService {
    // We have indexed cloud optimized data indexed in ElasticSearch
    boolean hasIndex(String collectionId);
    String getHitId(String collectionId);
    List<BulkResponse> indexCloudOptimizedData(MetadataEntity entity, LocalDate startDate, LocalDate endDate, IndexService.Callback callback) throws IOException;
    List<BulkResponse> indexAllCloudOptimizedData(String beginWithUuid, IndexService.Callback callback) throws IOException;
}
