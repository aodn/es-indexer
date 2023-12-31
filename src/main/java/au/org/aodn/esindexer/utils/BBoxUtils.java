package au.org.aodn.esindexer.utils;

import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

public class BBoxUtils {

    protected static Logger logger = LoggerFactory.getLogger(BBoxUtils.class);

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
