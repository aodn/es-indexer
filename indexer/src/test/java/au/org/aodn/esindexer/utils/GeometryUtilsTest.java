package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import jakarta.xml.bind.JAXBException;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

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
        GeometryUtils.setGridSize(10.0);
        GeometryUtils.setGridSpatialExtents(false);
        GeometryUtils.setExecutorService(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

        GeometryUtils.init();
    }

    @Test
    public void verifyLandStrippedFromSpatialExtents() throws IOException, JAXBException {
        String xml = readResourceFile("classpath:canned/sample_complex_area.xml");
        MDMetadataType source = jaxb.unmarshal(xml);
        // Whole spatial extends
        List<List<Geometry>> withLand = GeometryUtils.createGeometryItems(
                source,
                (rawInput) -> GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput)
        );

        List<List<Geometry>> l = Objects.requireNonNull(withLand);

        assertEquals(l.size(),1, "Land have 1 polygon array");
        assertEquals(l.get(0).size(),8, "Size 8 with land");

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
                GeometryUtils::createGeometryWithoutLand
        );

        List<List<Geometry>> nl = Objects.requireNonNull(noLand);

        assertEquals(nl.size(),1, "No Land have 1 polygon array");
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
     * Given a irregular geojson, with hole the centroid point is still inside the polygon
     * @throws IOException
     */
    @Test
    public void verifyCentroidCorrect() throws IOException {
        // You can paste the geojson on geojson.io to see what it looks like
        String geojson = readResourceFile("classpath:canned/irregular.geojson");
        FeatureJSON json = new FeatureJSON();

        // Read the GeoJSON file
        StringReader reader = new StringReader(geojson);
        FeatureCollection<SimpleFeatureType, SimpleFeature> feature = json.readFeatureCollection(reader);

        List<Coordinate> point = GeometryUtils.calculateCollectionCentroid(convertToGeometryCollection(feature));
        assertEquals(1, point.size(), "One item");
        assertEquals(2.805438932281021, point.get(0).getX(),"X");
        assertEquals( 2.0556251797475227, point.get(0).getY(), "Y");
    }

    protected GeometryCollection convertToGeometryCollection(
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
        // Create a GeometryFactory
        GeometryFactory geometryFactory = new GeometryFactory();

        // List to hold the geometries extracted from the FeatureCollection
        List<Geometry> geometries = new ArrayList<>();

        // Iterate through the FeatureCollection and extract geometries
        try(FeatureIterator<SimpleFeature> features = featureCollection.features()) {
            while(features.hasNext()) {
                Geometry geometry = (Geometry)(features.next()).getDefaultGeometry();
                geometries.add(geometry);
            }
        }

        // Convert the list of geometries to an array
        Geometry[] geometryArray = geometries.toArray(new Geometry[0]);

        // Create and return a GeometryCollection from the array of geometries
        return geometryFactory.createGeometryCollection(geometryArray);
    }
}
