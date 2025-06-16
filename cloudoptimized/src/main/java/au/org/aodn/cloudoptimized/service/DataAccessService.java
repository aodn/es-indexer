package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.model.CloudOptimizedEntry;
import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.model.MetadataFields;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DataAccessService {

    enum HealthStatus {
        STARTING("STARTING"),
        UP("UP"),
        UNKNOWN("UNKNOWN");

        private final String status;

        HealthStatus(String status) {
            this.status = status;
        }

        public static HealthStatus fromValue(String status) {
            for (HealthStatus s : HealthStatus.values()) {
                if(s.status.equalsIgnoreCase(status)) {
                    return s;
                }
            }
            return HealthStatus.UNKNOWN;
        }
    }

    default List<MetadataFields> getFields(MetadataEntity entity) {
        return entity.getDepth() != null ?
                List.of(MetadataFields.TIME, MetadataFields.DEPTH, MetadataFields.LONGITUDE, MetadataFields.LATITUDE) :
                List.of(MetadataFields.TIME, MetadataFields.LONGITUDE, MetadataFields.LATITUDE);
    }

    void aggregateData(Map<CloudOptimizedEntry, Long> merge, List<? extends CloudOptimizedEntry> data);
    FeatureCollectionGeoJson getIndexingDatasetByMonth(String uuid, YearMonth yearMonth, List<MetadataFields> fields);
    List<TemporalExtent> getTemporalExtentOf(String uuid);
    Optional<String> getNotebookLink(String uuid);
    MetadataEntity getMetadataByUuid(String uuid);
    List<MetadataEntity> getAllMetadata();
    HealthStatus getHealthStatus();
}
