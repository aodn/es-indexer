package au.org.aodn.cloudoptimized.model.geojson;

import lombok.Getter;

import java.util.List;

@Getter
public class PolygonGeoJson implements GeometryGeoJson {

    private final List<List<List<Double>>> coordinates;

    public PolygonGeoJson(final List<List<List<Double>>> coordinates) {
        this.coordinates = coordinates;
    }
}
