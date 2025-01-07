package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.DatasetProvider;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class IndexCloudOptimizedServiceImpl extends IndexServiceImpl implements IndexCloudOptimizedService {

    protected DataAccessService dataAccessService;
    protected ObjectMapper indexerObjectMapper;
    protected String datasetIndexName;

    @Lazy
    @Autowired
    protected IndexCloudOptimizedServiceImpl self;

    @Autowired
    public IndexCloudOptimizedServiceImpl(
            @Value("${elasticsearch.dataset_index.name}") String datasetIndexName,
            @Qualifier("portalElasticsearchClient") ElasticsearchClient elasticsearchClient,
            ObjectMapper indexerObjectMapper,
            DataAccessService dataAccessService) {

        super(elasticsearchClient, indexerObjectMapper);

        this.datasetIndexName = datasetIndexName;
        this.indexerObjectMapper = indexerObjectMapper;
        this.dataAccessService = dataAccessService;
    }

    // TODO: Refactor this method later since it uses similar logic as indexAllMetadataRecordsFromGeoNetwork
    @Override
    public List<BulkResponse> indexCloudOptimizedData(String uuid, LocalDate startDate, LocalDate endDate) {

        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
        List<BulkResponse> responses = new ArrayList<>();

        long dataSize = 0;
        final long maxSize = 5242880; // is 5mb

        var dataset = new DatasetProvider(uuid, startDate, endDate, dataAccessService).getIterator();
        try {
            for (var entry : dataset) {
                if (entry == null) {
                    continue;
                }
                log.info("add dataset into b with UUID: {} and yearMonth: {}", entry.uuid(), entry.yearMonth());

                bulkRequest.operations(operation -> operation.index(
                        indexReq -> indexReq
                                .id(entry.uuid() + entry.yearMonth())
                                .index(datasetIndexName)
                                .document(entry)
                ));
                dataSize += indexerObjectMapper.writeValueAsBytes(entry).length;
                if (dataSize > maxSize) {
                    log.info("Execute bulk request as bulk request is big enough {}", dataSize);
                    responses.add(reduceResponse(self.executeBulk(bulkRequest, (item) -> Optional.empty(), null)));
                    dataSize = 0;
                    bulkRequest = new BulkRequest.Builder();
                }
            }
            log.info("Finished execute bulk indexing records to index: {}", datasetIndexName);
            responses.add(reduceResponse(self.executeBulk(bulkRequest, (item) -> Optional.empty(), null)));
        } catch (Exception e) {
            log.error("Failed", e);
            throw new RuntimeException("Exception thrown while indexing dataset with UUID: " + uuid + " | " + e.getMessage(), e);
        }
        return responses;
    }
}
