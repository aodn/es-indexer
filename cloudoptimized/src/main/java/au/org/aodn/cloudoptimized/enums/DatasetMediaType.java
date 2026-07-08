package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

@Getter
public enum DatasetMediaType {

    APPLICATION_PARQUET("application/x-parquet"),
    APPLICATION_ZARR("application/x-zarr");

    private final String value;

    DatasetMediaType(String value) {
        this.value = value;
    }
}
