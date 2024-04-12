package au.org.aodn.esindexer.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.*;

import java.math.BigDecimal;
import java.util.List;

public class BBoxUtils {

    protected static Logger logger = LogManager.getLogger(BBoxUtils.class);

    public static List<List<BigDecimal>> createBBoxFromEXBoundingPolygonType(List<Object> rawInput) {
        //TODO: avoid hardcode CRS, get it from document
        List<Polygon> polygons = GeometryBase.findPolygonsFromEXBoundingPolygonType(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);
        return StacUtils.createStacBBox(polygons);
    }

    public static List<List<BigDecimal>> createBBoxFromEXGeographicBoundingBoxType(List<Object> rawInput) {
        //TODO: avoid hardcode CRS, get it from document
        List<Polygon> polygons = GeometryBase.findPolygonsFromEXGeographicBoundingBoxType(GeometryBase.COORDINATE_SYSTEM_CRS84, rawInput);
        return StacUtils.createStacBBox(polygons);
    }
}
