package au.org.aodn.cloudoptimized.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Objects;
import java.util.Optional;

/**
 * A container that represent the data entry from cloud optimize, if you add fields, please
 * update the hashCode() and equals()
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudOptimizedEntry {

    static final BigDecimal MIN = new BigDecimal(Double.MIN_VALUE);

    @JsonIgnore
    protected DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @JsonIgnore
    protected DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @JsonIgnore
    protected Temporal time;

    @JsonIgnore
    protected BigDecimal longitude;

    @JsonIgnore
    protected BigDecimal latitude;

    @JsonIgnore
    protected BigDecimal depth;

    @JsonIgnore
    public ZonedDateTime getZonedDateTime() {
        return ((LocalDateTime)this.time).atZone(ZoneOffset.UTC);
    }

    @JsonProperty("depth")
    public void setDepth(Double v) {
        this.depth = new BigDecimal(v);
    }

    @JsonProperty("longitude")
    public void setLongitude(Double v) {
        this.longitude = new BigDecimal(v);
    }

    @JsonProperty("latitude")
    public void setLatitude(Double v) {
        this.latitude = new BigDecimal(v);
    }

    @JsonProperty("time")
    public void setTime(String time) {
        try {
            this.time = LocalDateTime.parse(time, DATETIME_FORMATTER);
        }
        catch(DateTimeParseException pe) {
            this.time = LocalDateTime.parse(time, DATE_FORMATTER);
        }
    }
    /**
     * Must use function as child class may override functions
     * @return - The hashcode
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                this.getTime(),
                this.getLongitude(),
                this.getLatitude(),
                this.getDepth()
        );
    }
    /**
     * Must use function as child class may override functions
     * @param obj - Input
     * @return - Compare result
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        else if (obj instanceof CloudOptimizedEntry that) {
            int compareLong = Optional
                    .ofNullable(that.getLongitude())
                    .orElse(MIN)
                    .compareTo(Optional.ofNullable(getLongitude())
                            .orElse(MIN));

            int compareLat = Optional
                    .ofNullable(that.getLatitude())
                    .orElse(MIN)
                    .compareTo(Optional.ofNullable(getLatitude())
                            .orElse(MIN));

            int compareDepth = Optional
                    .ofNullable(that.getDepth())
                    .orElse(MIN)
                    .compareTo(Optional.ofNullable(getDepth())
                            .orElse(MIN));

            boolean compareTime = Objects.equals(that.getTime(), getTime());

            return compareLong == 0 &&
                    compareLat == 0 &&
                    compareDepth == 0 &&
                    compareTime;
        }
        else {
            return false;
        }
    }
}
