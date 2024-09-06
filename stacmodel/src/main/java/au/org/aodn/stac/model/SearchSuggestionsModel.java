package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchSuggestionsModel {
    @JsonProperty("abstract_phrases")
    private List<String> abstractPhrases;

    //sayt stands for search_as_you_type
    @JsonProperty("parameter_vocabs_sayt")
    private List<String> parameterVocabs;

    @JsonProperty("platform_vocabs_sayt")
    private List<String> platformVocabs;

    @JsonProperty("organisation_vocabs_sayt")
    private List<String> organisationVocabs;
}
