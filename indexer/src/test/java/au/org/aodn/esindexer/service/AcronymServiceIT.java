package au.org.aodn.esindexer.service;

import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.esindexer.Application;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import au.org.aodn.esindexer.controller.IndexerController;
import au.org.aodn.esindexer.model.MockServer;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Acronym synonyms end-to-end: the manual rules from config (elasticsearch.acronyms.manual) flow
 * into the ES synonyms set and expand at search time. The test manual rules are "aa => aurora
 * australis", "soop => ships of opportunity", and the one-acronym-two-names pair "ams => australian
 * marine sciences" / "ams => antarctic meteorological service" (see application-test.yaml).
 * The index (with the acronym schema and synonyms set) is built once in {@link #setup()}. Most tests
 * are read-only over it; the document-search test reindexes itself so suite-level mock pollution
 * cannot leave the portal index empty. {@link #cleanUp()} tears it down at the end.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AcronymServiceIT extends BaseTestClass {

    // sample12.xml's title/description say "Aurora Australis" but never the token "aa".
    private static final String ACRONYM_SAMPLE_UUID = "201112060";

    @Autowired
    protected GeoNetworkServiceImpl geoNetworkService;

    // Declared as a spy (not a plain @Autowired) so this class's context matches IndexerServiceIT's
    // bean-override set exactly, letting Spring reuse the same cached context (and its docker stack)
    // instead of starting another GeoNetwork + Elasticsearch set just for this class.
    @MockitoSpyBean
    protected IndexerMetadataServiceImpl indexerService;

    @Autowired
    protected IndexerController indexerController;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @Autowired
    protected MockServer mockServer;

    @MockitoBean
    protected DataDiscoveryAiService dataDiscoveryAiService;

    // ---- schema setup: build the index (mapping + analyser + synonyms set) once, tear it down at the end ----

    /** Build the index once: point at GeoNetwork, insert sample12, then reindex (which syncs the synonyms set). */
    @BeforeAll
    public void setup() throws IOException {
        geoNetworkService.setServer(String.format("http://%s:%s",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT))
        );
        // Restore DAS 404 defaults in case an earlier IT wiped them with getServer().reset().
        mockServer.resetToDefault();
        insertMetadataRecords(ACRONYM_SAMPLE_UUID, "classpath:canned/sample12.xml");
        indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
    }

    @AfterAll
    public void cleanUp() throws IOException {
        clearElasticIndex(INDEX_NAME);
        deleteRecord(ACRONYM_SAMPLE_UUID);
        mockServer.resetToDefault();
    }

    // ---- rule output: the rules built from vocab + manual, via the read-only preview endpoint ----

    /** Preview is read-only: it lists the rules that would be pushed, including the manual ones from config. */
    @Test
    public void previewIncludesManualAcronymRules() throws IOException {
        var preview = indexerController.previewAcronyms().getBody();

        Assertions.assertNotNull(preview, "preview body should not be null");
        Assertions.assertEquals(preview.rules().size(), preview.count(), "count should match the rule list size");
        Assertions.assertTrue(preview.rules().contains("aa => aurora australis"),
                "preview should include the manual rule for 'aa'; got " + preview.rules());
        Assertions.assertTrue(preview.rules().contains("soop => ships of opportunity"),
                "preview should include the manual rule for 'soop'; got " + preview.rules());
    }

    /** Current shows what is live in the synonyms set after the reindex, including the manual rules from config. */
    @Test
    public void currentReflectsTheLiveSynonymsSet() throws IOException {
        var current = indexerController.currentAcronyms().getBody();

        Assertions.assertNotNull(current, "current body should not be null");
        Assertions.assertEquals(current.rules().size(), current.count(), "count should match the rule list size");
        Assertions.assertTrue(current.rules().contains("aa => aurora australis"),
                "current should include the manual rule for 'aa'; got " + current.rules());
        Assertions.assertTrue(current.rules().contains("soop => ships of opportunity"),
                "current should include the manual rule for 'soop'; got " + current.rules());
        Assertions.assertNotNull(current.lastModified(),
                "the reindex in setup() should have stamped a last-modified date");
        // Guard the format: it must be a parseable offset date-time (zone/format is a deliberate choice).
        Assertions.assertDoesNotThrow(() -> OffsetDateTime.parse(current.lastModified()),
                "lastModified should be an ISO offset date-time; got " + current.lastModified());
    }

    /** Sync reports the real outcome: the number of rules pushed, not a blanket "updated". */
    @Test
    public void syncReportsTheNumberOfRulesPushed() throws IOException {
        var result = indexerController.syncAcronyms().getBody();

        Assertions.assertNotNull(result, "sync body should not be null");
        Assertions.assertNotNull(result.synonymSetName(), "sync should report which synonym set was pushed");
        Assertions.assertTrue(result.count() > 0, "the manual rules alone should push at least one rule");
        Assertions.assertTrue(result.message().contains("updated with " + result.count() + " rules"),
                "sync message should report the rule count; got " + result.message());
    }

    // ---- search behaviour: the acronym expands at search time and matches the indexed document ----

    /**
     * Searching the acronym "aa" finds a record that only says "aurora australis" — proof of search-time expansion.
     * <p>
     * Self-contained: reindexes after restoring DAS defaults via {@link MockServer#resetToDefault()}, so
     * suite-level {@code getServer().reset()} pollution cannot leave the portal index empty.
     */
    @Test
    public void acronymQueryExpandsToFullNameAndMatchesDocument() throws IOException {
        mockServer.resetToDefault();
        insertMetadataRecords(ACRONYM_SAMPLE_UUID, "classpath:canned/sample12.xml");
        indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);

        var hits = searchAcronymField("aa");

        Assertions.assertFalse(hits.isEmpty(),
                "'aa' should expand to 'aurora australis' and match the record");
    }

    /** "soop" must still expand, even though its full name "ships of opportunity" contains a stopword ("of"). */
    @Test
    public void acronymWithStopwordInExpansionStillExpands() throws IOException {
        var tokens = analyzeTokens("soop");

        Assertions.assertTrue(tokens.contains("ships") && tokens.contains("opportunity"),
                "'soop' should expand to 'ships of opportunity'; got " + tokens);
    }

    /** One acronym, two full names: "ams" expands to BOTH at search time, so both full names' tokens appear. */
    @Test
    public void acronymWithMultipleFullNamesExpandsToAll() throws IOException {
        var tokens = analyzeTokens("ams");

        Assertions.assertTrue(tokens.contains("marine") && tokens.contains("meteorological"),
                "'ams' should expand to both 'australian marine sciences' and 'antarctic meteorological service'; got " + tokens);
    }

    // ---- helpers ----

    /** Search the given term against the acronym-aware title.synonyms sub-field. */
    private List<Hit<ObjectNode>> searchAcronymField(String term) throws IOException {
        return client.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q.match(m -> m.field("title.synonyms").query(term))), ObjectNode.class)
                .hits().hits();
    }

    /** Run the given text through the acronym_search_analyser and return the resulting tokens. */
    private List<String> analyzeTokens(String text) throws IOException {
        AnalyzeRequest request = AnalyzeRequest.of(a -> a
                .index(INDEX_NAME)
                .analyzer("acronym_search_analyser")
                .text(text));
        return client.indices().analyze(request).tokens().stream()
                .map(AnalyzeToken::token)
                .toList();
    }
}
