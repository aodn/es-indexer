package au.org.aodn.stac.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchSuggestionsModelTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void deserialize_envelopedShape_unpacksInnerKeys() throws Exception {
        String json = """
                { "id": "abc",
                  "search_suggestions": {
                    "abstract_phrases": ["foo"],
                    "parameter_vocabs_sayt": ["temperature"],
                    "platform_vocabs_sayt": ["ship"],
                    "organisation_vocabs_sayt": ["CSIRO"]
                  }
                }""";

        SearchSuggestionsModel m = mapper.readValue(json, SearchSuggestionsModel.class);

        assertEquals(List.of("foo"), m.getAbstractPhrases());
        assertEquals(List.of("temperature"), m.getParameterVocabs());
        assertEquals(List.of("ship"), m.getPlatformVocabs());
        assertEquals(List.of("CSIRO"), m.getOrganisationVocabs());
    }

    @Test
    void deserialize_flatShape_stillBinds() throws Exception {
        String json = """
                { "abstract_phrases": ["foo"],
                  "parameter_vocabs_sayt": ["temperature"]
                }""";

        SearchSuggestionsModel m = mapper.readValue(json, SearchSuggestionsModel.class);

        assertEquals(List.of("foo"), m.getAbstractPhrases());
        assertEquals(List.of("temperature"), m.getParameterVocabs());
        assertNull(m.getPlatformVocabs());
        assertNull(m.getOrganisationVocabs());
    }

    @Test
    void deserialize_viaParent_populatesSubField() throws Exception {
        String json = """
                { "uuid": "abc",
                  "search_suggestions": {
                    "abstract_phrases": ["foo"],
                    "parameter_vocabs_sayt": ["temperature"],
                    "platform_vocabs_sayt": ["ship"],
                    "organisation_vocabs_sayt": ["CSIRO"]
                  }
                }""";

        StacCollectionModel parent = mapper.readValue(json, StacCollectionModel.class);

        SearchSuggestionsModel sub = parent.getSearchSuggestionsModel();
        assertNotNull(sub);
        assertEquals(List.of("foo"), sub.getAbstractPhrases());
        assertEquals(List.of("temperature"), sub.getParameterVocabs());
        assertEquals(List.of("ship"), sub.getPlatformVocabs());
        assertEquals(List.of("CSIRO"), sub.getOrganisationVocabs());
    }

    @Test
    void deserialize_nullEnvelope_isTolerated() throws Exception {
        String json = "{ \"search_suggestions\": null }";

        SearchSuggestionsModel m = mapper.readValue(json, SearchSuggestionsModel.class);

        assertNull(m.getAbstractPhrases());
        assertNull(m.getParameterVocabs());
        assertNull(m.getPlatformVocabs());
        assertNull(m.getOrganisationVocabs());
    }

    @Test
    void deserialize_partialEnvelope_leavesAbsentFieldsNull() throws Exception {
        String json = "{ \"search_suggestions\": { \"abstract_phrases\": [\"foo\"] } }";

        SearchSuggestionsModel m = mapper.readValue(json, SearchSuggestionsModel.class);

        assertEquals(List.of("foo"), m.getAbstractPhrases());
        assertNull(m.getParameterVocabs());
        assertNull(m.getPlatformVocabs());
        assertNull(m.getOrganisationVocabs());
    }

    @Test
    void serialize_flatShape_nullsSuppressed() throws Exception {
        SearchSuggestionsModel m = SearchSuggestionsModel.builder()
                .abstractPhrases(List.of("foo"))
                .build();

        String out = mapper.writeValueAsString(m);

        assertEquals("{\"abstract_phrases\":[\"foo\"]}", out);
    }
}
