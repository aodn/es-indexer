package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

public class GeometryUtils {

    public enum PointOrientation {
        CLOCKWISE,
        COUNTER_CLOCKWISE,
        FLAT
    }

    protected static Logger logger = LogManager.getLogger(GeometryUtils.class);
    protected static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
    protected static ObjectMapper objectMapper = new ObjectMapper();
    protected static Geometry landGeometry;
    // Create an ExecutorService with a fixed thread pool size
    @Getter
    @Setter
    protected static ExecutorService executorService;

    @Getter
    @Setter
    protected static boolean gridSpatialExtents;

    @Getter
    @Setter
    protected static int centroidScale = 3;

    @Getter
    @Setter
    protected static double gridSize = 10.0;

    @Getter
    @Setter
    protected static double coastalPrecision = 0.03;

    // Load a coastline shape file so that we can get a spatial extents that cover sea only
    public static void init() {
        try {
            // shp file depends on shx, so need to have shx appear in temp folder.
            saveResourceToTemp("land/ne_10m_land.shx", "shapefile.shx");
            File tempFile = saveResourceToTemp("land/ne_10m_land.shp", "shapefile.shp");

            // Load the shapefile from the temporary file using ShapefileDataStore
            URL tempFileUrl = tempFile.toURI().toURL();
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createDataStore(tempFileUrl);
            SimpleFeatureSource featureSource = dataStore.getFeatureSource();

            // Step 3: Extract the land geometry from the shapefile
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();
            List<Geometry> geometries = new ArrayList<>();

            try (SimpleFeatureIterator iterator = featureCollection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Geometry landFeatureGeometry = (Geometry) feature.getDefaultGeometry();

                    // This will reduce the points of the shape file for faster processing
                    Geometry simplifiedGeometry = DouglasPeuckerSimplifier
                            .simplify(landFeatureGeometry, getCoastalPrecision()); // Adjust tolerance

                    geometries.add(simplifiedGeometry);
                }
            }
            // Faster to use union list rather than union by geometry one by one.
            landGeometry = UnaryUnionOp.union(geometries);
        }
        catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    protected static File saveResourceToTemp(String resourceName, String filename) {
        String tempDir = System.getProperty("java.io.tmpdir");
        ClassPathResource resource = new ClassPathResource(resourceName);

        File tempFile = new File(tempDir, filename);
        try(InputStream input = resource.getInputStream()) {
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
        return tempFile;
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
                        if (geometry instanceof Polygon polygon) {
                            // Ensure all polygons follow the right-hand rule
                            // The right-hand rule is a convention used to maintain consistency in the orientation of vertices for polygons.
                            // For a polygon, adhering to the right-hand rule means that its vertices are ordered counterclockwise for the exterior ring
                            // and clockwise for any interior rings (holes).
                            // Standard: https://www.rfc-editor.org/rfc/rfc7946#section-3.1.6
                            return GeometryUtils.ensureCounterClockwise(polygon, factory);
                        }
                        else if(geometry instanceof Point point) {
                            // Filter out empty point to avoid GeoJson create error
                            return point.isEmpty() ? null : point;
                        }
                        return geometry;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            GeometryCollection collection = new GeometryCollection(orientedPolygons.toArray(new Geometry[0]), factory);
            try (StringWriter writer = new StringWriter()) {
                // This must be hard code and cannot change, the geonetwork comes with some very long decimal coordinate
                // if we do not preserve this, we will result polygon rejected by elastic due to not having 3 non-collinear
                // points after rounding by the GeometryJson
                GeometryJSON geometryJson = new GeometryJSON(15);
                geometryJson.write(collection, writer);

                Map<?, ?> values = objectMapper.readValue(writer.toString(), HashMap.class);

                if(values == null)  {
                    logger.warn("Convert geometry to JSON result in null, {}", writer.toString());
                }
                else {
                    logger.debug("Created geometry {}", values);
                }
                return values;
            } catch (IOException | StringIndexOutOfBoundsException e) {
                logger.error("Error create geometry {} ",collection, e);
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
    protected static PointOrientation orientation(Coordinate[] coordinates) {
        // Computes the signed area for a ring. The signed area is positive if the ring is oriented CW,
        // negative if the ring is oriented CCW, and zero if the ring is degenerate or flat.
        double orientation = Area.ofRingSigned(coordinates);
        if(orientation > 0) {
            return PointOrientation.CLOCKWISE;
        }
        else if(orientation < 0) {
            return PointOrientation.COUNTER_CLOCKWISE;
        }
        else {
            return PointOrientation.FLAT;
        }
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
        if (orientation(shell.getCoordinates()) == PointOrientation.CLOCKWISE) {
            shell = shell.reverse();
        }

        // Check and reorder each interior ring (hole) if necessary
        LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
        for (int i = 0; i < holes.length; i++) {
            LinearRing hole = polygon.getInteriorRingN(i);
            if (orientation(hole.getCoordinates()) == PointOrientation.COUNTER_CLOCKWISE) {
                hole = hole.reverse();
            }
            holes[i] = hole;
        }

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
        CoordinateArrays.reverse(coords);
        return coords;
    }
    /**
     * Create a grid based on the area of the spatial extents. Once we have the grid, we can union the area
     * @param envelope - An envelope that cover the area of the spatial extents
     * @param cellSize - How big each cell will be
     * @return - List of polygon that store the grid
     */
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
    /**
     * Special function to convert a MuliplePolygon to List<Geometry>
     * @param multipolygon - A multipolygon
     * @return - List of polygon the represent the incoming Multiple Polygon
     */
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
    protected static List<Geometry> breakLargeGeometryToGrid(final Geometry large) {
        logger.debug("Break down large geometry to grid {}", large);
        // Get the bounding box (extent) of the large polygon
        Envelope envelope = large.getEnvelopeInternal();

        // Hard code cell size, we can adjust the break grid size. 10.0 result in 3x3 grid
        // cover Australia
        List<Polygon> gridPolygons = createGridPolygons(envelope, getGridSize());

        // List to store Future objects representing the results of the tasks
        List<Future<Geometry>> futureResults = new ArrayList<>();

        // Submit tasks to executor for each gridPolygon
        for (Polygon gridPolygon : gridPolygons) {
            Callable<Geometry> task = () -> {
                Geometry intersection = gridPolygon.intersection(large);
                return !intersection.isEmpty() ? intersection : null;
            };
            Future<Geometry> future = executorService.submit(task);
            futureResults.add(future);
        }

        // List to store the intersected polygons
        final List<Geometry> intersectedPolygons = new ArrayList<>();

        // Collect the results from the futures
        for (Future<Geometry> future : futureResults) {
            try {
                // This blocks until the result is available
                Geometry result = future.get();
                if (result != null) {
                    intersectedPolygons.add(result);
                }
            } catch (InterruptedException | ExecutionException e) {
                // Nothing to report
            }
        }
        return intersectedPolygons;
    }

    protected static List<List<Geometry>> splitAreaToGrid(List<List<Geometry>> geoList) {
        return geoList.stream()
                .flatMap(Collection::stream)
                .map(GeometryUtils::breakLargeGeometryToGrid)
                .toList();
    }
    /**
     * The geometry from geonetwork can be pretty bad that it cover area of sea and land, this function is use
     * to remove the land part
     * @param geoList - The polygon after parsing Goenetwork XML
     * @return - Polygons with land area removed and split into grid aka list of polygon. Geometry is the base class for Polygon
     */
    protected static List<List<Geometry>> removeLandAreaFromGeometry(List<List<Geometry>> geoList) {
        // Do not flatten the array in geometries level, otherwise the map will not display the grid boundary
        return geoList.stream()
                .map(geometries ->
                    geometries.stream()
                            .filter(Objects::nonNull)
                            // Try fixing it with buffer(0), which often fixes small topological errors
                            // it fixed the non-noded intersection issue
                            .map(geometry -> geometry.isValid() ? geometry : geometry.buffer(0))
                            .map(geometry -> geometry.difference(landGeometry))
                            .map(GeometryUtils::convertToListGeometry)
                            .flatMap(Collection::stream)
                            .toList()
                )
                .toList();
    }

    protected static List<Coordinate> calculateGeometryCentroid(Geometry geometry) {
        if(geometry instanceof GeometryCollection gc) {
            return calculateCollectionCentroid(gc);
        }
        else if(geometry instanceof Polygon pl) {
            return List.of(calculatePolygonCentroid(pl).getCoordinate());
        }
        else if(geometry instanceof LineString) {
            return List.of(geometry.getCentroid().getCoordinate());
        }
        else if(geometry instanceof Point p) {
            return List.of(p.getCoordinate());
        }
        else {
            logger.info("Skip geometry centroid for {}", geometry.getGeometryType());
            return null;
        }
    }

    protected static Point calculatePolygonCentroid(Geometry geometry) {
        // Make sure the point will not fall out of the shape, for example a U shape will make
        // centroid fall out of the U, so we check if the centroid is out of the shape? if yes then use
        // interior point
        return geometry.contains(geometry.getCentroid()) ?
                geometry.getCentroid() :
                geometry.getInteriorPoint();
    }

    protected static List<Coordinate> calculateCollectionCentroid(GeometryCollection geometryCollection) {
        // Try simplified the polygon to reduce the number of centroid.
        Geometry geometry = geometryCollection.union();

        try {
            Point centroid = calculatePolygonCentroid(geometry);
            return List.of(new Coordinate(centroid.getX(), centroid.getY()));
        }
        catch(Exception e) {
            // That means it cannot be simplified to a polygon that able to calculate centroid,
            // we need to calculate it one by one
            List<Coordinate> coordinates = new ArrayList<>();
            for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
                geometry = geometryCollection.getGeometryN(i);

                // Make sure the point will not fall out of the shape, for example a U shape will make
                // centroid fall out of the U, so we check if the centroid is out of the shape? if yes then use
                // interior point
                Point centroid = calculatePolygonCentroid(geometry);
                coordinates.add(new Coordinate(centroid.getX(), centroid.getY()));
            }
            return coordinates;
        }
    }
    /**
     * Create a centroid point for the polygon, this will help to speed up the map processing as there is no need
     * to calculate large amount of data.
     * @param geometries - The polygon that describe the spatial extents.
     * @return - The points that represent the centroid or use interior point if centroid is outside of the polygon.
     */
    protected static List<List<BigDecimal>> createCentroid(List<List<Geometry>> geometries) {
        // Flatten the map and extract all polygon, some of the income geometry is GeometryCollection
        return geometries.stream()
                .flatMap(Collection::stream)
                .map(GeometryUtils::calculateGeometryCentroid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(coordinate -> List.of(
                        // We do not need super high precision for centroid, this help to speed up the transfer
                        // on large area
                        BigDecimal.valueOf(coordinate.getX()).setScale(getCentroidScale(), RoundingMode.HALF_UP),
                        BigDecimal.valueOf(coordinate.getY()).setScale(getCentroidScale(), RoundingMode.HALF_UP))
                )
                .toList();
    }
    /**
     * Function to locate the geometry field in source and pass it to the handler for processing
     * @param source - A parsed XML from geonetwork
     * @param handler - The handler to create the item you needed give source
     * @return - The target item
     * @param <R> - Type that align with the handler return type
     */
    public static <R> R createGeometryItems(
            MDMetadataType source,
            Function<List<List<AbstractEXGeographicExtentType>>, R> handler) {

        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            if(items.size() > 1) {
                logger.warn("!! More than 1 block of MDDataIdentificationType, data will be missed !!");
            }
            // Assume only 1 block of <mri:MD_DataIdentification>
            // We only concern geographicElement here
            List<EXExtentType> ext = items.get(0)
                    .getExtent()
                    .stream()
                    .filter(f -> f.getAbstractExtent() != null)
                    .filter(f -> f.getAbstractExtent().getValue() != null)
                    .filter(f -> f.getAbstractExtent().getValue() instanceof EXExtentType)
                    .map(f -> (EXExtentType) f.getAbstractExtent().getValue())
                    .filter(f -> f.getGeographicElement() != null)
                    .toList();

            // We want to get a list of item where each item contains multiple, (aka list) of
            // (EXGeographicBoundingBoxType or EXBoundingPolygonType)
            List<List<AbstractEXGeographicExtentType>> rawInput = ext.stream()
                    .map(EXExtentType::getGeographicElement)
                    .map(l ->
                            /*
                                l = List<AbstractEXGeographicExtentPropertyType>
                                For each AbstractEXGeographicExtentPropertyType, we get the tag that store the
                                coordinate, it is either a EXBoundingPolygonType or EXGeographicBoundingBoxType
                             */
                            l.stream()
                                    .map(AbstractEXGeographicExtentPropertyType::getAbstractEXGeographicExtent)
                                    .filter(Objects::nonNull)
                                    .filter(m -> (m.getValue() instanceof EXBoundingPolygonType || m.getValue() instanceof EXGeographicBoundingBoxType))
                                    .map(m -> {
                                        if (m.getValue() instanceof EXBoundingPolygonType exBoundingPolygonType) {
                                            if (!exBoundingPolygonType.getPolygon().isEmpty() && exBoundingPolygonType.getPolygon().get(0).getAbstractGeometry() != null) {
                                                return exBoundingPolygonType;
                                            }
                                        } else if (m.getValue() instanceof EXGeographicBoundingBoxType) {
                                            return m.getValue();
                                        }
                                        return null; // Handle other cases or return appropriate default value
                                    })
                                    .filter(Objects::nonNull) // Filter out null values if any
                                    .toList()
                    )
                    .toList();
            return handler.apply(rawInput);
        }
        return null;
    }

    protected static List<List<Geometry>> createGeometryWithoutLand(List<List<AbstractEXGeographicExtentType>> rawInput) {
        return removeLandAreaFromGeometry(
                GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput)
        );
    }
    /**
     * A preprocessed centroid point based on spatial extents area. In order not to have a point that fall into the
     * land, we remove the area contain land area. Then we spit it into grid, in each grid we calculate the centroid.
     * Multiple point will be created for the map to process, this is needed because when map drag, some spatial extents
     * that across multiple region should have a point that represent it.
     * @param rawInput - The parsed XML block that contains the spatial extents area
     * @return - Centroid point which will not appear on land.
     */
    public static List<List<BigDecimal>> createCentroidFrom(List<List<AbstractEXGeographicExtentType>> rawInput) {
        List<List<Geometry>> grid = splitAreaToGrid(createGeometryWithoutLand(rawInput));
        return (grid != null && !grid.isEmpty()) ? createCentroid(grid) : null;
    }
    /**
     * Create the spatial extents area given the XML info, it will not remove land area for speed reason. Otherwise,
     * too many polygon created plus this is not what the metadata provides. Also, it may create non-collinear
     * or self-intersecting polygon.
     * @param rawInput - The parsed XML block that contains the spatial extents area
     * @return
     */
    public static Map<?, ?> createGeometryFrom(List<List<AbstractEXGeographicExtentType>> rawInput) {
        // The return polygon is in EPSG:4326, so we can call createGeoJson directly

        // This line will cause the spatial extents to break into grid, it may help to debug but will make production
        // slow and sometimes cause polygon break.
        // List<List<Geometry>> polygonNoLand = splitAreaToGrid(createGeometryWithoutLand(rawInput));
        List<List<Geometry>> polygon = GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);

        // This is helpful when debug, it help to visualize the grid size on say edge env.
        polygon = isGridSpatialExtents() ? splitAreaToGrid(polygon) : polygon;

        return (polygon != null && !polygon.isEmpty()) ? createGeoJson(polygon) : null;
    }
}
