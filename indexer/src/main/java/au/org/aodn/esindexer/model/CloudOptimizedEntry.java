package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    @JsonIgnore
    protected DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @JsonIgnore
    protected Temporal time;

    @JsonIgnore
    protected Double longitude;

    @JsonIgnore
    protected Double latitude;

    @JsonIgnore
    protected Double depth;

    @JsonIgnore
    public ZonedDateTime getZonedDateTime() {
        return ((LocalDateTime)this.time).atZone(ZoneOffset.UTC);
    }

    @JsonProperty("DEPTH")
    public void setDepth(Double v) {
        this.depth = v;
    }

    @JsonProperty("LONGITUDE")
    public void setLongitude(Double v) {
        this.longitude = v;
    }

    @JsonProperty("LATITUDE")
    public void setLatitude(Double v) {
        this.latitude = v;
    }

    @JsonProperty("TIME")
    public void setTime(String time) {
        this.time = LocalDateTime.parse(time, DATE_FORMATTER);
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
                    .orElse(Double.NEGATIVE_INFINITY)
                    .compareTo(Optional.ofNullable(getLongitude())
                            .orElse(Double.NEGATIVE_INFINITY));

            int compareLat = Optional
                    .ofNullable(that.getLatitude())
                    .orElse(Double.NEGATIVE_INFINITY)
                    .compareTo(Optional.ofNullable(getLatitude())
                            .orElse(Double.NEGATIVE_INFINITY));

            int compareDepth = Optional
                    .ofNullable(that.getDepth())
                    .orElse(Double.NEGATIVE_INFINITY)
                    .compareTo(Optional.ofNullable(getDepth())
                            .orElse(Double.NEGATIVE_INFINITY));

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
