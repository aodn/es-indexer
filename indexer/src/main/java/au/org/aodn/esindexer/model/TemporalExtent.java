package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TemporalExtent{
    @JsonProperty("start_date")
    String startDate;
    @JsonProperty("end_date")
    String endDate;

    public LocalDate getStartDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return LocalDate.parse(startDate, formatter);
    }

    public LocalDate getEndDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return LocalDate.parse(endDate, formatter);
    }
}
