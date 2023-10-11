package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.exception.MappingValueException;
import au.org.aodn.metadata.iso19115_3_2018.*;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/*
According to spec https://github.com/radiantearth/stac-spec/blob/master/collection-spec/collection-spec.md

The first bounding box always describes the overall spatial extent of the data.
All subsequent bounding boxes can be used to provide a more precise description of the extent and
identify clusters of data. Clients only interested in the overall spatial extent will only need to
access the first item in each array. It is recommended to only use multiple bounding boxes if a union
of them would then include a large uncovered area (e.g. the union of Germany and Chile).

The length of the inner array must be 2*n where n is the number of dimensions. The array contains all
axes of the  southwesterly most extent followed by all axes of the northeasterly most extent specified
in Longitude/Latitude or Longitude/Latitude/Elevation based on WGS 84. When using 3D geometries,
the elevation of the southwesterly most extent is the minimum depth/height in meters and the elevation
of the northeasterly most extent is the maximum.

The coordinate reference system of the values is WGS 84 longitude/latitude. Example that covers the
whole Earth:  [[-180.0, -90.0, 180.0, 90.0]]. Example that covers the whole earth with a depth of 100
meters to a height of 150 meters: [[-180.0, -90.0, -100.0, 180.0, 90.0, 150.0]].

This class is tailor for the above operation
 */
public class BBoxUtils {

    // TODO: this class seriously need some refactoring at the end

    protected static Logger logger = LoggerFactory.getLogger(BBoxUtils.class);

    public static Envelope calculateBoundingBox(Envelope boundingBox, CoordinateReferenceSystem sourceCRS, Polygon... polygons) throws FactoryException, TransformException {
        // Define the CRS:84 coordinate reference system as state in STAC, EPSG:4326 = CRS:84
        CoordinateReferenceSystem crs84 = CRS.decode("CRS:84", true);

        // Create an empty bounding box
        MathTransform transform = CRS.findMathTransform(sourceCRS, crs84);

        // Iterate through the polygons
        for (Polygon polygon : polygons) {
            // Transform the polygon to CRS:84
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
            Geometry transformedPolygon = JTS.transform(polygon, transform);

            // Update the bounding box with the current polygon's envelope
            boundingBox.expandToInclude(transformedPolygon.getEnvelopeInternal());
        }

        return boundingBox;
    }

    public static List<List<Double>> createBBoxFromEXBoundingPolygonType(List<Object> rawInput) throws FactoryException, TransformException {

        List<EXBoundingPolygonType>  input = rawInput.stream()
                .filter(f -> f instanceof EXBoundingPolygonType)
                .map(m -> (EXBoundingPolygonType)m)
                .toList();

        // This is used to store the envelope of all polygon
        Envelope envelope = new Envelope();
        List<Polygon> polygons = new ArrayList<>();

        // Create polygon base on coordinate system
        for(EXBoundingPolygonType pt : input) {
            for (GMObjectPropertyType i : pt.getPolygon()) {
                AbstractGeometryType type = i.getAbstractGeometry().getValue();
                /// TODO: fix here more cases
                if (type instanceof MultiSurfaceType mst) {

                    // Set the coor system for the factory
                    // CoordinateReferenceSystem system = CRS.decode(mst.getSrsName().trim(), true);
                    // TODO: should come from the doc rather than hard coded CRS84
                    CoordinateReferenceSystem system = CRS.decode("CRS:84", true);
                    // TODO: should come from the doc rather than hard coded 4326
                    GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

                    for (SurfacePropertyType j : mst.getSurfaceMember()) {
                        // TODO: Only process Polygon for now.
                        if (j.getAbstractSurface().getValue() instanceof PolygonType polygonType) {
                            // TODO: Only process LinearRingType for now
                            if (polygonType.getExterior() != null && polygonType.getExterior().getAbstractRing().getValue() instanceof LinearRingType linearRingType) {
                                // TODO: Handle 2D now, can be 3D
                                if (linearRingType.getPosList().getSrsDimension().doubleValue() == 2.0) {
                                    List<Double> v = linearRingType.getPosList().getValue();
                                    List<Coordinate> items = new ArrayList<>();

                                    for (int z = 0; z < v.size(); z += 2) {
                                        items.add(new Coordinate(v.get(z), v.get(z + 1)));
                                    }

                                    // We need to store it so that we can create the multi-array as told by spec
                                    Polygon polygon = factory.createPolygon(items.toArray(new Coordinate[items.size()]));
                                    polygons.add(polygon);

                                    logger.debug("2D Polygon added {}", polygon);
                                    envelope = calculateBoundingBox(envelope, system, polygon);
                                }
                            }
                        }
                    }
                } else if (type instanceof PolygonType plt) {
                    // TODO: Only process LinearRingType for now
                    // Set the coor system for the factory
                    // CoordinateReferenceSystem system = CRS.decode(mst.getSrsName().trim(), true);
                    // TODO: should come from the doc rather than hard coded CRS84
                    CoordinateReferenceSystem system = CRS.decode("CRS:84", true);
                    // TODO: should come from the doc rather than hard coded 4326
                    GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);


                    if (plt.getExterior() != null && plt.getExterior().getAbstractRing().getValue() instanceof LinearRingType linearRingType) {
                        // TODO: Handle 2D now, can be 3D
                        if (linearRingType.getPosList().getSrsDimension().doubleValue() == 2.0) {
                            List<Double> v = linearRingType.getPosList().getValue();
                            List<Coordinate> items = new ArrayList<>();

                            for (int z = 0; z < v.size(); z += 2) {
                                items.add(new Coordinate(v.get(z), v.get(z + 1)));
                            }

                            // We need to store it so that we can create the multi-array as told by spec
                            Polygon polygon = factory.createPolygon(items.toArray(new Coordinate[items.size()]));
                            polygons.add(polygon);

                            logger.debug("2D Polygon added {}", polygon);
                            envelope = calculateBoundingBox(envelope, system, polygon);
                        }
                    }
                }
            }
        }

