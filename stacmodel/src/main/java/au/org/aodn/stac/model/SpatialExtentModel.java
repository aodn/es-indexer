package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpatialExtentModel {
    /**
     * The gex:EX_Extent/gex:description of one extent block, e.g. a site name
     */
    protected String description;
    /**
     * Bounding box [minLon, minLat, maxLon, maxLat] of that extent block
     */
    protected List<BigDecimal> bbox;
}
