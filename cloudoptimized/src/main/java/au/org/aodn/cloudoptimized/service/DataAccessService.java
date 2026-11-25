package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.model.MetadataFields;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    /**
     * Get spatial extents value from the cloud optimize data
     * @param uuid - UUID of dataset
     * @param key - UUID can map to multiple dataset, you need a key to find your target
     * @return - The TemporalExtent value
     */
    List<TemporalExtent> getTemporalExtentOf(String uuid, String key);
    /**
     * Get the notebook url of a cloud optimized dataset, the url is returned only if it is usable. The metadata is
     * the point of truth, so if the metadata already has the same link, nothing is returned.
     *
     * @param uuid - UUID of dataset
     * @param existingHrefs - The hrefs of the links already in the metadata, can be null or empty
     * @return - The url, or empty if there is no notebook, the metadata already has it, or the url is not usable
     */
    Optional<String> getNotebookLink(String uuid, Set<String> existingHrefs);
    Map<String, MetadataEntity> getMetadataByUuid(String uuid);
    Map<String, Map<String, MetadataEntity>> getAllMetadata();
    HealthStatus getHealthStatus();
    void waitTillServiceUp();
}
