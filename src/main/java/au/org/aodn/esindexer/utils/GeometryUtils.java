package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.model.SummariesModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryUtils {

    protected static Logger logger = LoggerFactory.getLogger(GeometryUtils.class);

    protected static GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    protected static ObjectMapper objectMapper = new ObjectMapper();
    /**
     *
     * @param polygons - Assume to be EPSG:4326, as GeoJson always use this encoding.
     * @return
     */
    protected static Map createGeoJson(List<Polygon> polygons) {

        if(!polygons.isEmpty()) {
            try (StringWriter writer = new StringWriter()) {
                MultiPolygon multiPolygon = new MultiPolygon(polygons.toArray(new Polygon[polygons.size()]), factory);

                GeometryJSON geometryJson = new GeometryJSON();
                geometryJson.write(multiPolygon, writer);

                Map values = objectMapper.readValue(writer.toString(), HashMap.class);

                logger.debug("Created geometry {}", values);
                return values;
            }
            catch (IOException e) {
                logger.error("Error create geometry", e);
                return null;
            }
        }
        return null;
    }

    public static Map createGeometryFromEXGeographicBoundingBoxType(List<Object> rawInput) {
        // The return polygon is in EPSG:4326, so we can call createGeoJson directly
        //TODO: avoid hardcode CRS, get it from document
        List<Polygon> polygons = GeometryBase.findPolygonsFromEXGeographicBoundingBoxType(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);
        return createGeoJson(polygons);
    }

    public static Map createGeometryFromFromEXBoundingPolygonType(List<Object> rawInput) {
        // The return polygon is in EPSG:4326, so we can call createGeoJson directly
        //TODO: avoid hardcode CRS, get it from document
        List<Polygon> polygons = GeometryBase.findPolygonsFromEXBoundingPolygonType(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);
        return createGeoJson(polygons);
    }
}
