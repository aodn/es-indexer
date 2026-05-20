package au.org.aodn.stac.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CitationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void noArgsConstructor_isPublic() throws Exception {
        // Regression guard: Jackson needs a public no-args ctor to deserialize.
        Citation c = Citation.class.getDeclaredConstructor().newInstance();
        assertNotNull(c);
    }

    @Test
    void roundtrip_preservesFields() throws Exception {
        Citation original = Citation.builder()
                .suggestedCitation("Cite me")
                .useLimitations(List.of("limit-a", "limit-b"))
                .otherConstraints(List.of("constraint-a"))
                .build();

        String json = mapper.writeValueAsString(original);
        Citation parsed = mapper.readValue(json, Citation.class);

        assertEquals(original, parsed);
    }
}
