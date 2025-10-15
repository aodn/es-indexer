package au.org.aodn.cloudoptimized.model.geojson;


import au.org.aodn.cloudoptimized.enums.GeoJsonTypes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class FeatureCollectionGeoJson  {

    @JsonProperty("type")
    private final String type = GeoJsonTypes.FEATURE_COLLECTION.getValue();
    @JsonProperty("features")
    private List<FeatureGeoJson> features;
    @JsonProperty("properties")
    private Map<String, Object> properties = new HashMap<>();

    public void addProperty(String key, Object value) {
        if (Objects.isNull(properties)) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }
}
