package au.org.aodn.esindexer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SummariesModel {

    protected Integer score;
    protected String status;
    protected Map<String, String> scope;
    /**
     * Use to generate the vector tile, the STAC format is not optimized and hard to work with for Elastic search
     */
    @JsonProperty("proj:geometry")
    protected Map geometry;
    /**
     * Use for effective search on temporal.
     */
    @JsonProperty("temporal")
    protected List<Map<String, String>> temporal;
}
