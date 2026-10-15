package au.org.aodn.esindexer.utils;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
public class StacUtils {

    protected static Logger logger = LogManager.getLogger(StacUtils.class);

    @Setter
    @Getter
    protected static int scale = 10;

    /**
     * Create list of bbox, where the first one is the overall bbox
     * @param listOfPolygons - Assume to be EPSG:4326 as this is what GeoJson use
     * @return List of STAC bbox
     */
    public static List<List<BigDecimal>> createStacBBox(List<List<Geometry>> listOfPolygons) {
        List<List<BigDecimal>> result = new ArrayList<>();

        if(listOfPolygons != null) {
            final Envelope overallBoundingBox = new Envelope();
            final AtomicBoolean hasBoundingBoxUpdate = new AtomicBoolean(false);
            // A lone EX_GeographicBoundingBox of -180..180 is the whole world. The same envelope
            // on a dateline-crossing polygon, or mixed with smaller cells, must keep the 0..360 shift.
            final AtomicBoolean worldLongitudeBox = new AtomicBoolean(false);
            final AtomicBoolean partialLongitude = new AtomicBoolean(false);
            listOfPolygons
                    .forEach(polygons -> {
                        for (Geometry polygon : polygons) {
                            // Add polygon one by one to expand the overall bounding box area, this is requirement
                            // of STAC to have an overall bounding box of all smaller area as the first bbox in the list.
                            if (polygon != null && polygon.getEnvelopeInternal() != null) {
                                Envelope env = polygon.getEnvelopeInternal();
                                double minX;
                                double maxX;
                                if (isFullLongitudeRectangle(polygon, env)) {
                                    worldLongitudeBox.set(true);
                                    // Keep the collapsed meridian so this box does not widen a union of smaller cells.
                                    minX = 180.0;
                                    maxX = 180.0;
                                }
                                else {
                                    partialLongitude.set(true);
                                    // Normalize longitudes to [0, 360]
                                    minX = env.getMinX() < 0 ? env.getMinX() + 360.0 : env.getMinX();
                                    maxX = env.getMaxX() < 0 ? env.getMaxX() + 360.0 : env.getMaxX();
                                }
                                Envelope normalizedEnv = new Envelope(minX, maxX, env.getMinY(), env.getMaxY());
                                overallBoundingBox.expandToInclude(normalizedEnv);

                                hasBoundingBoxUpdate.set(true);
                            }
                        }
                    });

            // Now write the first box to the head of list only if we have at least on polygon exist, if no polygon
            // exist then we can skip the reset of operation
            if(hasBoundingBoxUpdate.get()) {
                double minX = overallBoundingBox.getMinX();
                double maxX = overallBoundingBox.getMaxX();
                if (worldLongitudeBox.get() && !partialLongitude.get()) {
                    // -180 and 180 are the same meridian, so the shift above is the line at 180.
                    minX = -180.0;
                    maxX = 180.0;
                }
                else {
                    // Shift to [-180, 180]
                    if (minX > 180.0) minX -= 360.0;
                    if (maxX > 180.0) maxX -= 360.0;
                    // Ensure maxX >= minX, result can be > 180 but needed as we need to represent box cross
                    // meridian, if you do not allow it you may have bbox longitude flipped in wrong direction
                    if (maxX < minX) maxX += 360.0;
                }
                overallBoundingBox.init(minX, maxX, overallBoundingBox.getMinY(), overallBoundingBox.getMaxY());

                result.add(List.of(
                        BigDecimal.valueOf(overallBoundingBox.getMinX()).setScale(scale, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(overallBoundingBox.getMinY()).setScale(scale, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(overallBoundingBox.getMaxX()).setScale(scale, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(overallBoundingBox.getMaxY()).setScale(scale, RoundingMode.HALF_UP)));

                for (List<Geometry> polygons : listOfPolygons) {

                    if (!polygons.isEmpty()) {
                        for (Geometry p : polygons) {
                            final Envelope individualEnvelope = new Envelope();

                            if (p != null && p.getEnvelopeInternal() != null) {
                                individualEnvelope.expandToInclude(p.getEnvelopeInternal());
                            }
                            result.add(List.of(
                                    BigDecimal.valueOf(individualEnvelope.getMinX()).setScale(scale, RoundingMode.HALF_UP),
                                    BigDecimal.valueOf(individualEnvelope.getMinY()).setScale(scale, RoundingMode.HALF_UP),
                                    BigDecimal.valueOf(individualEnvelope.getMaxX()).setScale(scale, RoundingMode.HALF_UP),
                                    BigDecimal.valueOf(individualEnvelope.getMaxY()).setScale(scale, RoundingMode.HALF_UP)));
                        }
                    }
                }
            }
        }

        if(result.isEmpty()) {
            logger.warn("No applicable BBOX calculation found");
        }

        return result;
    }

    /**
     * True for the four-corner box produced from west=-180, east=180.
     * A polygon that merely reaches both sides of the dateline is not this box.
     */
    private static boolean isFullLongitudeRectangle(Geometry geometry, Envelope env) {
        if (env.getMinX() > -180.0 || env.getMaxX() < 180.0) {
            return false;
        }
        Coordinate[] coordinates = geometry.getCoordinates();
        if (coordinates.length < 4 || coordinates.length > 5) {
            return false;
        }
        for (Coordinate coordinate : coordinates) {
            boolean onLongitudeEdge = Math.abs(coordinate.x - env.getMinX()) < 1e-6
                    || Math.abs(coordinate.x - env.getMaxX()) < 1e-6;
            boolean onLatitudeEdge = Math.abs(coordinate.y - env.getMinY()) < 1e-6
                    || Math.abs(coordinate.y - env.getMaxY()) < 1e-6;
            if (!onLongitudeEdge || !onLatitudeEdge) {
                return false;
            }
        }
        return true;
    }
}
