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
    @JsonProperty("parameter_vocab")
    ParameterVocabModel parameterVocabModel;

    @JsonProperty("platform_vocab")
    PlatformVocabModel platformVocabModel;

    @JsonProperty("organisation_vocab")
    OrganisationVocabModel organisationVocabModel;
}
