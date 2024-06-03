package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.DecimalPropertyType;
import au.org.aodn.metadata.iso19115_3_2018.EXGeographicBoundingBoxType;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

public class GeometryBaseTest {

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
        assertTrue("We found a point", point.get() instanceof Point);
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
        assertTrue("We found a polygon", point.get() instanceof Polygon);
    }
}
