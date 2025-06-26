package au.org.aodn.datadiscoveryai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiEnhancedLink {
    
    @JsonProperty("href")
    private String href;
    
    @JsonProperty("rel")
    private String rel;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("ai:group")
    private String aiGroup;
} 
