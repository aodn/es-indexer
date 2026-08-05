package au.org.aodn.esindexer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticSearchIndexServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsNormalisedMappingsAndExtraDynamicFields() throws Exception {
        var expected = mapper.readTree("""
                {"concept_semantic":{"type":"semantic_text"},"parameter_vocab":{"properties":{"label":{"type":"text"}}}}
                """);
        var live = mapper.readTree("""
                {"dynamic":"true","concept_semantic":{"type":"semantic_text","model_settings":{"task_type":"semantic_search"}},
                 "parameter_vocab":{"properties":{"label":{"type":"text"},"version":{"type":"text"},"narrower":{"properties":{"definition":{"type":"text"}}}}}}
                """);

        assertFalse(ElasticSearchIndexService.mappingPropertiesOutdated(expected, live));
    }

    @Test
    void reportsMissingDeclaredField() throws Exception {
        var expected = mapper.readTree("""
                {"concept_semantic":{"type":"semantic_text"}}
                """);
        var live = mapper.readTree("""
                {"version":{"type":"text"}}
                """);

        assertTrue(ElasticSearchIndexService.mappingPropertiesOutdated(expected, live));
    }

    @Test
    void reportsDifferentDeclaredFieldType() throws Exception {
        var expected = mapper.readTree("""
                {"concept_semantic":{"type":"semantic_text"}}
                """);
        var live = mapper.readTree("""
                {"concept_semantic":{"type":"text"}}
                """);

        assertTrue(ElasticSearchIndexService.mappingPropertiesOutdated(expected, live));
    }

    @Test
    void checksMultiFieldsRecursively() throws Exception {
        var expected = mapper.readTree("""
                {"label":{"type":"text","fields":{"keyword":{"type":"keyword"}}}}
                """);
        var matchingLive = mapper.readTree("""
                {"label":{"type":"text","fields":{"keyword":{"type":"keyword"}}}}
                """);
        var mismatchedLive = mapper.readTree("""
                {"label":{"type":"text","fields":{"keyword":{"type":"text"}}}}
                """);

        assertFalse(ElasticSearchIndexService.mappingPropertiesOutdated(expected, matchingLive));
        assertTrue(ElasticSearchIndexService.mappingPropertiesOutdated(expected, mismatchedLive));
    }
}
