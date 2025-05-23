package au.org.aodn.cloudoptimized.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeParseException;

/**
 * This class modified the time entry and consider the Year and Month only, and reduce precision it is used
 * later to aggregate data based on year ond month only
 */
public class CloudOptimizedEntryReducePrecision extends CloudOptimizedEntry {

    @Override
    public void setTime(String time) {
        try {
            LocalDateTime l = LocalDateTime.parse(time, DATETIME_FORMATTER);
            this.time = YearMonth.of(l.getYear(), l.getMonth());
        }
        catch(DateTimeParseException pe) {
            LocalDate l = LocalDate.parse(time, DATE_FORMATTER);
            this.time = YearMonth.of(l.getYear(), l.getMonth());
        }
    }

    @Override
    public void setLongitude(Double v) {
        if(v != null) {
            this.longitude = new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
        }
    }

    @Override
    public void setLatitude(Double v) {
        if(v != null) {
            this.latitude = new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
        }
    }
    /**
     * Round to 10th position, with two decimal place so later parse will generate .00
     * @param v - Input
     */
    @Override
    public void setDepth(Double v) {
        if(v != null) {
            this.depth = new BigDecimal(v)
                    .divide(BigDecimal.TEN, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
    /**
     * Return a zoned datetime in this case because we use YearMonth internally, we set the day to 1 and all time zero
     * @return - A ZonedDateTime at day 1 time 0
     */
    @Override
    public ZonedDateTime getZonedDateTime() {
        return ((YearMonth)this.time)
                .atDay(1)
                .atTime(0,0,0)
                .atZone(ZoneOffset.UTC);
    }
}
