package au.org.aodn.stac.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class JsonUtilTest {

    @Test
    public void verifyCreateJsonStreamWorks() throws Exception {
        // Load schema with no params - file without placeholders
        try (Reader r = JsonUtil.createJsonStream("vocabs_index_schema.json", null)) {
            assertNotNull(r, "should return reader for existing schema file");
            String content = new BufferedReader(r).lines().collect(Collectors.joining("\n"));
            assertTrue(content.contains("\"parameter_vocab\""), "content should include expected schema element");
            assertTrue(content.contains("\"platform_vocab\""), "content should include expected schema element");
        }

        // Load with params - verify placeholder substitution
        try (Reader r = JsonUtil.createJsonStream("portal_records_index_schema.json", Map.of("portal-acronyms", "test-synonym-set-123"))) {
            assertNotNull(r, "should return reader when using substitution params");
            String content = new BufferedReader(r).lines().collect(Collectors.joining("\n"));
            assertTrue(content.contains("\"synonyms_set\": \"test-synonym-set-123\""), "placeholder should be replaced with provided value");
            assertFalse(content.contains("${portal-acronyms}"), "original placeholder should not remain after substitution");
        }

        // Null params map on file with placeholder - placeholder remains
        try (Reader r = JsonUtil.createJsonStream("portal_records_index_schema.json", null)) {
            assertNotNull(r);
            String content = new BufferedReader(r).lines().collect(Collectors.joining("\n"));
            assertTrue(content.contains("${portal-acronyms}"), "placeholder should remain when no params provided");
        }

        // Empty params map - no substitutions performed
        try (Reader r = JsonUtil.createJsonStream("portal_records_index_schema.json", Map.of())) {
            assertNotNull(r);
            String content = new BufferedReader(r).lines().collect(Collectors.joining("\n"));
            assertTrue(content.contains("${portal-acronyms}"), "placeholder should remain with empty params map");
        }

        // Non-existent file returns null
        Reader r = JsonUtil.createJsonStream("does-not-exist.json", null);
        assertNull(r, "should return null for missing resource");

        // Critical: synonym_graph filter using updateable synonyms_set MUST NOT be present
        // in any index-time analyzer (ES forbids it). It may only appear via search_analyzer.
        try (Reader reader = JsonUtil.createJsonStream("portal_records_index_schema.json", Map.of("portal-acronyms", "it-synset"))) {
            assertNotNull(reader);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new BufferedReader(reader).lines().collect(Collectors.joining("\n")));

            JsonNode customAnalyser = root.path("settings").path("analysis").path("analyzer").path("custom_analyser");
            JsonNode searchAnalyser = root.path("settings").path("analysis").path("analyzer").path("acronym_search_analyser");

            String customFilters = customAnalyser.path("filter").toString();
            String searchFilters = searchAnalyser.path("filter").toString();

            assertFalse(customFilters.contains("acronym_synonym_filter"),
                    "custom_analyser (index) must not reference the synonym filter");
            assertTrue(searchFilters.contains("acronym_synonym_filter"),
                    "acronym_search_analyser (search) must include the synonym filter");

            // Spot-check that search_analyzer is wired on key fields
            JsonNode title = root.path("mappings").path("properties").path("title");
            assertTrue(title.has("search_analyzer"), "title should declare search_analyzer for acronym expansion");
            assertEquals("acronym_search_analyser", title.path("search_analyzer").asText());

            JsonNode desc = root.path("mappings").path("properties").path("description");
            assertTrue(desc.has("search_analyzer"));
        }
    }
}
