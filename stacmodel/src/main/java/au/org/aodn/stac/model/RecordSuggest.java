package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecordSuggest {
    private String title;
    private String description;
}
