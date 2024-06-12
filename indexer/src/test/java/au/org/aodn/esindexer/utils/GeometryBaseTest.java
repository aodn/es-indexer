package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.DecimalPropertyType;
import au.org.aodn.metadata.iso19115_3_2018.EXGeographicBoundingBoxType;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;


import org.locationtech.jts.geom.*;

public class GeometryBaseTest {

    private static final GeometryFactory factory = new GeometryFactory();

    @Test
    public void verifyGetCoordinatesPoint() {
        EXGeographicBoundingBoxType boundingBoxType = new EXGeographicBoundingBoxType();

        DecimalPropertyType a = new DecimalPropertyType();
        a.setDecimal(BigDecimal.valueOf(146.85));

        boundingBoxType.setEastBoundLongitude(a);
        boundingBoxType.setWestBoundLongitude(a);

        DecimalPropertyType b = new DecimalPropertyType();
        b.setDecimal(BigDecimal.valueOf(-19.168333));

        boundingBoxType.setSouthBoundLatitude(b);
        boundingBoxType.setNorthBoundLatitude(b);

        Optional<Geometry> point = GeometryBase.getCoordinates(boundingBoxType);
        assertTrue(point.get() instanceof Point, "We found a point");
    }

    @Test
    public void verifyGetCoordinatesPolygon() {
        EXGeographicBoundingBoxType boundingBoxType = new EXGeographicBoundingBoxType();

        DecimalPropertyType w = new DecimalPropertyType();
        w.setDecimal(BigDecimal.valueOf(60));
        boundingBoxType.setWestBoundLongitude(w);

        DecimalPropertyType s = new DecimalPropertyType();
        s.setDecimal(BigDecimal.valueOf(-68));
        boundingBoxType.setSouthBoundLatitude(s);

        DecimalPropertyType e = new DecimalPropertyType();
        e.setDecimal(BigDecimal.valueOf(78));
        boundingBoxType.setEastBoundLongitude(e);

        DecimalPropertyType n = new DecimalPropertyType();
        n.setDecimal(BigDecimal.valueOf(-66));
        boundingBoxType.setNorthBoundLatitude(n);

        Optional<Geometry> point = GeometryBase.getCoordinates(boundingBoxType);
        assertTrue(point.get() instanceof Polygon, "We found a polygon");
    }

    @Test
    public void testIsCounterClockwise() {
        Coordinate[] ccwCoords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0)
        };

        Coordinate[] cwCoords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(0, 1),
                new Coordinate(1, 1),
                new Coordinate(1, 0),
                new Coordinate(0, 0)
        };

        assertTrue(GeometryUtils.isCounterClockwise(ccwCoords));
        assertFalse(GeometryUtils.isCounterClockwise(cwCoords));
    }

    @Test
    public void testReverseCoordinates() {
        Coordinate[] coords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0)
        };

        Coordinate[] expectedReversedCoords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(0, 1),
                new Coordinate(1, 1),
                new Coordinate(1, 0),
                new Coordinate(0, 0)
        };

        Coordinate[] reversedCoords = GeometryUtils.reverseCoordinates(coords);
        assertArrayEquals(expectedReversedCoords, reversedCoords);
    }

    @Test
    public void testEnsureCounterClockwise() {
        // A clockwise polygon
        Coordinate[] cwCoords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(0, 1),
                new Coordinate(1, 1),
                new Coordinate(1, 0),
                new Coordinate(0, 0)
        };
        LinearRing cwRing = factory.createLinearRing(cwCoords);
        Polygon cwPolygon = factory.createPolygon(cwRing);

        Polygon ccwPolygon = GeometryUtils.ensureCounterClockwise(cwPolygon, factory);
        assertTrue(GeometryUtils.isCounterClockwise(ccwPolygon.getExteriorRing().getCoordinates()));

        // A counterclockwise polygon should remain unchanged
        Coordinate[] ccwCoords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0)
        };
        LinearRing ccwRing = factory.createLinearRing(ccwCoords);
        Polygon originalCcwPolygon = factory.createPolygon(ccwRing);

        Polygon ensuredCcwPolygon = GeometryUtils.ensureCounterClockwise(originalCcwPolygon, factory);
        assertTrue(GeometryUtils.isCounterClockwise(ensuredCcwPolygon.getExteriorRing().getCoordinates()));
    }
}
