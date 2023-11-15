package au.org.aodn.esindexer.utils;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryBase {

    protected static Logger logger = LoggerFactory.getLogger(GeometryBase.class);

    protected static Map<String, Integer> codeToSID = new HashMap<>();

    public static final String COORDINATE_SYSTEM_CRS84 = "CRS:84";
    public static final String COORDINATE_SYSTEM_WGS84 = "WGS:84";  // Same as EPSG:4326

    // Must hardcode 4326 as all GeoJson use WGS84 which is EPSG 4326
    protected static GeometryFactory geoJsonFactory = new GeometryFactory(new PrecisionModel(), 4326);
    /**
     *
     * @param boundingBox
     * @param polygons - The polygon that fit into the bounding box.
     * @return
     * @throws FactoryException
     * @throws TransformException
     */
    public static Envelope calculateBoundingBox(Envelope boundingBox, Polygon... polygons) throws FactoryException, TransformException {

        // Iterate through the polygons
        for (Polygon polygon : polygons) {
            // Transform the polygon to CRS:84
            // Update the bounding box with the current polygon's envelope
            boundingBox.expandToInclude(polygon.getEnvelopeInternal());
        }

        return boundingBox;
    }

    public static List<Polygon> findPolygonsFromEXBoundingPolygonType(String rawCRS, List<Object> rawInput) {
        List<Polygon> polygons = new ArrayList<>();

        if(COORDINATE_SYSTEM_CRS84.equals(rawCRS)) {
            List<List<GMObjectPropertyType>> input = rawInput.stream()
                    .filter(f -> f instanceof EXBoundingPolygonType)
                    .map(m -> (EXBoundingPolygonType) m)
                    .map(m -> m.getPolygon())
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
                                    if (polygonType.getExterior() != null && polygonType.getExterior().getAbstractRing().getValue() instanceof LinearRingType linearRingType) {
                                        // TODO: Handle 2D now, can be 3D
                                        if (linearRingType.getPosList().getSrsDimension().doubleValue() == 2.0) {
                                            List<Double> v = linearRingType.getPosList().getValue();
                                            List<Coordinate> items = new ArrayList<>();

                                            for (int z = 0; z < v.size(); z += 2) {
                                                items.add(new Coordinate(v.get(z), v.get(z + 1)));
                                            }

                                            // We need to store it so that we can create the multi-array as told by spec
                                            Polygon polygon = geoJsonFactory.createPolygon(items.toArray(new Coordinate[items.size()]));
                                            polygons.add(polygon);
                                            logger.debug("2D Polygon added {}", polygon);
                                        }
                                    }
                                }
                            }
                        } else if (type instanceof PolygonType plt) {
                            // TODO: Only process LinearRingType for now
                            // Set the coor system for the factory
                            // CoordinateReferenceSystem system = CRS.decode(mst.getSrsName().trim(), true);
                            if (plt.getExterior() != null && plt.getExterior().getAbstractRing().getValue() instanceof LinearRingType linearRingType) {
                                // TODO: Handle 2D now, can be 3D
                                if (linearRingType.getPosList().getSrsDimension().doubleValue() == 2.0) {
                                    List<Double> v = linearRingType.getPosList().getValue();
                                    List<Coordinate> items = new ArrayList<>();

                                    for (int z = 0; z < v.size(); z += 2) {
                                        items.add(new Coordinate(v.get(z), v.get(z + 1)));
                                    }

                                    // We need to store it so that we can create the multi-array as told by spec
                                    Polygon polygon = geoJsonFactory.createPolygon(items.toArray(new Coordinate[items.size()]));
                                    polygons.add(polygon);

                                    logger.debug("2D Polygon added {}", polygon);
                                }
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

    public static List<Polygon> findPolygonsFromEXGeographicBoundingBoxType(String rawCRS, List<Object> rawInput) {
        List<Polygon> polygons = new ArrayList<>();

        if(COORDINATE_SYSTEM_CRS84.equals(rawCRS)) {
            List<EXGeographicBoundingBoxType> input = rawInput.stream()
                    .filter(f -> f instanceof EXGeographicBoundingBoxType)
                    .map(m -> (EXGeographicBoundingBoxType) m)
                    .toList();

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

                Polygon polygon = geoJsonFactory.createPolygon(coordinates);
                polygons.add(polygon);
            }
        }

        if(!polygons.isEmpty()) {
            return polygons;
        }
        else {
            logger.warn("No applicable BBOX calculation found for findPolygonsFromEXGeographicBoundingBoxType using CRS {}", rawCRS);
            return null;
        }
    }
}
