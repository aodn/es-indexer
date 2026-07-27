package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabModel {
    protected String label;
    @JsonProperty("display_label")
    protected String displayLabel;
    @JsonProperty("hidden_labels")
    protected List<String> hiddenLabels;
    @JsonProperty("alt_labels")
    protected List<String> altLabels;
    @JsonProperty("is_latest_label")
    protected Boolean isLatestLabel;
    @JsonProperty("replaced_by")
    protected String replacedBy;
    protected String definition;
    protected String about;
    protected List<VocabModel> broader;
    protected List<VocabModel> narrower;
    protected String version;

    /**
     * Concat the concept text so that helps to its semantic representation. Include all text for representing a vocab.
     */
    public String toConceptText() {
        List<String> parts = new ArrayList<>();
        if (label != null)        parts.add(label);
        if (displayLabel != null) parts.add(displayLabel);
        if (altLabels != null)    parts.addAll(altLabels);
        if (hiddenLabels != null) parts.addAll(hiddenLabels);
        if (definition != null)   parts.add(definition);
        if (narrower != null)     narrower.stream()
                .map(VocabModel::getLabel).filter(Objects::nonNull).forEach(parts::add);
        return parts.isEmpty() ? null : String.join(". ", parts);
    }
}
