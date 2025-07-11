package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

@Getter
public enum GeoJsonProperty {

    COLLECTION("collection"),
    DATE("date"),
    COUNT("count"),
    KEY("key");

    private final String value;

    GeoJsonProperty(String value) {
        this.value = value;
    }
}
