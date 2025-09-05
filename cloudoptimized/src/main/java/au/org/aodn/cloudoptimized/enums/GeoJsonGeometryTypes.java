package au.org.aodn.cloudoptimized.enums;

import lombok.Getter;

@Getter
public enum GeoJsonGeometryTypes {

    POINT("Point"),
    MULTI_POINT("MultiPoint"),
    LINE_STRING("LineString"),
    MULTI_LINE_STRING("MultiLineString"),
    POLYGON("Polygon"),
    MULTI_POLYGON("MultiPolygon"),
    GEOMETRY_COLLECTION("GeometryCollection"),
    ;
    private final String value;

    GeoJsonGeometryTypes(String value) {
        this.value = value;
    }
}
