package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.AbstractEXGeographicExtentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.net.URL;
import java.util.*;

public class GeometryUtils {

    protected static Logger logger = LogManager.getLogger(GeometryUtils.class);
    protected static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
    protected static ObjectMapper objectMapper = new ObjectMapper();
    protected static Geometry landGeometry;

    // Load a coastline shape file so that we can get a spatial extents that cover sea only
    static {
        String tempDir = System.getProperty("java.io.tmpdir");
        ClassPathResource resource1 = new ClassPathResource("land/ne_10m_land.shp");
        ClassPathResource resource2 = new ClassPathResource("land/ne_10m_land.shx");

        try(InputStream input = resource2.getInputStream()) {
            File tempFile = new File(tempDir, "shapefile.shx");
            tempFile.deleteOnExit();  // Ensure the file is deleted when the JVM exits

            // Write the InputStream to the temporary file
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try(InputStream input = resource1.getInputStream()) {
            // Create a temporary file to store the shapefile, because ShapefileDataStoreFactory do not support
            // input stream.
            File tempFile = new File(tempDir, "shapefile.shp");
            tempFile.deleteOnExit();  // Ensure the file is deleted when the JVM exits

            // Write the InputStream to the temporary file
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Load the shapefile from the temporary file using ShapefileDataStore
            URL tempFileUrl = tempFile.toURI().toURL();
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createDataStore(tempFileUrl);
            SimpleFeatureSource featureSource = dataStore.getFeatureSource();

            // Step 3: Extract the land geometry from the shapefile
            Geometry land = null;
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();
            try (SimpleFeatureIterator iterator = featureCollection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Geometry landFeatureGeometry = (Geometry) feature.getDefaultGeometry();

                    // Union the land geometries (since land shapefile may contain multiple features)
                    if (land == null) {
                        land = landFeatureGeometry;
                    } else {
                        land = land.union(landFeatureGeometry);
                    }
                }
            }
            landGeometry = land;
        }
        catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
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
    /**
     * Checks if the vertices of a polygon are ordered counterclockwise using the Shoelace formula.
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

    // Method to create grid polygons (grid cells) over the bounding box (envelope)
    protected static List<Polygon> createGridPolygons(Envelope envelope, double cellSize) {
        List<Polygon> gridPolygons = new ArrayList<>();
        GeometryFactory geometryFactory = new GeometryFactory();

        double minX = envelope.getMinX();
        double minY = envelope.getMinY();
        double maxX = envelope.getMaxX();
        double maxY = envelope.getMaxY();

        // Loop to create grid cells
        for (double x = minX; x < maxX; x += cellSize) {
            for (double y = minY; y < maxY; y += cellSize) {
                // Create a polygon for each grid cell
                Polygon gridCell = geometryFactory.createPolygon(new Coordinate[]{
                        new Coordinate(x, y),
                        new Coordinate(x + cellSize, y),
                        new Coordinate(x + cellSize, y + cellSize),
                        new Coordinate(x, y + cellSize),
                        new Coordinate(x, y)  // Closing the polygon
                });
                gridPolygons.add(gridCell);
            }
        }

        return gridPolygons;
    }

    protected static List<Geometry> convertToListGeometry(Geometry multipolygon) {
        // The geometry is expected to be multipolygon
        // The above map will result in a multi-polygon, we need to flatten
        // it to List<Geometry> which is what the later processing expected.
        List<Geometry> geo = new ArrayList<>();

        // Iterate over the geometries in the MultiPolygon
        for (int i = 0; i < multipolygon.getNumGeometries(); i++) {
            Geometry geometry = multipolygon.getGeometryN(i);
            // Ensure that the geometry is a Polygon before adding to the list
            geo.add(geometry);
        }
        return geo;

    }
    /**
     * Some geometry polygon cover the whole australia which is very big, it would be easier to process by UI
     * if we break it down in to grid of polygon. The grid size is hardcode here to 100.0, you can adjust it
     * but need to re-compile the code.
     * @param large - A Polygon to break into grid
     * @return - A polygon the break into grid.
     */
    protected static List<Geometry> breakLargeGeometryToGrid(Geometry large) {
        // Get the bounding box (extent) of the large polygon
        Envelope envelope = large.getEnvelopeInternal();

        // Hard code cell size, we can adjust the break grid size by alter this value 100.0
        List<Polygon> gridPolygons = createGridPolygons(envelope, 100.0);

        List<Geometry> intersectedPolygons = new ArrayList<>();
        for (Polygon gridPolygon : gridPolygons) {
            Geometry intersection = gridPolygon.intersection(large);
            if (!intersection.isEmpty()) {
                intersectedPolygons.add(intersection);
            }
        }

        return intersectedPolygons;
    }

    protected static List<List<Geometry>> removeLandAreaFromGeometry(List<List<Geometry>> geoList) {
        return geoList.stream()
                .map(geometries ->
                    geometries.stream()
                            .map(geometry -> geometry.difference(landGeometry))
                            .map(GeometryUtils::convertToListGeometry)
                            .flatMap(Collection::stream)
                            .map(GeometryUtils::breakLargeGeometryToGrid)
                            .flatMap(Collection::stream)
                            .toList()
                )
                .toList();
    }

    public static Map<?, ?> createGeometryFrom(List<List<AbstractEXGeographicExtentType>> rawInput) {
        // The return polygon is in EPSG:4326, so we can call createGeoJson directly
        //TODO: avoid hardcode CRS, get it from document
        List<List<Geometry>> polygons = removeLandAreaFromGeometry(
                GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput)
        );
        return (polygons != null && !polygons.isEmpty()) ? createGeoJson(polygons) : null;
    }
}
