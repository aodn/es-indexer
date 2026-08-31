package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.Application;
import au.org.aodn.esindexer.BaseTestClass;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlighterOrder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static au.org.aodn.esindexer.utils.CommonUtils.persevere;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ELSER inference against the real Testcontainers Elasticsearch (trial licence), not a mock client.
 * Vocabs are already bulk-indexed with {@code concept_semantic} in {@link BaseTestClass#init()}.
 * The query and highlight extraction match ogcapi autocomplete {@code suggested_semantic}.
 * This is just a minimal test, as the real test is in ogcapi because all queries happen there, here just
 * to check ELSER is working.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InferenceIT extends BaseTestClass {

    private static final String SEMANTIC_CONCEPT_FIELD = "concept_semantic";
    private static final String ORGANISATION_VOCAB_FIELD = "organisation_vocab";
    // A phrasing that is not a vocab label, so a hit is from meaning rather than a lexical match.
    private static final String AUTOCOMPLETE_INPUT = "how warm is the sea";

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabsIndexName;

    /**
     * Autocomplete surfaces vocab terms ranked by meaning. A query about sea warmth should
     * return temperature-related parameter vocabs via {@code concept_semantic}.
     */
    @Test
    void semanticSearchResultsAppearInAutocomplete() throws IOException {
        persevere(20, 3, this::semanticSuggestionsReady);

        Set<String> suggestions = searchSemanticSuggestions(AUTOCOMPLETE_INPUT);

        assertFalse(suggestions.isEmpty(),
                "semantic autocomplete should return suggested_semantic labels for '" + AUTOCOMPLETE_INPUT + "'");
        assertTrue(
                suggestions.stream().anyMatch(label -> label.toLowerCase().contains("temperature")),
                "expected a temperature-related vocab in suggested_semantic; got " + suggestions);
    }

    private boolean semanticSuggestionsReady() {
        try {
            return !searchSemanticSuggestions(AUTOCOMPLETE_INPUT).isEmpty();
        } catch (Exception e) {
            logger.warn("ELSER inference not ready yet: {}", e.getMessage());
            return false;
        }
    }
    /**
     * Same request ogcapi uses in {@code ElasticSearch#getSemanticTermHits}: semantic query on
     * vocabs {@code concept_semantic}, organisation vocabs excluded, highlights ordered by score.
     */
    private Set<String> searchSemanticSuggestions(String input) throws IOException {
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(vocabsIndexName)
                .size(3)
                .query(q -> q.bool(b -> b
                        .must(m -> m.semantic(sm -> sm
                                .field(SEMANTIC_CONCEPT_FIELD)
                                .query(input)))
                        .mustNot(mn -> mn.exists(e -> e.field(ORGANISATION_VOCAB_FIELD)))))
                .highlight(h -> h
                        .fields(SEMANTIC_CONCEPT_FIELD, f -> f
                                .numberOfFragments(3)
                                .order(HighlighterOrder.Score)
                                .preTags("")
                                .postTags(""))));

        SearchResponse<JsonNode> response = client.search(searchRequest, JsonNode.class);

        return response.hits().hits()
                .stream()
                .flatMap(hit -> extractSemanticLabels(hit).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> extractSemanticLabels(Hit<JsonNode> hit) {
        List<String> fragments = hit.highlight() == null
                ? null
                : hit.highlight().get(SEMANTIC_CONCEPT_FIELD);

        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }
        return fragments.stream()
                .map(this::toConceptLabel)
                .filter(label -> label != null && !label.isBlank())
                .toList();
    }

    private String toConceptLabel(String fragment) {
        if (fragment == null) {
            return null;
        }
        int end = fragment.indexOf(". ");
        return (end >= 0 ? fragment.substring(0, end) : fragment).trim();
    }
}
