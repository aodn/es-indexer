package au.org.aodn.cloudoptimized.model.geojson;


import au.org.aodn.cloudoptimized.enums.GeoJsonType;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Setter
@Getter
public class FeatureCollectionGeoJson  {

    private final String type = GeoJsonType.FEATURE_COLLECTION.getValue();
    private List<FeatureGeoJson> features;
    private Map<String, Object> properties;

    public void addProperty(String key, Object value) {
        if (Objects.isNull(properties)) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }
}
