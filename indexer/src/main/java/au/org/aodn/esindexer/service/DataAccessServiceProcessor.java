package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.model.*;
import au.org.aodn.esindexer.utils.GeometryUtils;
import au.org.aodn.stac.model.StacItemModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class DataAccessServiceProcessor {
    /**
     * Group and count the entries based on user object equals/hashcode
     *
     * @param uuid - The parent uuid that associate with input
     * @param data - The aggregated data
     * @return - List of formatted stac item
     */
    public static List<StacItemModel> toStacItemModel(String uuid, Map<? extends CloudOptimizedEntry, Long> data) {
        return data.entrySet().stream()
                .filter(d -> d.getKey().getLongitude() != null && d.getKey().getLatitude() != null)
                .map(d -> {
                    StacItemModel.StacItemModelBuilder builder = StacItemModel.builder()
                            .collection(uuid) // collection point to the uuid of parent
                            .uuid(String
                                    .join("|",
                                            uuid,
                                            d.getKey().getTime().toString(),
                                            d.getKey().getLongitude().toString(),
                                            d.getKey().getLatitude().toString(),
                                            d.getKey().getDepth() != null ? d.getKey().getDepth().toString() : "*"
                                    )
                            )
                            // The elastic query cannot sort by geo_shape or geo_point, so need to flatten value in properties
                            // this geometry is use for filtering
                            .geometry(GeometryUtils.createGeoShapeJson(d.getKey().getLongitude(), d.getKey().getLatitude()));

                    Map<String, Object> props = new HashMap<>() {{
                        // Fields dup here is use for aggregation, you must have the geo_shape to do spatial search
                        put("lng", d.getKey().getLongitude().doubleValue());
                        put("lat", d.getKey().getLatitude().doubleValue());
                        put("count", d.getValue());
                        var dates = new ArrayList<DateCountPair>();
                        var date = d.getKey().getZonedDateTime().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                        dates.add(new DateCountPair(date, d.getValue()));
                        put("dates", dates);

                        // Some data do not have depth!
                        if (d.getKey().getDepth() != null) {
                            put("depth", d.getKey().getDepth().doubleValue());
                        }
                    }};

                    return builder.properties(props).build();

                })
                .toList();
    }
}
