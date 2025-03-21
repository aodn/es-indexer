package au.org.aodn.cloudoptimized.model.geojson;

import au.org.aodn.cloudoptimized.enums.GeoJsonType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PointGeoJson implements GeometryGeoJson{
    private final String type = GeoJsonType.POINT.getValue();
    private final List<BigDecimal> coordinates = new ArrayList<>();

    public PointGeoJson(BigDecimal longitude, BigDecimal latitude) {
        coordinates.add(longitude);
        coordinates.add(latitude);
    }
}
