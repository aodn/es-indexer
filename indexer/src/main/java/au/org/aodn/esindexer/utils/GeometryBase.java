package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

public class GeometryBase {

    public static final String COORDINATE_SYSTEM_CRS84 = "CRS:84";
    protected static Logger logger = LogManager.getLogger(GeometryBase.class);
    protected static final CoordinateReferenceSystem CRS84;  // WGS84 (CRS84)

    static {
        try {
            CRS84 = CRS.decode(COORDINATE_SYSTEM_CRS84);  // WGS84 (CRS84)
        }
        catch (FactoryException e) {
            throw new RuntimeException(e);
        }
    }

    // Must hardcode 4326 as all GeoJson use WGS84 which is EPSG 4326
    protected static GeometryFactory geoJsonFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Ths function is use to extract a list of list of polygon from the XML, the coordinate can be store in box type of geometry type and hence
     * we need to use a if state to correctly locate the coordinate based on type.
     * The EXBoundingPolygonType should return a box while the EXGeographicBoundingBoxType will be a polygon, in either case
     * this can fit into a polygon
     *
     * @param rawCRS - The coordination reference.
     * @param rawInput - A list of AbstractEXGeographicExtentType, AbstractEXGeographicExtentType is a base type, and can be a bbox or geometry
     * @return - Geometry of the polygon
     */
    public static List<List<Geometry>> findPolygonsFrom(final String rawCRS, List<List<AbstractEXGeographicExtentType>> rawInput) {
        return rawInput
                .stream()
                .map(r -> {
                    if(!r.isEmpty() && r.get(0) instanceof EXBoundingPolygonType) {
                        return findPolygonsFromEXBoundingPolygonType(rawCRS, r);
                    }
                    else if(!r.isEmpty() && r.get(0) instanceof EXGeographicBoundingBoxType) {
                        return findPolygonsFromEXGeographicBoundingBoxType(rawCRS, r);
                    }
                    // Some type not support so return null
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(i -> !i.isEmpty())
                .toList();
    }

    protected static List<Geometry> findPolygonsFromEXBoundingPolygonType(String rawCRS, List<AbstractEXGeographicExtentType> rawInput) {
        final List<Geometry> polygons = new ArrayList<>();

        if(COORDINATE_SYSTEM_CRS84.equals(rawCRS)) {
            List<List<GMObjectPropertyType>> input = rawInput
                    .stream()
                    .filter(f -> f instanceof EXBoundingPolygonType)
                    .map(m -> (EXBoundingPolygonType) m)
                    .map(EXBoundingPolygonType::getPolygon)
                    .toList();

            for (List<GMObjectPropertyType> i : input) {
                for (GMObjectPropertyType t : i) {

                    if (t.getAbstractGeometry() != null) {
                        AbstractGeometryType type = t.getAbstractGeometry().getValue();

                        // TODO: fix here more cases
                        if (type instanceof MultiSurfaceType mst) {
                            for (SurfacePropertyType j : mst.getSurfaceMember()) {
                                // TODO: Only process Polygon for now.
                                if (j.getAbstractSurface().getValue() instanceof PolygonType polygonType) {
                                    // TODO: Only process LinearRingType for now
                                    safeGet(() -> polygonType.getExterior().getAbstractRing().getValue())
                                            .filter(value -> value instanceof LinearRingType)
                                            .map(value -> (LinearRingType) value)
                                            .flatMap(linearRingType ->
                                                    safeGet(linearRingType::getPosList)).ifPresent(pos -> {
                                                        Polygon polygon = linerPositionToPolygon(pos, polygonType.getSrsName());
                                                        if(polygon != null) {
                                                            logger.debug("MultiSurfaceType 2D added (findPolygonsFromEXBoundingPolygonType) {}", polygon);
                                                            polygons.add(polygon);
                                                        }
                                                    });
                                }
                            }
                        } else if (type instanceof PolygonType plt) {
                            // TODO: Only process LinearRingType for now
                            // Set the coor system for the factory
                            // CoordinateReferenceSystem system = CRS.decode(mst.getSrsName().trim(), true);

                            // Process exterior ring
                            if (plt.getExterior() != null && plt.getExterior().getAbstractRing().getValue() instanceof LinearRingType exLinearRingType) {
                                Optional<LinearRing> exteriorRing = safeGet(exLinearRingType::getPosList)
                                        .map(pos -> {
                                            // We need to store it so that we can create the multi-array as told by spec
                                            LinearRing r = linerPositionToLinearRing(pos, plt.getSrsName());
                                            logger.debug("LinearRingType added {}", r);
                                            return r;
                                        });

                                // Process interior rings (holes)
                                exteriorRing.ifPresent(ring -> {
                                    List<LinearRing> interiorRings = new ArrayList<>();
                                    if (plt.getInterior() != null) {
                                        for (AbstractRingPropertyType interior : plt.getInterior()) {
                                            if (interior.getAbstractRing().getValue() instanceof LinearRingType inLinearRingType) {
                                                LinearRing interiorRing = linerPositionToLinearRing(inLinearRingType.getPosList(), plt.getSrsName());
                                                // In some case, the interior ring value is set incorrectly where it is outside the
                                                // exterior ring, this will not work with geojson in Elastic search because of the right hand
                                                // rule that exterior ring need to be anti-clockwise and interior ring needs to be clockwise
                                                // For those interior ring outside exterior ring, it should be another polygon
                                                if(interiorRing != null && interiorRing.within(ring)) {
                                                    interiorRings.add(interiorRing);
                                                }
                                            }
                                        }
                                    }
                                    polygons.add(geoJsonFactory.createPolygon(ring, interiorRings.toArray(new LinearRing[0])));
                                });
                            }
                        }
                    }
                }
            }
        }
        if(polygons.isEmpty()) {
            logger.warn("No applicable BBOX calculation found for findPolygonsFromEXBoundingPolygonType using CRS {}", rawCRS);
        }
        return polygons;
    }
    /**
     * This function look into the EXGeographicBoundingBox amd extract the coordinate, this block will be like this
     * <gex:EX_GeographicBoundingBox>
     *   <gex:westBoundLongitude>
     *     <gco:Decimal>146.862133</gco:Decimal>
     *   </gex:westBoundLongitude>
     *   <gex:eastBoundLongitude>
     *     <gco:Decimal>146.862133</gco:Decimal>
     *   </gex:eastBoundLongitude>
     *   <gex:southBoundLatitude>
     *     <gco:Decimal>-19.10415</gco:Decimal>
     *   </gex:southBoundLatitude>
     *   <gex:northBoundLatitude>
     *     <gco:Decimal>-19.10415</gco:Decimal>
     *   </gex:northBoundLatitude>
     * </gex:EX_GeographicBoundingBox>
     * with North, East, South, West only, but people may not necessary create a box are but can set coordinate to
     * Points or Line, so our return type needs to be Geometry
     *
     * @param rawCRS - The coordinate reference
     * @param rawInput - The raw parsed XML input of the section of AbstractEXGeographicExtentType
     * @return - List of Geometry, where it can be Point, Line or Box aka (Polygon)
     */
    protected static List<Geometry> findPolygonsFromEXGeographicBoundingBoxType(String rawCRS, List<AbstractEXGeographicExtentType> rawInput) {
        final List<Geometry> geometries = new ArrayList<>();

        if(COORDINATE_SYSTEM_CRS84.equals(rawCRS)) {
            List<EXGeographicBoundingBoxType> input = rawInput.stream()
                    .filter(f -> f instanceof EXGeographicBoundingBoxType)
                    .map(m -> (EXGeographicBoundingBoxType) m)
                    .toList();

            // Noted that some user do not create a box in this section but a Point!! This isn't correct but
            // the geonetwork allow this, so we need to deal with it.
            for (EXGeographicBoundingBoxType bbt : input) {
                if (bbt.getWestBoundLongitude().getDecimal() == null || bbt.getEastBoundLongitude().getDecimal() == null || bbt.getNorthBoundLatitude().getDecimal() == null || bbt.getSouthBoundLatitude().getDecimal() == null) {
                    logger.warn("Invalid BBOX found for findPolygonsFromEXGeographicBoundingBoxType using CRS {}", rawCRS);
                } else {
                    logger.debug("BBOX found for findPolygonsFromEXGeographicBoundingBoxType using CRS {}", rawCRS);
                    getCoordinates(bbt).ifPresent(geometries::add);
                }
            }
        }

        if(!geometries.isEmpty()) {
            return geometries;
        }
        else {
            logger.warn("No applicable BBOX calculation found for findPolygonsFromEXGeographicBoundingBoxType using CRS {}", rawCRS);
            return null;
        }
    }

    protected static Optional<Geometry> getCoordinates(EXGeographicBoundingBoxType bbt) {

        double east = bbt.getEastBoundLongitude().getDecimal().doubleValue();
        double west = bbt.getWestBoundLongitude().getDecimal().doubleValue();
        double north = bbt.getNorthBoundLatitude().getDecimal().doubleValue();
        double south = bbt.getSouthBoundLatitude().getDecimal().doubleValue();

        // Sometime value input is incorrect, fix it here, a value bigger than 180 for coordinate imply max value 180
        east = east >= 180 ? 180 : east;

        // Define the coordinates for the bounding box
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(west, south),
                new Coordinate(east, south),
                new Coordinate(east, north),
                new Coordinate(west, north)
        };

        if(verifyPoint(coordinates)) {
            // This is a point as all 4 point same
             return Optional.of(geoJsonFactory.createPoint(coordinates[0]));
        }
        else {
            coordinates = ArrayUtils.add(coordinates, new Coordinate(west, south));  // Closing the ring
            if(verifyPolygon(coordinates)) {
                return Optional.of(geoJsonFactory.createPolygon(coordinates));
            }
            else {
                logger.warn("Unknown shape, not point or polygon {}", (Object) coordinates);
                return Optional.empty();
            }
        }
    }

    protected static boolean verifyPoint(Coordinate[] coordinates) {

        // Groups the coordinates by their values and counts the occurrences of each coordinate.
        Map<Coordinate, Long> coordinateCountMap = Arrays.stream(coordinates)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Filters out the coordinates that occur more than once.
        long total = coordinateCountMap.values().stream()
                .filter(count -> count > 1)
                .reduce(0L, Long::sum);

        if(total != coordinates.length) {
            // Some points not the same
            return false;
        }
        Point point = geoJsonFactory.createPoint(coordinates[0]);

        // A point's dimension should be 0
        return point.getDimension() == 0;
    }

    protected static boolean verifyPolygon(Coordinate[] coordinates) {
        if (coordinates.length < 4) {
            return false; // At least three distinct points plus a closing point are needed to form a polygon
        }

        // The first and last coordinates must be the same to close the polygon
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            return false;
        }

        Polygon polygon = geoJsonFactory.createPolygon(coordinates);

        // A polygon's dimension should be 2
        return polygon.getDimension() == 2;
    }
    /**
     * Convert the linearPosition attribute to list of coordination for further processing, use internally
     * @param pos - The object that holds the section like this
     *            <gml:posList srsDimension="2">
     *                -60.51553344691095 -45.68359375005144 -60.60853564740216 -46.035816073053326 -60.68042373666604 -45.13080215479556 -60.54016101332009 -45.45302212217308 -60.51553344691095 -45.68359375005144
     *            </gml:posList>
     * @return - The list of coordinate, for example, [-60.51553344691095 -45.68359375005144], [-45.68359375005144 -60.60853564740216], [-60.60853564740216 -46.035816073053326]...
     */
    protected static List<Coordinate> linearPositionToCoordinates(DirectPositionListType pos, String proj) {
        List<Coordinate> items = new ArrayList<>();
        // Assume 2D if not present
        Double dimension = safeGet(() -> pos.getSrsDimension().doubleValue()).orElse(2.0);
        // TODO: Handle 2D now, can be 3D
        if (dimension == 2.0) {
            List<Double> v = pos.getValue();

            String projection = proj != null ? proj : pos.getSrsName();
            MathTransform transform = null;
            if(projection != null && !projection.isBlank()) {
                try {
                    transform = CRS.findMathTransform(CRS.decode(projection), CRS84);
                }
                catch (FactoryException e) {
                    // Default is WGS84 (CRS84), so do nothing need to transform
                }
            }

            // Create the MathTransform object for transforming between source and target CRS
            for (int z = 0; z < v.size(); z += 2) {
                Coordinate coordinate = new Coordinate(v.get(z), v.get(z + 1));
                if(transform != null) {
                    try {
                        // try transform to the correct coordinate
                        items.add(JTS.transform(coordinate, null, transform));
                    }
                    catch (TransformException e) {
                        items.add(coordinate);
                    }
                }
                else {
                    items.add(coordinate);
                }
            }
        }
        return items;
    }

    protected static Polygon linerPositionToPolygon(DirectPositionListType pos, String proj) {
        List<Coordinate> items = linearPositionToCoordinates(pos, proj);
        try {
            // We need to store it so that we can create the multi-array as told by spec
            return geoJsonFactory.createPolygon(items.toArray(new Coordinate[0]));
        }
        catch(IllegalArgumentException iae) {
            logger.warn("Invalid Polygon", iae);
        }
        return null;
    }

    protected static LinearRing linerPositionToLinearRing(DirectPositionListType pos, String proj) {
        List<Coordinate> items = linearPositionToCoordinates(pos, proj);
        try {
            // We need to store it so that we can create the multi-array as told by spec
            return geoJsonFactory.createLinearRing(items.toArray(new Coordinate[0]));
        }
        catch(IllegalArgumentException iae) {
            logger.warn("Invalid LinearRingType", iae);
        }
        return null;
    }
}
