package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchSuggestionsModel {
    @JsonProperty("abstract_phrases")
    private Set<String> abstractPhrases;

    //sayt stands for search_as_you_type
    @JsonProperty("parameter_vocabs_sayt")
    private Set<String> parameterVocabs;

    @JsonProperty("platform_vocabs_sayt")
    private Set<String> platformVocabs;

    @JsonProperty("organisation_vocabs_sayt")
    private Set<String> organisationVocabs;
}
