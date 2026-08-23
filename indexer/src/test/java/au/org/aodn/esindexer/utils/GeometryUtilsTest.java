package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeometryUtilsTest {
    protected Logger logger = LoggerFactory.getLogger(GeometryUtilsTest.class);
    protected JaxbUtils<MDMetadataType> jaxb;

    public GeometryUtilsTest() throws JAXBException {
        jaxb = new JaxbUtils<>(MDMetadataType .class);
    }

    @BeforeEach
    public void init() {
        GeometryUtils.setCoastalPrecision(0.03);
    }

    @Test
    public void verifyLandStrippedFromSpatialExtents() throws IOException, JAXBException {
        GeometryUtils.setReducerPrecision(null);
        GeometryUtils.init();

        String xml = readResourceFile("classpath:canned/sample_complex_area.xml");
        MDMetadataType source = jaxb.unmarshal(xml);
        // Whole spatial extends
        List<List<Geometry>> withLand = GeometryUtils.createGeometryItems(
                source,
                (rawInput, size) -> GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput),
                null
        );

        List<List<Geometry>> l = Objects.requireNonNull(withLand);

        assertEquals(1, l.size(), "Land have 1 polygon array");
        assertEquals(8, l.get(0).size(), "Size 8 with land");

        Geometry le = l.get(0).get(0);
        Coordinate[] coors = le.getCoordinates();

        assertEquals(118.0, coors[0].getX(), 0.001);
        assertEquals(-36.0, coors[0].getY(), 0.001);

        assertEquals(118.0, coors[1].getX(), 0.001);
        assertEquals(-34.0, coors[1].getY(), 0.001);

        assertEquals(122.0, coors[2].getX(), 0.001);
        assertEquals(-34.0, coors[2].getY(), 0.001);

        assertEquals(122.0, coors[3].getX(), 0.001);
        assertEquals(-32.0, coors[3].getY(), 0.001);
        // Strip the land away.
        List<List<Geometry>> noLand = GeometryUtils.createGeometryItems(
                source,
                (rawInput, s) -> GeometryUtils.createGeometryWithoutLand(rawInput),
                null
        );

        List<List<Geometry>> nl = Objects.requireNonNull(noLand);

        assertEquals(1, nl.size(), "No Land have 1 polygon array");
        assertEquals(11, nl.get(0).size(), "Size 11 with land");

        Geometry nle = nl.get(0).get(0).getEnvelope();
        Coordinate[] ncoors = nle.getCoordinates();

        // The envelope of the two polygon should match given one is the original and the other just strip the land
        assertEquals(118.0, ncoors[0].getX(), 0.01);
        assertEquals(-35.9999, ncoors[0].getY(), 0.01);

        assertEquals(118.0, ncoors[1].getX(), 0.01);
        assertEquals(-32.2787, ncoors[1].getY(), 0.01);

        assertEquals(126, ncoors[2].getX(), 0.01);
        assertEquals(-32.2787, ncoors[2].getY(), 0.01);

        assertEquals(126.0, ncoors[3].getX(), 0.01);
        assertEquals(-35.9999, ncoors[3].getY(), 0.01);
    }
    /**
     * This test turn on the reducer to further reduce the complexity of the land area after
     * DouglasPeuckerSimplifier simplifier, this further reduce the number of digit to get a smaller geojson
     * which should improve transfer speed.
     *
     * @throws IOException - Not expect to throw
     * @throws JAXBException - Not expect to throw
     */
    @Test
    public void verifyLandStrippedFromSpatialExtentsWithReducerOn() throws IOException, JAXBException {
        GeometryUtils.setReducerPrecision(4.0);
        GeometryUtils.init();

        String xml = readResourceFile("classpath:canned/sample_complex_area.xml");
        MDMetadataType source = jaxb.unmarshal(xml);

        // Strip the land away.
        List<List<Geometry>> noLand = GeometryUtils.createGeometryItems(
                source,
                (rawInput, s) -> GeometryUtils.createGeometryWithoutLand(rawInput),
                null
        );

        List<List<Geometry>> nl = Objects.requireNonNull(noLand);

        assertEquals(1, nl.size(), "No Land have 1 polygon array");
        assertEquals(16, nl.get(0).size(), "Size 16 with land");

        Geometry nle = nl.get(0).get(0).getEnvelope();
        Coordinate[] ncoors = nle.getCoordinates();

        // The envelope of the two polygon should match given one is the original and the other just strip the land
        assertEquals(118.0, ncoors[0].getX(), 0.00);
        assertEquals(-36.0, ncoors[0].getY(), 0.00);

        assertEquals(118.0, ncoors[1].getX(), 0.00);
        assertEquals(-32.25, ncoors[1].getY(), 0.00);

        assertEquals(126, ncoors[2].getX(), 0.00);
        assertEquals(-32.25, ncoors[2].getY(), 0.00);

        assertEquals(126.0, ncoors[3].getX(), 0.00);
        assertEquals(-36.0, ncoors[3].getY(), 0.00);

        assertEquals(118.0, ncoors[4].getX(), 0.00);
        assertEquals(-36.0, ncoors[4].getY(), 0.00);
    }
    /**
     * COWCLIP global metadata uses west=-180, east=179. createGeometryNoLandFrom (land strip)
     * previously produced self-intersecting rings near lon=179 that Elasticsearch geo_shape
     * rejected. Ensure the no-land path yields non-empty, valid polygons and serializable geojson.
     * Record: 1de0e8b1-4777-4526-b3d7-805938b8e6bc / bug 8072.
     */
    @Test
    public void verifyGlobalBboxNoLandGeometriesAreValid() throws IOException, JAXBException {
        GeometryUtils.setReducerPrecision(4.0);
        GeometryUtils.setCoastalPrecision(0.5);
        GeometryUtils.init();

        String xml = readResourceFile("classpath:canned/sample_cowclip_global_bbox.xml");
        MDMetadataType source = jaxb.unmarshal(xml);

        List<List<Geometry>> noLand = GeometryUtils.createGeometryItems(
                source,
                (rawInput, s) -> GeometryUtils.createGeometryWithoutLand(rawInput),
                null
        );

        List<List<Geometry>> nl = Objects.requireNonNull(noLand);
        Assertions.assertFalse(nl.isEmpty(), "Expected geometry after land strip");

        int invalid = 0;
        int parts = 0;
        for (List<Geometry> group : nl) {
            for (Geometry g : group) {
                parts++;
                if (!g.isValid()) {
                    invalid++;
                    logger.error("Invalid no-land geometry: type={} envelope={}",
                            g.getGeometryType(), g.getEnvelopeInternal());
                }
            }
        }
        Assertions.assertTrue(parts > 0, "Expected at least one geometry part");
        assertEquals(0, invalid, "All no-land geometries must be valid for geo_shape indexing");

        Map<?, ?> geoJson = GeometryUtils.createGeometryItems(
                source,
                GeometryUtils::createGeometryNoLandFrom,
                null
        );
        Assertions.assertNotNull(geoJson, "createGeometryNoLandFrom must produce geojson");
        Assertions.assertEquals("GeometryCollection", geoJson.get("type"));
    }

    /**
     * f1578_1461_5604_5121 (sample26): west=-280, east=80. DEV uses coastalPrecision 0.5
     * and reducerPrecision 4.0.
     */
    @Test
    public void verifySample26NoLandGeometriesAreValidWithDevPrecision() throws IOException, JAXBException {
        GeometryUtils.setReducerPrecision(4.0);
        GeometryUtils.setCoastalPrecision(0.5);
        GeometryUtils.init();

        String xml = readResourceFile("classpath:canned/sample26.xml");
        MDMetadataType source = jaxb.unmarshal(xml);

        Map<?, ?> geoJson = GeometryUtils.createGeometryItems(
                source,
                GeometryUtils::createGeometryNoLandFrom,
                null
        );
        Assertions.assertNotNull(geoJson, "createGeometryNoLandFrom must produce geojson");
        Assertions.assertEquals("GeometryCollection", geoJson.get("type"));
    }

    /**
     * Given a point call this function return a GeometryCollection contain a single point
     */
    @Test
    public void verifyCreateJsonPoint() {
        Map<?,?> item = GeometryUtils.createGeoShapeJson(
                BigDecimal.valueOf(1.2),
                BigDecimal.valueOf(2.2)
        );

        Assertions.assertNotNull(item);
        Assertions.assertEquals("GeometryCollection", item.get("type"));
        Assertions.assertInstanceOf(List.class, item.get("geometries"));

        List<Map<?,?>> geometries = (List<Map<?,?>>)item.get("geometries");
        Assertions.assertInstanceOf(List.class, geometries.get(0).get("coordinates"));

        List<?> coors = (List<?>)geometries.get(0).get("coordinates");
        Assertions.assertEquals(1.2, coors.get(0));
        Assertions.assertEquals(2.2, coors.get(1));
    }
}
