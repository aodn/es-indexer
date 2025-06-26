package au.org.aodn.datadiscoveryai.model;

import au.org.aodn.stac.model.LinkModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiEnhancementRequest {
    @JsonProperty("selected_model")
    private List<String> selectedModel;
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("links")
    private List<LinkModel> links;
} 
