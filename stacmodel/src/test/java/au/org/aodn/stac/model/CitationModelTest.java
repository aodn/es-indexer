package au.org.aodn.stac.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Standalone on purpose — CitationModel is the one model not reachable through
// StacCollectionModel's full-document roundtrip. On the wire, sci:citation is
// stored as a double-encoded JSON string so StacCollectionModel.citation is `String`, not `CitationModel`.
class CitationModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundtrip_preservesFields() throws Exception {
        CitationModel original = CitationModel.builder()
                .suggestedCitation(
                        "IMOS [year-of-data-download], [Title], [data-access-URL], "
                                + "accessed [date-of-access].")
                .useLimitations(List.of(
                        "Data, products and services from IMOS are provided \"as is\" "
                                + "without any warranty as to fitness for a particular purpose."))
                .otherConstraints(List.of(
                        "Any users of IMOS data are required to clearly acknowledge the "
                                + "source of the material derived from IMOS."))
                .build();

        String json = mapper.writeValueAsString(original);
        CitationModel parsed = mapper.readValue(json, CitationModel.class);

        assertEquals(original, parsed);
    }
}
