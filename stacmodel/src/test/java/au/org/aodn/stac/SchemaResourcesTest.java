package au.org.aodn.stac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaResourcesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dataIndexSchema_isOnClasspath() throws Exception {
        assertLoadsAsJson("/schema/data_index_schema.json");
    }

    @Test
    void portalRecordsIndexSchema_isOnClasspath() throws Exception {
        assertLoadsAsJson("/schema/portal_records_index_schema.json");
    }

    @Test
    void vocabsIndexSchema_isOnClasspath() throws Exception {
        assertLoadsAsJson("/schema/vocabs_index_schema.json");
    }

    /** Each non-comment line in portal-acronyms.txt must match "acronym => full name". */
    @Test
    void portalAcronyms_isWellFormed() throws Exception {
        Pattern rule = Pattern.compile("^[a-z0-9][a-z0-9 \\-]* => .+$");

        try (InputStream in = getClass().getResourceAsStream("/portal-acronyms.txt")) {
            assertNotNull(in, "portal-acronyms.txt not found on classpath");

            List<String> lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .filter(l -> !l.trim().isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toList());

            assertFalse(lines.isEmpty(), "Dictionary has no rules");
            for (String line : lines) {
                assertTrue(rule.matcher(line).matches(),
                        "Line does not match 'acronym => full name': " + line);
            }
        }
    }

    private void assertLoadsAsJson(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, "resource not found on classpath: " + path);
            assertNotNull(mapper.readTree(in), "resource is not valid JSON: " + path);
        }
    }
}
