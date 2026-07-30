package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabDto {
    // properties are extendable (e.g platformVocabs, organisationVocabs etc.), currently just parameterVocabs.
    @JsonProperty("parameter_vocab")
    VocabModel parameterVocabModel;

    @JsonProperty("platform_vocab")
    VocabModel platformVocabModel;

    @JsonProperty("organisation_vocab")
    VocabModel organisationVocabModel;

    /**
     * Each entry in the list is the combination text from the level-2 label title, level-2 label description, and the leaf label title of level-2 label.
     */
    @JsonProperty("concept_semantic")
    public List<String> getConceptSemantic() {
        VocabModel model = parameterVocabModel != null ? parameterVocabModel
                         : platformVocabModel  != null ? platformVocabModel
                         : organisationVocabModel;

        if (model == null) {
            return null;
        }

        List<String> conceptTexts = model.getNarrower() == null
                ? List.of()
                : model.getNarrower().stream()
                        .map(VocabModel::toConceptText)
                        .filter(Objects::nonNull)
                        .toList();

        if (conceptTexts.isEmpty()) {
            String ownConceptText = model.toConceptText();
            return ownConceptText == null ? null : List.of(ownConceptText);
        }
        return conceptTexts;
    }
}
