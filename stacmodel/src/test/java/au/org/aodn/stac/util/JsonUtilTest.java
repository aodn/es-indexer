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

            // Acronym expansion lives on dedicated synonyms sub-fields, kept off the primary
            // title/description fields so their analysis stays clean.
            JsonNode title = root.path("mappings").path("properties").path("title");
            assertFalse(title.has("search_analyzer"), "primary title must not carry the acronym analyzer");
            assertEquals("acronym_search_analyser", title.path("fields").path("synonyms").path("search_analyzer").asText(),
                    "title.synonyms sub-field should declare the acronym search_analyzer");

            JsonNode desc = root.path("mappings").path("properties").path("description");
            assertFalse(desc.has("search_analyzer"), "primary description must not carry the acronym analyzer");
            assertEquals("acronym_search_analyser", desc.path("fields").path("synonyms").path("search_analyzer").asText());
        }
    }

    /**
     * semantic_text needs the licensed `inference` feature; on a basic-licence cluster an index carrying
     * one rejects every document written to it. With semantic disabled the schema must come back with no
     * semantic field and no dangling copy_to, while everything else stays intact.
     */
    @Test
    public void verifySemanticFieldsStrippedWhenDisabled() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        try (Reader r = JsonUtil.createJsonStream("portal_records_index_schema.json",
                Map.of("portal-acronyms", "it-synset"), false)) {
            assertNotNull(r);
            JsonNode props = mapper.readTree(new BufferedReader(r).lines().collect(Collectors.joining("\n")))
                    .path("mappings").path("properties");

            assertTrue(props.path("description_semantic").isMissingNode(),
                    "description_semantic must be gone when semantic is disabled");
            assertFalse(props.path("description").has("copy_to"),
                    "description.copy_to must go with the field it fed, or ES rejects the mapping");

            // untouched neighbours
            assertEquals("text", props.path("description").path("type").asText());
            assertEquals("acronym_search_analyser",
                    props.path("description").path("fields").path("synonyms").path("search_analyzer").asText());
            assertEquals("keyword", props.path("parameter_vocabs").path("type").asText());
        }

        try (Reader r = JsonUtil.createJsonStream("vocabs_index_schema.json", null, false)) {
            assertNotNull(r);
            JsonNode props = mapper.readTree(new BufferedReader(r).lines().collect(Collectors.joining("\n")))
                    .path("mappings").path("properties");

            assertTrue(props.path("concept_semantic").isMissingNode(),
                    "concept_semantic must be gone when semantic is disabled");
            assertTrue(props.has("parameter_vocab"), "the rest of the vocabs mapping must survive");
            assertTrue(props.has("platform_vocab"));
            assertTrue(props.has("organisation_vocab"));
        }
    }

    /**
     * The default (and the licensed clusters) must still get the semantic fields.
     */
    @Test
    public void verifySemanticFieldsKeptWhenEnabled() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        try (Reader r = JsonUtil.createJsonStream("portal_records_index_schema.json",
                Map.of("portal-acronyms", "it-synset"))) {
            assertNotNull(r);
            JsonNode props = mapper.readTree(new BufferedReader(r).lines().collect(Collectors.joining("\n")))
                    .path("mappings").path("properties");

            assertEquals("semantic_text", props.path("description_semantic").path("type").asText());
            assertEquals("description_semantic", props.path("description").path("copy_to").asText());
        }

        try (Reader r = JsonUtil.createJsonStream("vocabs_index_schema.json", null)) {
            assertNotNull(r);
            JsonNode props = mapper.readTree(new BufferedReader(r).lines().collect(Collectors.joining("\n")))
                    .path("mappings").path("properties");

            assertEquals("semantic_text", props.path("concept_semantic").path("type").asText());
        }
    }
}
