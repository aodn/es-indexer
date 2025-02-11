package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummariesModel {

    protected Integer score;
    protected String status;
    protected List<String> credits;
    protected Map<String, String> scope;
    protected String statement;
    protected String creation;
    protected String revision;

    /**
     * Group info as setup in geonetwork
     */
    @JsonProperty("dataset_group")
    protected String datasetGroup;
    /**
     * It is used to flag who owns the dataset, right now this field appears if it is from IMOS
     */
    @JsonProperty("dataset_provider")
    protected String datasetProvider;
    /**
     * Indicate how fast the update happens, it is use by portal to identity near real-time data.
     */
    @JsonProperty("update_frequency")
    protected String updateFrequency;
    /**
     * Use to generate the vector tile, the STAC format is not optimized and hard to work with for Elastic search
     */
    @JsonProperty("proj:geometry")
    protected Map<?, ?> geometry;
    /**
     * A spatial extents with land area removed. OGCapi will use this to create centroid point, so point will
     * not fall on land.
     */
    @JsonProperty("proj:geometry_noland")
    protected Map<?, ?> geometryNoLand;
    /**
     * Use for effective search on temporal.
     */
    @JsonProperty("temporal")
    protected List<Map<String, String>> temporal;

    /**
     * Discovery Parameter Vocabs
     */
    @JsonProperty("parameter_vocabs")
    protected Set<String> parameterVocabs;

    /**
     * ARDC platform vocabs
     */
    @JsonProperty("platform_vocabs")
    protected Set<String> platformVocabs;

    /**
     * ARDC organisation vocabs
     */
    @JsonProperty("organisation_vocabs")
    protected Set<String> organisationVocabs;
}
