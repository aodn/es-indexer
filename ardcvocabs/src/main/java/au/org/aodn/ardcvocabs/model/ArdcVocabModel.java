package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArdcVocabModel {
    // properties are extendable (e.g platformVocabs, organisationVocabs etc.), currently just parameterVocabs.
    @JsonProperty("parameter_vocabs")
    List<ParameterVocabModel> parameterVocabModels;
}
