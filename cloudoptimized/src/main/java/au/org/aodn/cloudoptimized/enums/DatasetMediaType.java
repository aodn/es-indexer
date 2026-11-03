package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

@Getter
public enum DatasetMediaType {

    APPLICATION_PARQUET("application/x-parquet"),
    APPLICATION_ZARR("application/x-zarr"),
    // The list of original NetCDF files of a collection
    APPLICATION_NETCDF_LIST("application/x-netcdf");

    private final String value;

    DatasetMediaType(String value) {
        this.value = value;
    }
}
