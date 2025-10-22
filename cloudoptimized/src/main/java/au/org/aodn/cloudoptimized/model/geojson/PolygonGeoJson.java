package au.org.aodn.cloudoptimized.model.geojson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class PolygonGeoJson implements GeometryGeoJson {

    private final List<List<List<Double>>> coordinates;

    @JsonCreator
    public PolygonGeoJson(@JsonProperty("coordinates") List<List<List<Double>>> coordinates) {
        this.coordinates = coordinates;
    }


}
