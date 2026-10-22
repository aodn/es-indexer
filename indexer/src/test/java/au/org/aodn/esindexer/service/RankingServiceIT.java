package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.Application;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.utils.FacilityRecordUtils;
import au.org.aodn.stac.model.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RankingServiceIT extends BaseTestClass {

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @AfterAll
    public void clear() throws IOException {
        super.clearElasticIndex(INDEX_NAME);
    }

    @Autowired
    RankingServiceImpl rankingService;

    private StacCollectionModel stacCollectionModel;

    @BeforeEach
    public void setUp() {
        stacCollectionModel = StacCollectionModel.builder().build();
        stacCollectionModel.setExtent(ExtentModel.builder().build());
    }

    @Test
    public void testNotFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // assert
        assertEquals(0, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testDescriptionFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // arrange
        stacCollectionModel.setDescription("The Cape Grim Baseline Air Pollution Station facility, located at the North/West tip of Tasmania (40� 41'S, 144� 41'E), is funded and managed by the Australian Bureau of Meteorology, with the scientific program being jointly supervised with CSIRO Marine and Atmospheric Research. This archive contains 1000 litre air samples contained in stainless steel flasks collected at approximately 3 monthly intervals since 1978. The archive is housed at the Aspendale laboratory of CSIRO Marine and Atmospheric Research. The Cape Grim air archive is invaluable in determining the past atmospheric composition of a wide range of gases. For some of these gases, accurate and precise analytical methods have only recently evolved (for example HFCs and PFCs). The measurements are state-of-the-art in precision and accuracy. They are used to identify trace gas trends in the Southern Hemisphere, which in turn can be used to drive climate change models and identify processes that influence changes to the atmosphere.");
        // assert
        assertEquals(11, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testLinksFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // arrange
        List<LinkModel> links = new ArrayList<>();
        LinkModel link1 = LinkModel.builder().build();
        link1.setHref("Test");
        LinkModel link2 = LinkModel.builder().build();
        link2.setHref("Test 2");
        LinkModel link3 = LinkModel.builder().build();
        link3.setHref("Test 3");
        LinkModel link4 = LinkModel.builder().build();
        link4.setHref("Test 4");

        links.add(link1);
        links.add(link2);
        links.add(link3);
        links.add(link4);

        stacCollectionModel.setLinks(links);

        assertEquals(16, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testThemesFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // arrange
        List<ThemesModel> themes = new ArrayList<>();
        ThemesModel theme1 = ThemesModel.builder().build();
        themes.add(theme1);

        ThemesModel theme2 = ThemesModel.builder().build();
        themes.add(theme2);

        stacCollectionModel.setThemes(themes);

        // assert, because only 1 field found, so we add 1 to the weight
        assertEquals(mockRankingService.linkMinWeigth + 1, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testMixedThemesFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // 1 original keyword and 1 AI predicted keyword
        List<ThemesModel> themes = List.of(
                ThemesModel.builder()
                        .concepts(List.of(ConceptModel.builder().id("original-keyword").build()))
                        .build(),
                ThemesModel.builder()
                        .concepts(List.of(ConceptModel.builder()
                                .id("ai-keyword")
                                .aiDescription("ai predicted")
                                .build()))
                        .build()
        );
        stacCollectionModel.setThemes(themes);
        // 1 original theme → themeMinWeigth(10) + count(1) = 11
        assertEquals(mockRankingService.themeMinWeigth + 1, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
    }

    @Test
    public void testLinageFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // arrange
        stacCollectionModel.setSummaries(SummariesModel
                .builder()
                .statement("Statement")
                .build()
        );

        // assert, because only 1 field found, so we add 1 to the weight
        assertEquals(mockRankingService.lineageWeigth + 1, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testLineageBlankStatementNotCounted() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        stacCollectionModel.setSummaries(SummariesModel
                .builder()
                .statement("")   // blank lineage should not take into account
                .build()
        );
        assertEquals(0, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testImosOwnedRecord() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        stacCollectionModel.setSummaries(SummariesModel.builder()
                .datasetGroup(List.of("imos"))
                .build());
        assertEquals(mockRankingService.imosWeigth, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
    }

    @Test
    public void testCloudOptimisedDownloadService() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        stacCollectionModel.setAssets(Map.of(
                "cloud-optimised.parquet", AssetModel.builder().role(AssetModel.Role.SUMMARY).build()
        ));
        assertEquals(mockRankingService.cloudOptimizedWeigth, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
    }

    @Test
    public void testDownloadableLinks() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        List<LinkModel> links = new ArrayList<>();
        links.add(LinkModel.builder()
                .rel("related")
                .href("http://example.com/data")
                .aiGroup("Data Access")
                .aiRole(List.of("download"))
                .build());
        stacCollectionModel.setLinks(links);
        // 1 link → linkMinWeigth(10) + downloadableWeigth(10) + count(1) = 21
        assertEquals(mockRankingService.linkMinWeigth + mockRankingService.downloadableWeigth + 1,
                mockRankingService.evaluateCompleteness(stacCollectionModel).total());
    }


    @Test
    public void testSupersededPenalty() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        stacCollectionModel.setSummaries(SummariesModel.builder()
                .status("superseded")
                .build());
        // penalty -20
        assertEquals(mockRankingService.supersededPenalty, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
    }

    @Test
    public void testDocumentPenalty() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        stacCollectionModel.setSummaries(SummariesModel.builder()
                .scope(Map.of("code", "document"))
                .build());
        assertEquals(mockRankingService.documentPenalty, mockRankingService.evaluateCompleteness(stacCollectionModel).total());
    }

    @Test
    public void testFacilityPenalty() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // The three scope codes that identify a facility / sub-facility record
        for (String code : List.of("collectionHardware", "series", "collectionSession")) {
            stacCollectionModel.setSummaries(SummariesModel.builder()
                    .datasetGroup(List.of("IMOS"))
                    .scope(Map.of("code", code))
                    .build());
            // IMOS owned so it keeps the imos weight, then the facility penalty applies on top
            assertEquals(mockRankingService.imosWeigth + mockRankingService.facilityPenalty,
                    mockRankingService.evaluateCompleteness(stacCollectionModel).total(),
                    "Facility penalty applies to scope code " + code);
        }
    }

    @Test
    public void testPortalImosCollectionRecord() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        for (String category : FacilityRecordUtils.PORTAL_IMOS_CATEGORIES) {
            stacCollectionModel.setSummaries(SummariesModel.builder()
                    .datasetGroup(List.of("IMOS"))
                    .scope(Map.of("code", "dataset"))
                    .categories(List.of(category))
                    .build());
            assertEquals(mockRankingService.imosWeigth + mockRankingService.portalImosWeight,
                    mockRankingService.evaluateCompleteness(stacCollectionModel).total(),
                    "IMOS collection category " + category + " gets the portal IMOS weight");
        }
    }

    // ---- score_breakdown: the points each rule added, the values must add up to the score ----

    /**
     * Check what holds for any breakdown: every rule is listed, in the order of the Confluence ranking table,
     * and the values add up to the total.
     * @param score - The result to check
     * @return - The breakdown, for the caller to check single rules
     */
    private Map<String, Integer> assertBreakdownConsistent(RankingService.CompletenessScore score) {
        assertEquals(
                Arrays.stream(RankingService.Rule.values()).map(rule -> rule.key).toList(),
                new ArrayList<>(score.breakdown().keySet()),
                "Every rule is listed, in the ranking table order");
        assertEquals(
                score.total(),
                score.breakdown().values().stream().mapToInt(Integer::intValue).sum(),
                "The breakdown adds up to the score");
        return score.breakdown();
    }

    @Test
    public void testBreakdownNotFound() {
        RankingService.CompletenessScore score = rankingService.evaluateCompleteness(stacCollectionModel);
        Map<String, Integer> breakdown = assertBreakdownConsistent(score);

        assertTrue(breakdown.values().stream().allMatch(v -> v == 0), "No rule met, every rule is 0, got " + breakdown);
    }

    @Test
    public void testBreakdownDownloadableLinks() {
        stacCollectionModel.setLinks(List.of(LinkModel.builder()
                .rel("related")
                .href("http://example.com/data")
                .aiRole(List.of("download"))
                .build()));

        Map<String, Integer> breakdown = assertBreakdownConsistent(rankingService.evaluateCompleteness(stacCollectionModel));

        assertEquals(rankingService.linkMinWeigth, breakdown.get(RankingService.Rule.LINKS.key));
        assertEquals(rankingService.downloadableWeigth, breakdown.get(RankingService.Rule.DOWNLOADABLE_LINKS.key));
        assertEquals(1, breakdown.get(RankingService.Rule.METADATA_ELEMENTS.key), "Links is the only element found");
    }

    @Test
    public void testBreakdownPenaltiesAreNegative() {
        stacCollectionModel.setSummaries(SummariesModel.builder()
                .status("superseded")
                .scope(Map.of("code", "document"))
                .build());

        RankingService.CompletenessScore score = rankingService.evaluateCompleteness(stacCollectionModel);
        Map<String, Integer> breakdown = assertBreakdownConsistent(score);

        assertEquals(rankingService.supersededPenalty, breakdown.get(RankingService.Rule.OUT_OF_DATE.key));
        assertEquals(rankingService.documentPenalty, breakdown.get(RankingService.Rule.DOCUMENT.key));
        assertEquals(rankingService.supersededPenalty + rankingService.documentPenalty, score.total());
    }

    @Test
    public void testBreakdownFacilityRecord() {
        stacCollectionModel.setSummaries(SummariesModel.builder()
                .datasetGroup(List.of("IMOS"))
                .scope(Map.of("code", "series"))
                .build());

        Map<String, Integer> breakdown = assertBreakdownConsistent(rankingService.evaluateCompleteness(stacCollectionModel));

        assertEquals(rankingService.imosWeigth, breakdown.get(RankingService.Rule.IMOS_OWNED.key));
        assertEquals(rankingService.facilityPenalty, breakdown.get(RankingService.Rule.IMOS_FACILITY.key));
        assertEquals(0, breakdown.get(RankingService.Rule.IMOS_COLLECTION.key));
    }

    @Test
    public void testBreakdownPortalImosCollectionRecord() {
        for (String category : FacilityRecordUtils.PORTAL_IMOS_CATEGORIES) {
            stacCollectionModel.setSummaries(SummariesModel.builder()
                    .datasetGroup(List.of("IMOS"))
                    .scope(Map.of("code", "series"))
                    .categories(List.of(category))
                    .build());

            Map<String, Integer> breakdown = assertBreakdownConsistent(rankingService.evaluateCompleteness(stacCollectionModel));

            assertEquals(rankingService.imosWeigth, breakdown.get(RankingService.Rule.IMOS_OWNED.key));
            assertEquals(rankingService.portalImosWeight, breakdown.get(RankingService.Rule.IMOS_COLLECTION.key),
                    category + " is an IMOS collection category");
            assertEquals(0, breakdown.get(RankingService.Rule.IMOS_FACILITY.key), category + " means not a facility record");
        }
    }

    @Test
    public void testBreakdownFullRecord() {
        List<LinkModel> links = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            links.add(LinkModel.builder().href("http://example.com/" + i).build());
        }
        links.add(LinkModel.builder().href("http://example.com/wfs").aiRole(List.of("download")).build());

        stacCollectionModel.setThemes(List.of(
                ThemesModel.builder().build(),
                ThemesModel.builder().build(),
                ThemesModel.builder().build()));
        stacCollectionModel.setLicense("CC BY 4.0");
        stacCollectionModel.setCitation("Cite as IMOS");
        stacCollectionModel.setDescription("a".repeat(3000));
        stacCollectionModel.setLinks(links);
        stacCollectionModel.setAssets(Map.of(
                "cloud-optimised.parquet", AssetModel.builder().role(AssetModel.Role.SUMMARY).build()));
        stacCollectionModel.setSummaries(SummariesModel.builder()
                .statement("Statement")
                .datasetGroup(List.of("IMOS"))
                .scope(Map.of("code", "dataset"))
                .categories(List.of("portal:IMOS"))
                .build());

        RankingService.CompletenessScore score = rankingService.evaluateCompleteness(stacCollectionModel);
        Map<String, Integer> breakdown = assertBreakdownConsistent(score);

        assertEquals(rankingService.themeMidWeigth, breakdown.get(RankingService.Rule.KEYWORDS.key));
        assertEquals(rankingService.lineageWeigth, breakdown.get(RankingService.Rule.STATEMENT.key));
        assertEquals(rankingService.licenseWeigth, breakdown.get(RankingService.Rule.LICENSE.key));
        assertEquals(rankingService.citationWeigth, breakdown.get(RankingService.Rule.CITATION.key));
        assertEquals(25, breakdown.get(RankingService.Rule.ABSTRACT.key), "Abstract points are capped at 25");
        assertEquals(rankingService.linkMaxWeigth, breakdown.get(RankingService.Rule.LINKS.key));
        assertEquals(6, breakdown.get(RankingService.Rule.METADATA_ELEMENTS.key));
        assertEquals(rankingService.imosWeigth, breakdown.get(RankingService.Rule.IMOS_OWNED.key));
        assertEquals(rankingService.cloudOptimizedWeigth, breakdown.get(RankingService.Rule.CLOUD_OPTIMISED_DOWNLOAD.key));
        assertEquals(rankingService.downloadableWeigth, breakdown.get(RankingService.Rule.DOWNLOADABLE_LINKS.key));
        assertEquals(rankingService.portalImosWeight, breakdown.get(RankingService.Rule.IMOS_COLLECTION.key));
        assertEquals(0, breakdown.get(RankingService.Rule.OUT_OF_DATE.key));
        assertEquals(0, breakdown.get(RankingService.Rule.DOCUMENT.key));
        assertEquals(0, breakdown.get(RankingService.Rule.IMOS_FACILITY.key));
    }
}
