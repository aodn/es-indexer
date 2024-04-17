package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
@Builder
public class ThemesModel {
    protected List<ConceptModel> concepts;
    protected String scheme;
    protected String description;
    protected String title;

    public ThemesModel(List<ConceptModel> concepts, String scheme, String description, String title) {
        this.concepts = concepts;
        this.scheme = scheme;
        this.description = description;
        this.title = title;
    }
}
