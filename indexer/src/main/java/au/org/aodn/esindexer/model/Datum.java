package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Datum that = (Datum) obj;

        return Double.compare(that.longitude, longitude) == 0 &&
                Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(
                        that.depth != null? that.depth : 0.0,
                        depth != null? depth : 0.0
                ) == 0 &&
                time.equals(that.time);
    }
}
