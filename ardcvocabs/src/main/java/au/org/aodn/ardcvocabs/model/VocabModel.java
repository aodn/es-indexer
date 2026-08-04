package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        List<String> parts = Stream.of(
                        Stream.of(label, displayLabel),
                        stream(altLabels),
                        stream(hiddenLabels),
                        Stream.of(definition),
                        stream(narrower).map(VocabModel::getLabel))
                .flatMap(s -> s)
                .filter(Objects::nonNull)
                .toList();

        return parts.isEmpty() ? null : String.join(". ", parts);
    }

    private static <T> Stream<T> stream(List<T> values) {
        return values == null ? Stream.empty() : values.stream();
    }
}
