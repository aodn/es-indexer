package au.org.aodn.datadiscoveryai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiEnhancementResponse {

    @JsonProperty("summaries")
    private Map<String, String> summaries;

    @JsonProperty("links")
    private List<AiEnhancedLink> links;
}
