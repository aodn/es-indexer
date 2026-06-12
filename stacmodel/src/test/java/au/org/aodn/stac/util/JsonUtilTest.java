package au.org.aodn.stac.util;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }
}
