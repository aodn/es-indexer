package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

/**
 * The kind of data behind a key of the data access service metadata endpoint, sent in the "type" field.
 */
@Getter
public enum MetadataKeyType {

    // A cloud-optimised dataset, e.g. a parquet or zarr dataset
    CLOUD_OPTIMISED("cloud_optimised"),
    // The data-index files of a collection with no cloud-optimised data, e.g. its original NetCDF files
    DATA_INDEX("data_index");

    private final String value;

    MetadataKeyType(String value) {
        this.value = value;
    }

    /**
     * @param value - The "type" value from the data access service
     * @return The matching type, or null if the value is unknown
     */
    public static MetadataKeyType fromValue(String value) {
        for (MetadataKeyType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
