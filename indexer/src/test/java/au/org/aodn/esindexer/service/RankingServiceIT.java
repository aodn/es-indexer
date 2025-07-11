package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
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
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    private ExtentModel extentModel;

    @BeforeEach
    public void setUp() {
        stacCollectionModel = StacCollectionModel.builder().build();
        extentModel = ExtentModel.builder().build();
        stacCollectionModel.setExtent(extentModel);
    }

    @Test
    public void testNotFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // assert
        assertEquals(0, mockRankingService.evaluateCompleteness(stacCollectionModel));
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testDescriptionFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // arrange
        stacCollectionModel.setDescription("The Cape Grim Baseline Air Pollution Station facility, located at the North/West tip of Tasmania (40� 41'S, 144� 41'E), is funded and managed by the Australian Bureau of Meteorology, with the scientific program being jointly supervised with CSIRO Marine and Atmospheric Research. This archive contains 1000 litre air samples contained in stainless steel flasks collected at approximately 3 monthly intervals since 1978. The archive is housed at the Aspendale laboratory of CSIRO Marine and Atmospheric Research. The Cape Grim air archive is invaluable in determining the past atmospheric composition of a wide range of gases. For some of these gases, accurate and precise analytical methods have only recently evolved (for example HFCs and PFCs). The measurements are state-of-the-art in precision and accuracy. They are used to identify trace gas trends in the Southern Hemisphere, which in turn can be used to drive climate change models and identify processes that influence changes to the atmosphere.");
        // assert
        assertEquals(11, mockRankingService.evaluateCompleteness(stacCollectionModel));
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

        assertEquals(16, mockRankingService.evaluateCompleteness(stacCollectionModel));
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
        assertEquals(mockRankingService.linkMinWeigth + 1, mockRankingService.evaluateCompleteness(stacCollectionModel));
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
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
        assertEquals(mockRankingService.lineageWeigth + 1, mockRankingService.evaluateCompleteness(stacCollectionModel));
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }
}