        if(!polygons.isEmpty()) {
            List<List<Double>> result = new ArrayList<>();
            result.add(List.of(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()));

            for(Polygon p : polygons) {
                List<Double> points = new ArrayList<>();
                for (Coordinate c : p.getCoordinates()) {
                    points.addAll(List.of(
                        c.getX(), c.getY()
                    ));
                }
                result.add(points);
            }
            return result;
        } else {
            throw new MappingValueException("No applicable BBOX calculation found");
        }
    }

    public static List<List<Double>> createBBoxFromEXGeographicBoundingBoxType(List<Object> rawInput) throws FactoryException, TransformException {

        List<EXGeographicBoundingBoxType>  input = rawInput.stream()
                .filter(f -> f instanceof EXGeographicBoundingBoxType)
                .map(m -> (EXGeographicBoundingBoxType)m)
                .toList();

        CoordinateReferenceSystem system = CRS.decode("CRS:84", true);
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

        Envelope envelope = new Envelope();
        List<Polygon> polygons = new ArrayList<>();

        for (EXGeographicBoundingBoxType bbt : input) {
            Double east = bbt.getEastBoundLongitude().getDecimal().doubleValue();
            Double west = bbt.getWestBoundLongitude().getDecimal().doubleValue();
            Double north = bbt.getNorthBoundLatitude().getDecimal().doubleValue();
            Double south = bbt.getSouthBoundLatitude().getDecimal().doubleValue();

            // Define the coordinates for the bounding box
            Coordinate[] coordinates = new Coordinate[]{
                    new Coordinate(west, south),
                    new Coordinate(east, south),
                    new Coordinate(east, north),
                    new Coordinate(west, north),
                    new Coordinate(west, south)  // Closing the ring
            };

            Polygon polygon = factory.createPolygon(coordinates);
            polygons.add(polygon);

            envelope = calculateBoundingBox(envelope, system, polygon);
        }

        if(!polygons.isEmpty()) {
            // If it didn't contain polygon, then the envelope is just the initial object and thus invalid.
            List<List<Double>> result = new ArrayList<>();
            result.add(List.of(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()));

            for(Polygon p : polygons) {
                result.add(List.of(
                        p.getEnvelopeInternal().getMinX(),
                        p.getEnvelopeInternal().getMinY(),
                        p.getEnvelopeInternal().getMaxX(),
                        p.getEnvelopeInternal().getMaxY()));
            }
            return result;
        } else {
            throw new MappingValueException("No applicable BBOX calculation found");
        }
    }
}
