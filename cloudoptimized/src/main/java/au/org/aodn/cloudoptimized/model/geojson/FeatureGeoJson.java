package au.org.aodn.cloudoptimized.model.geojson;

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

    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }
}
