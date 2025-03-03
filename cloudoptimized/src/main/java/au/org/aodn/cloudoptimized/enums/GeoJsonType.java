package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

@Getter
public enum GeoJsonType {
    FEATURE_COLLECTION("FeatureCollection"),
    FEATURE("Feature"),
    POINT("Point"),
    ;

    private final String value;

    GeoJsonType(String value) {
        this.value = value;
    }
}
