package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ExtentModel {
    /**
     * https://github.com/radiantearth/stac-spec/blob/master/collection-spec/collection-spec.md#temporal-extent-object
     *
     * bbox: Each outer array element can be a separate spatial extent describing the bounding boxes of the assets
     * represented by this Collection using either 2D or 3D geometries.
     *
     * The first bounding box always describes the overall spatial extent of the data. All subsequent bounding boxes
     * can be used to provide a more precise description of the extent and identify clusters of data. Clients only
     * interested in the overall spatial extent will only need to access the first item in each array. It is
     * recommended to only use multiple bounding boxes if a union of them would then include a large uncovered
     * area (e.g. the union of Germany and Chile).
     *
     * The length of the inner array must be 2*n where n is the number of dimensions. The array contains all axes
     * of the southwesterly most extent followed by all axes of the northeasterly most extent specified in
     * Longitude/Latitude or Longitude/Latitude/Elevation based on WGS 84. When using 3D geometries, the
     * elevation of the southwesterly most extent is the minimum depth/height in meters and the elevation of the
     * northeasterly most extent is the maximum.
     *
     * The coordinate reference system of the values is WGS 84 longitude/latitude. Example that covers the whole
     * Earth: [[-180.0, -90.0, 180.0, 90.0]]. Example that covers the whole earth with a depth of 100 meters to a
     * height of 150 meters: [[-180.0, -90.0, -100.0, 180.0, 90.0, 150.0]].
     */
    protected List<List<BigDecimal>> bbox;
    /**
     * https://github.com/radiantearth/stac-spec/blob/master/collection-spec/collection-spec.md#temporal-extent-object
     *
     * interval: Each outer array element can be a separate temporal extent. The first time interval always describes
     * the overall temporal extent of the data. All subsequent time intervals can be used to provide a more precise
     * description of the extent and identify clusters of data. Clients only interested in the overall extent will
     * only need to access the first item in each array. It is recommended to only use multiple temporal extents if a
     * union of them would then include a large uncovered time span (e.g. only having data for the years 2000,
     * 2010 and 2020).
     *
     * Each inner array consists of exactly two elements, either a timestamp or null.
     *
     * Timestamps consist of a date and time in UTC and MUST be formatted according to RFC 3339, section 5.6.
     * The temporal reference system is the Gregorian calendar.
     */
    protected List<String[]> temporal;
}
