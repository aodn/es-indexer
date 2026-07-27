package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

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
     * Computed property serialized into every indexed doc so the {@code concept_semantic} semantic_text
     * field is populated automatically (bulk index serializes this DTO). {@code @JsonInclude(NON_NULL)}
     * omits it when there is no text to embed.
     */
    @JsonProperty("concept_semantic")
    public String getConceptSemantic() {
        VocabModel m = parameterVocabModel != null ? parameterVocabModel
                     : platformVocabModel  != null ? platformVocabModel
                     : organisationVocabModel;
        return m == null ? null : m.toConceptText();
    }
}
