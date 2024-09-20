package au.org.aodn.esindexer.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.List;

import static org.junit.Assert.assertFalse;

@Slf4j
public class GeometryUtilsTest {

    @Test
    public void verifyLandStrippedFromSpatialExtents() {
        GeometryFactory geometryFactory = new GeometryFactory();
        // Step 2: Create some Point geometries
        Polygon polygon = geometryFactory.createPolygon(new Coordinate[]{
                new Coordinate(121.65, -33.86),
                new Coordinate(153.97, -33.86),
                new Coordinate(153.97, -9.19),
                new Coordinate(121.65, -9.19),
                new Coordinate(121.65, -33.86)
        });

        List<List<Geometry>> output = GeometryUtils.removeLandAreaFromGeometryAndGridded(List.of(List.of(polygon)));

        log.info(output.toString());
    }
    /**
     * Just to make sure that with small number the function handle it correctly
     */
    @Test
    public void verifyHasNonCollinearPoints() {
        GeometryFactory geometryFactory = new GeometryFactory();
        // Step 2: Create some Point geometries
        Polygon polygon = geometryFactory.createPolygon(new Coordinate[]{
                new Coordinate(156, -32.00000000000001),
                new Coordinate(152.5899971190634, -32.00000000000001),
                new Coordinate(152.5899971190634, -32),
                new Coordinate(156, -32),
                new Coordinate(156, -32.00000000000001)
        });

        Assertions.assertFalse(GeometryUtils.hasNonCollinearPoints(polygon), "Has non collinear point");
    }
}
