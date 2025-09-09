package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

@Getter
public enum GeoJsonTypes {
    FEATURE_COLLECTION("FeatureCollection"),
    FEATURE("Feature"),
    ;

    private final String value;

    GeoJsonTypes(String value) {
        this.value = value;
    }
}
