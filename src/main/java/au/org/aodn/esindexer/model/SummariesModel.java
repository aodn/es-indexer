package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SummariesModel {

    protected Integer score;
    protected String status;
    protected Map<String, String> scope;

    @JsonProperty("proj:geometry")
    protected Map geometry;
}
