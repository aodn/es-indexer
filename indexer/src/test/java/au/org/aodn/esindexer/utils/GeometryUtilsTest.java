package au.org.aodn.esindexer.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.List;

@Slf4j
public class GeometryUtilsTest {

    @Test
    public void verifyLandStrippedFromSpatialExtents() {
        GeometryFactory geometryFactory = new GeometryFactory();
        // Step 2: Create some Point geometries
        Polygon polygon = geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(121.65, -33.86),
                new Coordinate(153.97, -33.86),
                new Coordinate(153.97, -9.19),
                new Coordinate(121.65, -9.19),
                new Coordinate(121.65, -33.86)
        });

        List<List<Geometry>> output = GeometryUtils.removeLandAreaFromGeometry(List.of(List.of(polygon)));

        log.info(output.toString());
    }
}
