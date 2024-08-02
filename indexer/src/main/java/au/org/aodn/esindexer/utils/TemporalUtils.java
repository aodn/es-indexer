package au.org.aodn.esindexer.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TemporalUtils {

    protected static final Logger logger = LogManager.getLogger(TemporalUtils.class);

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    /**
     * Refer to the spec why we need to do this, in short we need an overall temporal that cover the full range
     * of all the discrete start/end
     * @param temporals
     * @return
     */
    public static List<String[]> concatOverallTemporalRange(List<String[]> temporals) {
        ZonedDateTime min = null;
        // Set the max to the smallest to give change to increase in value.
        ZonedDateTime max = Instant.EPOCH.atZone(ZoneOffset.UTC);

        if(temporals != null) {
            for (String[] temporal : temporals) {
                if (temporal[0] != null) {
                    ZonedDateTime t = ZonedDateTime.parse(temporal[0]);
                    min = (min == null || min.isAfter(t)) ? t : min;
                }

                if (temporal[1] != null && max != null) {
                    ZonedDateTime t = ZonedDateTime.parse(temporal[1]);
                    max = max.isBefore(t) ? t : max;
                }
                else {
                    // null indicate no end day yet, so it means latest and always the greatest
                    // so once it set to null, there is no need to set other values
                    max = null;
                }
            }
            // Append the overall to the front
            List<String[]> f = new ArrayList<>();

            f.add(new String[]{
                            min == null ? null : min.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            max == null ? null : max.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    }
            );

            f.addAll(temporals);
            return f;
        }
        else {
            return null;
        }
    }
}
