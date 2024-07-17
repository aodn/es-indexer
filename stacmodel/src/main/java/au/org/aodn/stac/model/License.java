package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class License {
    private String title;
    private String url;
    private String licenseGraphic;
}
