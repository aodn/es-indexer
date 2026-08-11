package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.exception.VocabHarvestIncompleteException;
import au.org.aodn.ardcvocabs.model.ArdcCurrentPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.Application;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.exception.IgnoreIndexingVocabsException;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// JSONAssert is a useful dependency for comparing JSON values, replacing the traditional string-to-string approach when dealing with JSON.
// More details: https://www.baeldung.com/jsonassert#overview, https://github.com/skyscreamer/JSONassert
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VocabServiceIT extends BaseTestClass {

    @InjectMocks
    @Spy
    VocabServiceImpl mockVocabService;

    @Autowired
    VocabService vocabService;

    @Mock
    ArdcVocabService mockArdcVocabService;

    @Autowired
    ArdcVocabService ardcVocabService;

    @Autowired
    protected ObjectMapper indexerObjectMapper;

    @Autowired
    protected ElasticSearchIndexService elasticSearchIndexService;

    @Value("${elasticsearch.vocabs_index.name}")
    protected String vocabsIndexName;

    /**
     * This class is PER_CLASS, so Spring's MockitoTestExecutionListener initialises the annotated mocks once for
     * the whole class rather than per method. Without an explicit reset, stubs and invocation counts leak between
     * tests: a thenThrow stub left by one test fires while the next test is still stubbing, and a spy invocation
     * from one test breaks another test's verify(..., never()).
     */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(mockVocabService, mockArdcVocabService);
    }

    @Test
    void testExtractParameterVocabLabelsFromThemes() throws IOException {
        // Prepare themes
        List<ThemesModel> themes = List.of(
                new ThemesModel(Arrays.asList(
                        new ConceptModel("Temperature of the water body", "http://vocab.nerc.ac.uk/collection/P01/current/TEMPPR01", "AODN Discovery Parameter Vocabulary", null, null),
                        new ConceptModel("Practical salinity of the water body", "http://vocab.nerc.ac.uk/collection/P01/current/PSLTZZ01", "AODN Discovery Parameter Vocabulary", null, null),
                        new ConceptModel("Concentration of carbon (total inorganic) per unit mass of the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/1", "AODN Discovery Parameter Vocabulary", null, null),
                        new ConceptModel("Total alkalinity per unit mass of the water body", "http://vocab.nerc.ac.uk/collection/P01/current/MDMAP014", "AODN Discovery Parameter Vocabulary", null, null),
                        new ConceptModel("Saturation state of aragonite in the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/24", "AODN Discovery Parameter Vocabulary", null, null),
                        new ConceptModel("Saturation state of aragonite in the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/24", "AODN Discovery Parameter Vocabulary", null, null),
                        new ConceptModel("pH (total scale) of the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/27", "AODN Discovery Parameter Vocabulary", null, null)
                ), "theme")
        );

        // Perform the test
        Set<String> parameterVocabs = vocabService.extractVocabLabelsFromThemes(themes, VocabService.VocabType.AODN_DISCOVERY_PARAMETER_VOCABS, false);

        // Assertions
        assertNotNull(parameterVocabs);
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("alkalinity")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("temperature")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("salinity")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("carbon")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("pH (total scale) of the water body")));
        assertEquals(5, parameterVocabs.size());
    }
    /**
     * Verify platform vocab works correctly, the platform vocab share the same logic that it extract the second level vocab however
     * it add additional level 1 vocab to the result. That is result contains level 1 and level 2, because
     * the filter for platform applies to level 1 not level 2, so we need these values appear in order to complete the search
     * @throws IOException - Not expected
     */
    @Test
    void testExtractPlatformVocabLabelsFromThemes() throws IOException {
        // Prepare themes
        List<ThemesModel> themes = List.of(
                new ThemesModel(Arrays.asList(
                        new ConceptModel("Lizard Island Base Station", "http://vocab.aodn.org.au/def/platform/entity/308", "AODN Platform Vocabulary", null, null),
                        new ConceptModel("Orpheus Island Base Station", "http://vocab.aodn.org.au/def/platform/entity/315", "AODN Platform Vocabulary", null, null),
                        new ConceptModel("OOCL Panama", "http://vocab.aodn.org.au/def/platform/entity/1", "AODN Platform Vocabulary", null, null),
                        new ConceptModel("Wana Bhum", "http://vocab.nerc.ac.uk/collection/P01/current/MDMAP014", "AODN Platform Vocabulary", null, null),
                        new ConceptModel("Lizard Island Sensor Float 4", "http://vocab.aodn.org.au/def/platform/entity/314", "AODN Platform Vocabulary", null, null),
                        new ConceptModel("Bateman's Marine Park 120m Mooring", "http://vocab.aodn.org.au/def/platform/entity/121", "AODN Platform Vocabulary", null, null),
                        new ConceptModel("NEMO Argo Float", "http://vocab.aodn.org.au/def/platform/entity/296", "AODN Platform Vocabulary", null, null)
                ), "theme")
        );

        // Perform the test
        Set<String> platformVocabs = vocabService.extractVocabLabelsFromThemes(themes, VocabService.VocabType.AODN_PLATFORM_VOCABS, true);

        // Assertions
        assertNotNull(platformVocabs);
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("land/onshore structure")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("vessel of opportunity")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("moored surface buoy")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("mooring")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("drifting subsurface profiling float")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("vessel")));            // Level 1
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("float")));             // Level 1
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("fixed station")));     // Level 1
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("mooring and buoy")));  // Level 1
        assertEquals(9, platformVocabs.size());
    }

    @Test
    void testProcessParameterVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> parameterVocabsFromArdc = ardcVocabService.getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);

        // read from Elastic search
        List<JsonNode> parameterVocabsFromEs = vocabService.getParameterVocabs();
        assertNotNull(parameterVocabsFromEs);
        assertEquals(parameterVocabsFromEs.size(), parameterVocabsFromArdc.size());

        JSONAssert.assertEquals(
                indexerObjectMapper.valueToTree(parameterVocabsFromEs).toPrettyString(),
                indexerObjectMapper.valueToTree(parameterVocabsFromArdc).toPrettyString(),
                JSONCompareMode.STRICT
        );
    }
    /**
     * Test to verify update skip if nothing return from source
     * @throws IOException - Not expected
     */
    @Test
    void testSkipIndexingIfEmptyVocabs() throws IOException {
        // Mock service calls to return empty lists
        doReturn(Collections.emptyList()).when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);
        doReturn(Collections.emptyList()).when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);
        doReturn(Collections.emptyList()).when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.ORGANISATION_VOCAB);

        assertThrows(IgnoreIndexingVocabsException.class, () -> mockVocabService.populateVocabsData());
        verify(mockVocabService, never()).indexAllVocabs(anyList(), anyList(), anyList());
    }

    @Test
    void testIncompleteHarvestIsNotIndexedSynchronously() throws IOException {
        // doThrow rather than when(...).thenThrow: the latter invokes the mock while stubbing it
        doThrow(incompleteHarvestException())
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);

        assertThrows(VocabHarvestIncompleteException.class, () -> mockVocabService.populateVocabsData());
        verify(mockVocabService, never()).indexAllVocabs(anyList(), anyList(), anyList());
        verify(mockArdcVocabService, never()).getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);
    }

    @Test
    void testIncompleteHarvestIsNotIndexedAsynchronously() throws IOException {
        doThrow(incompleteHarvestException())
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);
        doReturn(List.of(vocab("Platform", "http://example.com/platform")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);
        doReturn(List.of(vocab("Organisation", "http://example.com/organisation")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.ORGANISATION_VOCAB);

        CompletableFuture<Void> future = mockVocabService.populateVocabsDataAsync(0);

        // The async loop replaces a failed task with an empty list, so the future loses the harvest's root cause.
        ExecutionException thrown = assertThrows(
                ExecutionException.class,
                () -> future.get(30, TimeUnit.SECONDS));
        assertInstanceOf(IgnoreIndexingVocabsException.class, thrown.getCause());
        verify(mockVocabService, never()).indexAllVocabs(anyList(), anyList(), anyList());
    }

    @Test
    void testAsyncHarvestsRunSequentiallyInOrder() throws Exception {
        doReturn(List.of(vocab("Parameter", "http://example.com/parameter")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);
        doReturn(List.of(vocab("Platform", "http://example.com/platform")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);
        doReturn(List.of(vocab("Organisation", "http://example.com/organisation")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.ORGANISATION_VOCAB);
        doNothing().when(mockVocabService).indexAllVocabs(anyList(), anyList(), anyList());

        mockVocabService.populateVocabsDataAsync(0).get(30, TimeUnit.SECONDS);

        // InOrder proves invocation order, not non-overlap; instant stubs cannot reliably expose concurrency.
        InOrder inOrder = inOrder(mockArdcVocabService);
        inOrder.verify(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);
        inOrder.verify(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);
        inOrder.verify(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.ORGANISATION_VOCAB);
        verify(mockVocabService, times(1)).indexAllVocabs(anyList(), anyList(), anyList());
    }

    @Test
    void testProcessPlatformVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> platformVocabsFromArdc = ardcVocabService.getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);

        // read from Elastic search
        List<JsonNode> platformVocabsFromEs = vocabService.getPlatformVocabs();
        assertNotNull(platformVocabsFromEs);
        assertEquals(platformVocabsFromEs.size(), platformVocabsFromArdc.size());

        JSONAssert.assertEquals(
                indexerObjectMapper.valueToTree(platformVocabsFromEs).toPrettyString(),
                indexerObjectMapper.valueToTree(platformVocabsFromArdc).toPrettyString(),
                JSONCompareMode.STRICT);
    }

    @Test
    void testProcessOrganisationVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> organisationVocabsFromArdc = ardcVocabService.getARDCVocabByType(ArdcCurrentPaths.ORGANISATION_VOCAB);

        // read from Elastic search
        List<JsonNode> organisationVocabsFromEs = vocabService.getOrganisationVocabs();
        assertNotNull(organisationVocabsFromEs);
        assertEquals(organisationVocabsFromEs.size(), organisationVocabsFromArdc.size());

        JSONAssert.assertEquals(
                indexerObjectMapper.valueToTree(organisationVocabsFromEs).toPrettyString(),
                indexerObjectMapper.valueToTree(organisationVocabsFromArdc).toPrettyString(),
                JSONCompareMode.STRICT
        );
    }

    /**
     * The configured vocabs index name must be an alias over a blue/green colour, never a concrete
     * index, otherwise a rebuild would delete the data that is currently being served.
     */
    @Test
    void testVocabsIndexNameIsAnAliasOverAColour() throws IOException {
        var currentIndex = elasticSearchIndexService.getIndexNameFromAlias(vocabsIndexName);

        assertNotNull(currentIndex, vocabsIndexName + " should be an alias after populating vocabs");
        assertTrue(
                currentIndex.equals(vocabsIndexName + "-blue") || currentIndex.equals(vocabsIndexName + "-green"),
                "Alias should point at a blue/green index but points at " + currentIndex);
        // reads keep working through the alias
        assertFalse(vocabService.getParameterVocabs().isEmpty());
    }

    /**
     * A rebuild goes into the other colour and only then moves the alias, so the previous index is a
     * live backup for the whole run. Once the swap is done the superseded colour is cleaned up.
     */
    @Test
    void testRepopulateSwapsToTheOtherColour() throws IOException {
        var indexBefore = elasticSearchIndexService.getIndexNameFromAlias(vocabsIndexName);
        assertNotNull(indexBefore);

        vocabService.populateVocabsData();

        var indexAfter = elasticSearchIndexService.getIndexNameFromAlias(vocabsIndexName);
        assertNotNull(indexAfter);
        assertNotEquals(indexBefore, indexAfter, "Rebuild should have swapped to the other colour");
        assertFalse(
                client.indices().exists(e -> e.index(indexBefore)).value(),
                "Superseded index " + indexBefore + " should be deleted after the alias switch");
        assertTrue(elasticSearchIndexService.getDocumentsCount(vocabsIndexName) > 0);
    }

    /**
     * Regression test for the 429 Too Many Requests bug: ARDC rate limiting truncated the harvest,
     * so every platform root came back with no narrower terms. That tree is non-empty, so a size check
     * passes, but it is useless for record indexing - and the old index had already been deleted.
     * Now the flat tree is rejected and the existing index keeps serving.
     */
    @Test
    void testFlatHarvestKeepsTheExistingVocabsIndex() throws IOException {
        var indexBefore = elasticSearchIndexService.getIndexNameFromAlias(vocabsIndexName);
        var docCountBefore = elasticSearchIndexService.getDocumentsCount(vocabsIndexName);
        assertNotNull(indexBefore);
        assertTrue(docCountBefore > 0);

        doReturn(List.of(vocabWithChild("Parameter", "http://example.com/parameter")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PARAMETER_VOCAB);
        // truncated harvest: roots present, no children
        doReturn(List.of(vocab("Platform", "http://example.com/platform")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.PLATFORM_VOCAB);
        doReturn(List.of(vocabWithChild("Organisation", "http://example.com/organisation")))
                .when(mockArdcVocabService).getARDCVocabByType(ArdcCurrentPaths.ORGANISATION_VOCAB);

        assertThrows(IgnoreIndexingVocabsException.class, () -> mockVocabService.populateVocabsData());

        assertEquals(indexBefore, elasticSearchIndexService.getIndexNameFromAlias(vocabsIndexName),
                "Alias should not have moved after a flat harvest");
        assertEquals(docCountBefore, elasticSearchIndexService.getDocumentsCount(vocabsIndexName),
                "Existing vocabs should be untouched after a flat harvest");
    }

    private VocabHarvestIncompleteException incompleteHarvestException() {
        return new VocabHarvestIncompleteException(
                "https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-platform-vocabulary/version-6-1/concept.json",
                HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        HttpHeaders.EMPTY,
                        null,
                        null));
    }

    private VocabModel vocab(String label, String about) {
        return VocabModel.builder()
                .label(label)
                .about(about)
                .build();
    }

    /** A root with a child, i.e. what a complete harvest looks like. */
    private VocabModel vocabWithChild(String label, String about) {
        return VocabModel.builder()
                .label(label)
                .about(about)
                .narrower(List.of(vocab(label + " child", about + "/1")))
                .build();
    }
}
