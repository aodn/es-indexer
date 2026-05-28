package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchSuggestionsModel {

    public static final String KEY_ABSTRACT_PHRASES    = "abstract_phrases";
    //sayt stands for search_as_you_type
    public static final String KEY_PARAMETER_VOCABS    = "parameter_vocabs_sayt";
    public static final String KEY_PLATFORM_VOCABS     = "platform_vocabs_sayt";
    public static final String KEY_ORGANISATION_VOCABS = "organisation_vocabs_sayt";

    @JsonProperty(KEY_ABSTRACT_PHRASES)
    private List<String> abstractPhrases;

    @JsonProperty(KEY_PARAMETER_VOCABS)
    private List<String> parameterVocabs;

    @JsonProperty(KEY_PLATFORM_VOCABS)
    private List<String> platformVocabs;

    @JsonProperty(KEY_ORGANISATION_VOCABS)
    private List<String> organisationVocabs;

    // Allows standalone deserialisation when the ES _source carries the four vocab
    // arrays under a "search_suggestions" envelope. The sub-field path (parent
    // StacCollectionModel descending into this model) still uses the flat
    // Lombok-generated setters above.
    @JsonProperty("search_suggestions")
    private void unpackSearchSuggestionsEnvelope(Map<String, List<String>> inner) {
        if (inner == null) {
            return;
        }
        this.abstractPhrases    = inner.get(KEY_ABSTRACT_PHRASES);
        this.parameterVocabs    = inner.get(KEY_PARAMETER_VOCABS);
        this.platformVocabs     = inner.get(KEY_PLATFORM_VOCABS);
        this.organisationVocabs = inner.get(KEY_ORGANISATION_VOCABS);
    }
}
