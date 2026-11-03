package au.org.aodn.cloudoptimized.model;

import au.org.aodn.cloudoptimized.enums.MetadataKeyType;
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

    // null if the data access service does not send "type", or sends an unknown value
    @JsonIgnore
    protected MetadataKeyType type;

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

    // Read as a string, so an unknown value does not fail the whole response
    @JsonProperty("type")
    public void setType(String v) {
        this.type = MetadataKeyType.fromValue(v);
    }
}
