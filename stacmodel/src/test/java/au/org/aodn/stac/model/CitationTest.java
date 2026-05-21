package au.org.aodn.stac.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Standalone on purpose — Citation is the one model not reachable through
// StacCollectionModel's full-document roundtrip. On the wire, sci:citation is
// stored as a double-encoded JSON string so StacCollectionModel.citation is `String`, not `Citation`.
class CitationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundtrip_preservesFields() throws Exception {
        Citation original = Citation.builder()
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
        Citation parsed = mapper.readValue(json, Citation.class);

        assertEquals(original, parsed);
    }
}
