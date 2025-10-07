package au.org.aodn.cloudoptimized.model.geojson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class FeatureGeoJson {

    private final String type;
    private final GeometryGeoJson geometry;
    private final Map<String, Object> properties;

    public FeatureGeoJson(GeometryGeoJson geometry) {
        this.type = "Feature";
        this.geometry = geometry;
        this.properties = new HashMap<>();
    }

    @JsonCreator
    public FeatureGeoJson(
            @JsonProperty("type") String type,
            @JsonProperty("geometry") GeometryGeoJson geometry,
            @JsonProperty("properties") Map<String, Object> properties) {
        this.type = type;
        this.geometry = geometry;
        this.properties = properties != null ? properties : new HashMap<>();
    }


    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }
}
