package au.org.aodn.esindexer.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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
public class StacUtils {

    protected static Logger logger = LoggerFactory.getLogger(StacUtils.class);

    public static List<List<BigDecimal>> createStacBBox(List<Polygon> polygons) {
        Envelope envelope = new Envelope();
        try {
            for(Polygon polygon : polygons) {
                // Add polygon one by one to extend the overall bounding box area, this is requirement
                // of STAC to have an overall bounding box of all smaller area as the first bbox in the list.
                envelope = GeometryBase.calculateBoundingBox(envelope, "CRS:84", polygon);
            }

            if (!polygons.isEmpty()) {
                // If it didn't contain polygon, then the envelope is just the initial empty object and thus invalid.
                List<List<BigDecimal>> result = new ArrayList<>();
                result.add(List.of(
                        BigDecimal.valueOf(envelope.getMinX()),
                        BigDecimal.valueOf(envelope.getMinY()),
                        BigDecimal.valueOf(envelope.getMaxX()),
                        BigDecimal.valueOf(envelope.getMaxY())));

                for (Polygon p : polygons) {
                    List<BigDecimal> points = new ArrayList<>();
                    for (Coordinate c : p.getCoordinates()) {
                        points.addAll(List.of(
                            BigDecimal.valueOf(c.getX()), BigDecimal.valueOf(c.getY())
                        ));
                    }
                    result.add(points);
                }
                return result;
            }
            else {
                logger.warn("No applicable BBOX calculation found");
                return null;
            }
        }
        catch(Exception e) {
            logger.error("Error processing", e);
            e.printStackTrace();
        }
        return null;
    }
}

