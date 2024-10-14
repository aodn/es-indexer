package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabModel {
    protected String label;
    protected String displayLabel;
    protected List<String> hiddenLabels;
    protected List<String> altLabels;
    protected String definition;
    protected String about;
    protected List<VocabModel> broader;
    protected List<VocabModel> narrower;
    protected String version;
}
