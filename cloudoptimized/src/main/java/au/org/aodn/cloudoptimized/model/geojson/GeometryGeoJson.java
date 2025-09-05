package au.org.aodn.cloudoptimized.model.geojson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PointGeoJson.class, name = "Point"),
        @JsonSubTypes.Type(value = PolygonGeoJson.class, name = "Polygon")

})
public interface GeometryGeoJson {
}
