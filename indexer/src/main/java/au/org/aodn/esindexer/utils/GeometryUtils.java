package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.AbstractEXGeographicExtentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryUtils {

    protected static Logger logger = LogManager.getLogger(GeometryUtils.class);

    protected static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    protected static ObjectMapper objectMapper = new ObjectMapper();

    protected static boolean areCollinear(Coordinate p1, Coordinate p2, Coordinate p3) {
        // Use the area of the triangle method to check collinearity
        double area = p1.x * (p2.y - p3.y) +
                p2.x * (p3.y - p1.y) +
                p3.x * (p1.y - p2.y);
        return area == 0;
    }

    protected static boolean hasAtLeastThreeNonCollinearPoints(MultiPolygon multiPolygon) {
        // Extract coordinates
        Coordinate[] coordinates = multiPolygon.getCoordinates();

        // Check all triplets of points
        for (int i = 0; i < coordinates.length - 2; i++) {
            for (int j = i + 1; j < coordinates.length - 1; j++) {
                for (int k = j + 1; k < coordinates.length; k++) {
                    if (!areCollinear(coordinates[i], coordinates[j], coordinates[k])) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    /**
     *
     * @param polygons - Assume to be EPSG:4326, as GeoJson always use this encoding.
     * @return
     */
    protected static Map<?,?> createGeoJson(List<List<Polygon>> polygons) {

        if(!polygons.isEmpty()) {

            // Convert list<list<polygon>> to list<polygon>
            List<Polygon> reduced = polygons.stream().flatMap(List::stream).toList();
            MultiPolygon multiPolygon = new MultiPolygon(reduced.toArray(new Polygon[0]), factory);

            // Some bad data just create a line which will cause elastic polygon failed
            if(hasAtLeastThreeNonCollinearPoints(multiPolygon)) {
                try (StringWriter writer = new StringWriter()) {

                    GeometryJSON geometryJson = new GeometryJSON();
                    geometryJson.write(multiPolygon, writer);

                    Map<?, ?> values = objectMapper.readValue(writer.toString(), HashMap.class);

                    logger.debug("Created geometry {}", values);
                    return values;
                } catch (IOException e) {
                    logger.error("Error create geometry", e);
                    return null;
                }
            }
            else {
                logger.warn("Polygon invalid, less than 3 non collinear points, this cannot store in Elastic Search");
            }
        }
        return null;
    }

    public static Map<?,?> createGeometryFrom(List<List<AbstractEXGeographicExtentType>> rawInput) {
        // The return polygon is in EPSG:4326, so we can call createGeoJson directly
        //TODO: avoid hardcode CRS, get it from document
        List<List<Polygon>> polygons = GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);
        return (polygons != null && !polygons.isEmpty()) ? createGeoJson(polygons) : null;
    }
}
