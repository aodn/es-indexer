package au.org.aodn.datadiscoveryai.model;

import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiEnhancementRequest {
    @JsonProperty("selected_model")
    private List<String> selectedModel;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("title")
    private String title;

    @JsonProperty("abstract")
    private String abstractText;

    @JsonProperty("links")
    private List<LinkModel> links;

    @JsonProperty("lineage")
    private String lineageText;

    @JsonProperty("status")
    private String status;

    @JsonProperty("temporal")
    private List<Map<String, String>> temporal;

    @JsonProperty("themes")
    private List<ThemesModel> themes;

    /**
     * Set for the records that are not datasets, for example the IMOS Facility / Sub-Facility records, so the
     * delivery mode is not predicted for them, see https://github.com/aodn/backlog/issues/9003
     * JsonIgnore because it only selects the models to call, the AI service does not need it.
     */
    @JsonIgnore
    private boolean skipDeliveryClassification;
}
