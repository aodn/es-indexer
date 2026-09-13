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

            JsonNode analysis = root.path("settings").path("analysis");
            JsonNode indexAnalyser = analysis.path("analyzer").path("acronym_index_analyser");
            JsonNode searchAnalyser = analysis.path("analyzer").path("acronym_search_analyser");
            JsonNode possessiveStemmer = analysis.path("filter").path("english_possessive_stemmer");

            assertEquals("[\"lowercase\",\"english_possessive_stemmer\"]",
                    indexAnalyser.path("filter").toString(),
                    "acronym_index_analyser must strip possessives without using the synonym filter");
            assertFalse(indexAnalyser.path("filter").toString().contains("acronym_synonym_filter"),
                    "acronym_index_analyser must not reference the updateable synonym filter");
            assertEquals("[\"lowercase\",\"english_possessive_stemmer\",\"acronym_synonym_filter\"]",
                    searchAnalyser.path("filter").toString(),
                    "the possessive stemmer must run before acronym expansion at search time");
            assertEquals("stemmer", possessiveStemmer.path("type").asText(),
                    "english_possessive_stemmer must be declared before an analyser can reference it");
            assertEquals("possessive_english", possessiveStemmer.path("language").asText());

            // Acronym expansion lives on dedicated synonyms sub-fields, kept off the primary
            // title/description fields so their analysis stays clean.
            JsonNode title = root.path("mappings").path("properties").path("title");
            assertFalse(title.has("search_analyzer"), "primary title must not carry the acronym analyzer");
            JsonNode titleSynonyms = title.path("fields").path("synonyms");
            assertEquals("acronym_index_analyser", titleSynonyms.path("analyzer").asText(),
                    "title.synonyms sub-field should declare the acronym index analyzer");
            assertEquals("acronym_search_analyser", titleSynonyms.path("search_analyzer").asText(),
                    "title.synonyms sub-field should declare the acronym search_analyzer");

            JsonNode desc = root.path("mappings").path("properties").path("description");
            assertFalse(desc.has("search_analyzer"), "primary description must not carry the acronym analyzer");
            JsonNode descSynonyms = desc.path("fields").path("synonyms");
            assertEquals("acronym_index_analyser", descSynonyms.path("analyzer").asText());
            assertEquals("acronym_search_analyser", descSynonyms.path("search_analyzer").asText());
        }
    }

    @Test
    public void verifySemanticFieldsPresentInSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        try (Reader r = JsonUtil.createJsonStream("vocabs_index_schema.json", null)) {
            assertNotNull(r);
            JsonNode props = mapper.readTree(new BufferedReader(r).lines().collect(Collectors.joining("\n")))
                    .path("mappings").path("properties");

            assertEquals("semantic_text", props.path("concept_semantic").path("type").asText());
        }
    }
}
