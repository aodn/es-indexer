package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Objects;
import java.util.Optional;

//  If more fields are needed to be filtered, please add more columns here
//  and don't forget updating the override equals() method
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Datum {

    private String time;
    private Double longitude;
    private Double latitude;
    private Double depth;

    private long count;

    @JsonCreator
    public Datum(
            @JsonProperty("time") String time,
            @JsonProperty("longitude") Double longitude,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("depth") Double depth) {
        this.time = time;
        this.longitude = longitude;
        this.latitude = latitude;
        this.depth = depth;
        this.count = 1L;
    }

    // putting all same record into one instance and increment the count is more efficient
    public void incrementCount() {
        count++;
    }

    // Don't include variable "count" in the equals() method.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        else if (obj instanceof Datum that) {
            int compareLong = Optional
                    .ofNullable(that.longitude)
                    .orElse(Double.NEGATIVE_INFINITY)
                    .compareTo(Optional.ofNullable(longitude)
                            .orElse(Double.NEGATIVE_INFINITY));

            int compareLat = Optional
                    .ofNullable(that.latitude)
                    .orElse(Double.NEGATIVE_INFINITY)
                    .compareTo(Optional.ofNullable(latitude)
                            .orElse(Double.NEGATIVE_INFINITY));

            int compareDepth = Optional
                    .ofNullable(that.depth)
                    .orElse(Double.NEGATIVE_INFINITY)
                    .compareTo(Optional.ofNullable(depth)
                            .orElse(Double.NEGATIVE_INFINITY));

            boolean compareTime = Objects.equals(that.time, time);

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
