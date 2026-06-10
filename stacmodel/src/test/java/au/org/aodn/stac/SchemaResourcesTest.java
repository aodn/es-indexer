package au.org.aodn.stac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private void assertLoadsAsJson(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, "resource not found on classpath: " + path);
            assertNotNull(mapper.readTree(in), "resource is not valid JSON: " + path);
        }
    }
}
