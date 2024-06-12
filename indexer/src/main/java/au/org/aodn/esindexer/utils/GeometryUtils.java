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

    /**
     * @param polygons - Assume to be EPSG:4326, as GeoJson always use this encoding.
     * @return
     */
    protected static Map<?,?> createGeoJson(List<List<Geometry>> polygons) {

        if(!polygons.isEmpty()) {
            // Convert list<list<polygon>> to list<polygon>
            List<Geometry> reduced = polygons.stream().flatMap(List::stream).toList();
            List<Geometry> orientedPolygons = reduced.stream()
                    .map(geometry -> {
                        if (geometry instanceof Polygon) {
                            // Ensure all polygons follow the right-hand rule
                            // The right-hand rule is a convention used to maintain consistency in the orientation of vertices for polygons.
                            // For a polygon, adhering to the right-hand rule means that its vertices are ordered counterclockwise for the exterior ring
                            // and clockwise for any interior rings (holes).
                            // Standard: https://www.rfc-editor.org/rfc/rfc7946#section-3.1.6
                            return GeometryUtils.ensureCounterClockwise((Polygon) geometry, factory);
                        }
                        return geometry;
                    })
                    .toList();
            GeometryCollection collection = new GeometryCollection(orientedPolygons.toArray(new Geometry[0]), factory);
            try (StringWriter writer = new StringWriter()) {

                GeometryJSON geometryJson = new GeometryJSON();
                geometryJson.write(collection, writer);

                Map<?, ?> values = objectMapper.readValue(writer.toString(), HashMap.class);

                logger.debug("Created geometry {}", values);
                return values;
            } catch (IOException e) {
                logger.error("Error create geometry", e);
                return null;
            }
        }
        return null;
    }

    public static Map<?, ?> createGeometryFrom(List<List<AbstractEXGeographicExtentType>> rawInput) {
        // The return polygon is in EPSG:4326, so we can call createGeoJson directly
        //TODO: avoid hardcode CRS, get it from document
        List<List<Geometry>> polygons = GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);
        return (polygons != null && !polygons.isEmpty()) ? createGeoJson(polygons) : null;
    }

    /**
     * Checks if the vertices of a polygon are ordered counterclockwise using the Shoelace formula.
     *
     * The Shoelace formula (or Gauss's area formula) calculates the signed area
     * of the polygon. If the result is negative, the vertices are ordered
     * counterclockwise; if positive, they are ordered clockwise.
     *
     * @param coordinates Array of polygon coordinates.
     * @return True if vertices are ordered counterclockwise, False otherwise.
     */
    protected static boolean isCounterClockwise(Coordinate[] coordinates) {
        double sum = 0.0;
        for (int i = 0, n = coordinates.length; i < n - 1; i++) {
            Coordinate current = coordinates[i];
            Coordinate next = coordinates[i + 1];
            sum += (next.x - current.x) * (next.y + current.y);
        }
        return sum < 0.0;
    }

    /**
     * Ensures that a polygon's vertices are ordered counterclockwise.
     *
     * This method checks if the exterior ring (shell) of the polygon is ordered
     * counterclockwise and reorders it if necessary. It also checks all interior
     * rings (holes) and ensures they are ordered clockwise to maintain the right-hand rule.
     *
     * @param polygon Polygon whose vertex order needs to be ensured.
     * @param factory GeometryFactory to create new LinearRing objects if reordering is needed.
     * @return Polygon with correctly ordered vertices.
     */
    protected static Polygon ensureCounterClockwise(Polygon polygon, GeometryFactory factory) {
        // Check and reorder exterior ring if necessary
        LinearRing shell = polygon.getExteriorRing();
        if (!isCounterClockwise(shell.getCoordinates())) {
            shell = factory.createLinearRing(reverseCoordinates(shell.getCoordinates()));
        }

        // Check and reorder each interior ring (hole) if necessary
        LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
        for (int i = 0; i < holes.length; i++) {
            LinearRing hole = polygon.getInteriorRingN(i);
            if (isCounterClockwise(hole.getCoordinates())) {
                hole = factory.createLinearRing(reverseCoordinates(hole.getCoordinates()));
            }
            holes[i] = hole;
        }

        // Return a new polygon with the correctly ordered shell and holes
        return factory.createPolygon(shell, holes);
    }

    /**
     * Reverses the order of coordinates in an array.
     *
     * This method is used to reorder polygon vertices when they are not in
     * the desired counterclockwise or clockwise order.
     *
     * @param coords Array of coordinates to be reversed.
     * @return Array of coordinates in reversed order.
     */
    protected static Coordinate[] reverseCoordinates(Coordinate[] coords) {
        int n = coords.length;
        Coordinate[] reversed = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            reversed[i] = coords[n - 1 - i];
        }
        return reversed;
    }
}
