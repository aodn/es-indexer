package au.org.aodn.esindexer.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// TODO: if more fields are needed to be filtered, please add more columns here
@Getter
@Setter
public class DataRecord{


    private final LocalDate time;
    private final double longitude;
    private final double latitude;
    private final double depth;

    private long count = 1;

    public DataRecord(LocalDate time, double longitude, double latitude, double depth) {
        this.time = time;
        this.longitude = longitude;
        this.latitude = latitude;
        this.depth = depth;
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
        DataRecord that = (DataRecord) obj;
        return Double.compare(that.longitude, longitude) == 0 &&
                Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.depth, depth) == 0 &&
                time.equals(that.time);
    }
}
