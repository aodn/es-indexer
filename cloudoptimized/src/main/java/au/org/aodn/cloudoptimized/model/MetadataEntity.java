package au.org.aodn.cloudoptimized.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataEntity {

    public static class Depth {
        @JsonProperty("max")
        double max;

        @JsonProperty("min")
        double min;

        @JsonProperty("unit")
        String unit;
    }

    @JsonIgnore
    protected Depth depth;

    @JsonIgnore
    protected String dname;

    @JsonIgnore
    protected String uuid;

    @JsonIgnore
    protected boolean cloudOptimised = true;

    @JsonProperty("depth")
    public void setDepth(Depth v) {
        this.depth = v;
    }

    @JsonProperty("dname")
    public void setDname(String v) {
        this.dname = v;
    }

    @JsonProperty("uuid")
    public void setUuid(String v) {
        this.uuid = v;
    }

    @JsonProperty("cloud_optimised")
    public void setCloudOptimised(boolean v) {
        this.cloudOptimised = v;
    }
}
