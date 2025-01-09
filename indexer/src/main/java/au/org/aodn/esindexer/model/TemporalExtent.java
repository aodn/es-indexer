package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Builder
@Setter
@Getter
public class TemporalExtent{

    static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    @JsonProperty("start_date")
    protected String startDate;

    @JsonProperty("end_date")
    protected String endDate;

    @JsonIgnore
    public LocalDate getLocalStartDate() {
        return ZonedDateTime.parse(startDate, DATE_FORMAT).toLocalDate();
    }

    @JsonIgnore
    public LocalDate getLocalEndDate() {
        return ZonedDateTime.parse(endDate, DATE_FORMAT).toLocalDate();
    }
}
