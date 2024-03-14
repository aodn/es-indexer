package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkModel {
    protected String href;
    protected String rel;
    protected String type;
    protected String title;
}
