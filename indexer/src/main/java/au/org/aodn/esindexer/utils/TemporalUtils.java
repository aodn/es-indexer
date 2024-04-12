package au.org.aodn.esindexer.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TemporalUtils {

    protected static final Logger logger = LogManager.getLogger(TemporalUtils.class);

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    /**
     * Refer to the spec why we need to do this, in short we need an overall temporal
     * @param temporals
     * @return
     */
    public static List<String[]> concatOverallTemporalRange(List<String[]> temporals) {
        ZonedDateTime min = null;
        ZonedDateTime max = null;

        if(temporals != null) {
            for (String[] temporal : temporals) {
                if (temporal[0] != null) {
                    ZonedDateTime t = ZonedDateTime.parse(temporal[0]);
                    min = (min == null || min.isAfter(t)) ? t : min;
                }

                if (temporal[1] != null) {
                    ZonedDateTime t = ZonedDateTime.parse(temporal[1]);
                    max = (max == null || min.isBefore(t)) ? t : max;
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
