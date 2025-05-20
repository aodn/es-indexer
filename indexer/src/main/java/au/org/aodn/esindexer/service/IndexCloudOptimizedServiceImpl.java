package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.enums.GeoJsonProperty;
import au.org.aodn.cloudoptimized.model.DatasetProvider;
import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.configuration.AppConstants;
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
        } catch (IOException | ElasticsearchException exception) {
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

        for (MetadataEntity entity : sorted) {
            List<TemporalExtent> temporalExtents = dataAccessService.getTemporalExtentOf(entity.getUuid());
            if (!temporalExtents.isEmpty()) {
                // Only first block works from data service api
                LocalDate startDate = temporalExtents.get(0).getLocalStartDate();
                LocalDate endDate = temporalExtents.get(0).getLocalEndDate();

                callback.onProgress(String.format("Indexing dataset with UUID: %s from %s to %s", entity.getUuid(), startDate, endDate));
                try {
                    results.addAll(indexCloudOptimizedData(entity, startDate, endDate, callback));
                } catch (IOException ioe) {
                    // Do nothing
                }
            }
        }
        return results;
    }

    /**
     * Index the cloud optimized data
     *
     * @param metadata  - The metadata that describe the data
     * @param startDate - The start range to index
     * @param endDate   - THe end range to index
     * @return - The index result
     */
    @Override
    public List<BulkResponse> indexCloudOptimizedData(MetadataEntity metadata,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      IndexService.Callback callback) throws IOException {

        List<BulkResponse> responses = new ArrayList<>();

        callback.onProgress("Indexing cloud optimized data for dataset: " + metadata.getUuid());
        callback.onProgress("Temporal extent: " + startDate + " - " + endDate);

        Iterable<FeatureCollectionGeoJson> datasetIterator = new DatasetProvider(
                metadata.getUuid(),
                startDate,
                endDate,
                dataAccessService,
                dataAccessService.getFields(metadata)
        )
                .getIterator();

        BulkRequestProcessor<FeatureCollectionGeoJson> bulkRequestProcessor = new BulkRequestProcessor<>(
                indexName, (item) -> Optional.empty(), self, callback
        );

        try {
            for (FeatureCollectionGeoJson featureCollection : datasetIterator) {
                if (featureCollection == null) {
                    continue;
                }
                var featureCollections = avoidTooManyNestedObjects(featureCollection);
                if (featureCollections.isEmpty()) {
                    continue;
                }

                if (featureCollections.size() == 1) {
                    Object collection = featureCollections.get(0).getProperties().get(GeoJsonProperty.COLLECTION.getValue());
                    Object date = featureCollections.get(0).getProperties().get(GeoJsonProperty.DATE.getValue());

                    if(collection != null && date != null) {
                        bulkRequestProcessor.processItem(
                                        String.format("%s|%s", collection, date),
                                        featureCollections.get(0), true)
                                .ifPresent(responses::add);
                    }
                } else {
                    for (var i = 0; i < featureCollections.size(); i++) {
                        bulkRequestProcessor.processItem(
                                        featureCollections.get(i).getProperties().get(GeoJsonProperty.COLLECTION.getValue()).toString()
                                                + "|"
                                                + featureCollections.get(i).getProperties().get(GeoJsonProperty.DATE.getValue()).toString()
                                                + "(" + i + ")",
                                        featureCollections.get(i), true)
                                .ifPresent(responses::add);
                    }
                }

                callback.onProgress("Processed data in year month: " + featureCollection.getProperties().get(GeoJsonProperty.DATE.getValue()));
            }

            bulkRequestProcessor
                    .flush()
                    .ifPresent(responses::add);

            log.info("Finished execute bulk indexing records to index: {}", indexName);
            callback.onProgress(responses);
        } catch (Exception e) {
            log.error("Exception thrown or not found while indexing cloud optimized data : {}", metadata.getUuid(), e);
            throw e;
        }
        return responses;
    }

    private List<FeatureCollectionGeoJson> avoidTooManyNestedObjects(FeatureCollectionGeoJson featureCollection) {

        final int MAX_NESTED_OBJECTS = 9000;
        List<FeatureCollectionGeoJson> featureCollections = new ArrayList<>();
        if (featureCollection.getFeatures().size() > MAX_NESTED_OBJECTS) {
            // split the feature collection into smaller ones so that all smaller ones have less than 9000 features. e.g.: first featurecollection is from 0 to 8999, second is from 9000 to 17999, etc.
            log.info("Splitting feature collection with {} features into smaller ones", featureCollection.getFeatures().size());
            int i = 0;
            while (i < featureCollection.getFeatures().size()) {
                FeatureCollectionGeoJson featureCollectionPart = new FeatureCollectionGeoJson();
                featureCollectionPart.setFeatures(featureCollection.getFeatures().subList(i, Math.min(i + 9000, featureCollection.getFeatures().size())));
                featureCollectionPart.setProperties(featureCollection.getProperties());
                featureCollections.add(featureCollectionPart);
                i += 9000;
            }
        } else {
            log.info("Feature collection has {} features", featureCollection.getFeatures().size());
            featureCollections.add(featureCollection);
        }
        return featureCollections;
    }
}
