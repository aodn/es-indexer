package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.model.DatasetProvider;
import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.stac.model.StacItemModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class IndexCloudOptimizedServiceImpl extends IndexServiceImpl implements IndexCloudOptimizedService {

    protected DataAccessService dataAccessService;
    protected ObjectMapper indexerObjectMapper;
    protected String indexName;
    protected ElasticSearchIndexService elasticSearchIndexService;

    @Lazy
    @Autowired
    protected IndexCloudOptimizedServiceImpl self;

    public IndexCloudOptimizedServiceImpl(
            String indexName,
            ElasticsearchClient elasticsearchClient,
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
    public boolean hasIndex(String collectionId) {
        try {
            return elasticSearchIndexService.count(this.indexName, "collection", collectionId) > 0;
        }
        catch(IOException | ElasticsearchException exception) {
            // ElasticsearchException when indexName do not exist, this happens in a partial config env
            // but we still need to make sure indexing works as is, backward compatible
            log.warn("Missing index for collectionId {} on index {}", collectionId, this.indexName);
            return false;
        }
    }

    @Override
    public List<BulkResponse> indexAllCloudOptimizedData(IndexService.Callback callback) {
        List<BulkResponse> results = new ArrayList<>();
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.DATASET_INDEX_MAPPING_JSON_FILE, indexName);

        List<MetadataEntity> entities = dataAccessService.getAllMetadata();
        List<MetadataEntity> sorted = entities.stream()
                .sorted(Comparator.comparing(MetadataEntity::getUuid))
                .toList();

        for(MetadataEntity entity : sorted) {
            List<TemporalExtent> temporalExtents = dataAccessService.getTemporalExtentOf(entity.getUuid());
            if (!temporalExtents.isEmpty()) {
                // Only first block works from data service api
                LocalDate startDate = temporalExtents.get(0).getLocalStartDate();
                LocalDate endDate = temporalExtents.get(0).getLocalEndDate();

                callback.onProgress(String.format("Indexing dataset with UUID: %s from %s to %s", entity.getUuid(), startDate, endDate));
                try {
                    results.addAll(indexCloudOptimizedData(entity, startDate, endDate, callback));
                }
                catch(IOException ioe) {
                    // Do nothing
                }
            }
        }
        return results;
    }
    /**
     * Index the cloud optimized data
     * @param entity - The metadata that describe the data
     * @param startDate - The start range to index
     * @param endDate - THe end range to index
     * @return - The index result
     */
    @Override
    public List<BulkResponse> indexCloudOptimizedData(MetadataEntity entity,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      IndexService.Callback callback) throws IOException {

        List<BulkResponse> responses = new ArrayList<>();

        Iterable<List<StacItemModel>> dataset = new DatasetProvider(entity.getUuid(), startDate, endDate, dataAccessService)
                .getIterator(dataAccessService.getFields(entity));

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
            log.error("Exception thrown or not found while indexing cloud optimized data : {}", entity.getUuid(), e);
            throw e;
        }
        return responses;
    }
}
