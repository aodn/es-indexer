package au.org.aodn.esindexer.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * This class modified the time entry and consider the Year and Month only, and reduce precision it is used
 * later to aggregate data based on year ond month only
 */
public class CloudOptimizedEntryReducePrecision extends CloudOptimizedEntry {

    @Override
    public void setTime(String time) {
        LocalDateTime l = LocalDateTime.parse(time, DATE_FORMATTER);
        this.time = YearMonth.of(l.getYear(), l.getMonth());
    }

    @Override
    public void setLongitude(Double v) {
        super.setLongitude(new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    @Override
    public void setLatitude(Double v) {
        super.setLatitude(new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).doubleValue());
    }
    /**
     * Round to 10 digit not decimal place
     * @param v - Input
     */
    @Override
    public void setDepth(Double v) {
        super.setDepth(new BigDecimal(v / 10).setScale(0, RoundingMode.HALF_UP).doubleValue() * 10);
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
