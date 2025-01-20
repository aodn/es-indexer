package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.model.DatasetProvider;
import au.org.aodn.stac.model.StacItemModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

import java.io.IOException;
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
    protected String indexName;
    protected ElasticSearchIndexService elasticSearchIndexService;

    @Lazy
    @Autowired
    protected IndexCloudOptimizedServiceImpl self;

    @Autowired
    public IndexCloudOptimizedServiceImpl(
            @Value("${elasticsearch.cloud_optimized_index.name}") String indexName,
            @Qualifier("portalElasticsearchClient") ElasticsearchClient elasticsearchClient,
            ObjectMapper indexerObjectMapper,
            DataAccessService dataAccessService,
            ElasticSearchIndexService elasticSearchIndexService) {

        super(elasticsearchClient, indexerObjectMapper);

        this.indexName = indexName;
        this.indexerObjectMapper = indexerObjectMapper;
        this.dataAccessService = dataAccessService;
        this.elasticSearchIndexService = elasticSearchIndexService;
    }

    @Override
    public List<BulkResponse> indexAllCloudOptimizedData(IndexService.Callback callback) throws IOException {
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.DATASET_INDEX_MAPPING_JSON_FILE, indexName);
        // TODO: Right now just clear schema, we need to query all UUID and iterate one by one
        return null;
    }
    /**
     * Index the cloud optimized data
     * @param uuid - The UUID of data you want to index
     * @param startDate - The start range to index
     * @param endDate - THe end range to index
     * @return - The index result
     */
    @Override
    public List<BulkResponse> indexCloudOptimizedData(String uuid, LocalDate startDate, LocalDate endDate, IndexService.Callback callback) {

        List<BulkResponse> responses = new ArrayList<>();

        Iterable<List<StacItemModel>> dataset = new DatasetProvider(uuid, startDate, endDate, dataAccessService).getIterator();
        BulkRequestProcessor<StacItemModel> bulkRequestProcessor = new BulkRequestProcessor<>(
                indexName, (item) -> Optional.empty(),self, callback
        );

        try {
            for (List<StacItemModel> entries : dataset) {
                if (entries != null) {
                    for(StacItemModel entry: entries) {
                        log.debug("add dataset into b with UUID: {} and props: {}", entry.getUuid(), entry.getProperties());
                        bulkRequestProcessor.processItem(entry.getUuid(), entry)
                                .ifPresent(responses::add);
                    }
                }
            }
            bulkRequestProcessor
                    .flush()
                    .ifPresent(responses::add);

            log.info("Finished execute bulk indexing records to index: {}", indexName);
            callback.onComplete(responses);
        }
        catch (Exception e) {
            log.error("Failed", e);
            throw new RuntimeException("Exception thrown while indexing dataset with UUID: " + uuid + " | " + e.getMessage(), e);
        }
        return responses;
    }
}
