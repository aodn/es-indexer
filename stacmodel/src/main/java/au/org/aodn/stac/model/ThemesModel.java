package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
public class ThemesModel {
    protected List<ConceptModel> concepts;
    protected String scheme;

    public ThemesModel(List<ConceptModel> concepts, String scheme) {
        this.concepts = concepts;
        this.scheme = scheme;
    }
}
