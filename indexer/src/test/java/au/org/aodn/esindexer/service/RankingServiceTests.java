package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.stac.model.ContactsModel;
import au.org.aodn.stac.model.ExtentModel;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.stac.model.ThemesModel;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RankingServiceTests extends BaseTestClass {

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
        stacCollectionModel.setDescription("Test");
        // assert
        assertEquals(15, mockRankingService.evaluateCompleteness(stacCollectionModel));
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

        // assert
        assertEquals(15, mockRankingService.evaluateCompleteness(stacCollectionModel));
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }

    @Test
    public void testThemesFound() {
        RankingServiceImpl mockRankingService = Mockito.spy(rankingService);
        // arrange
        List<ThemesModel> themes = new ArrayList<>();
        ThemesModel theme1 = ThemesModel.builder().build();
        theme1.setTitle("Test");
        themes.add(theme1);

        ThemesModel theme2 = ThemesModel.builder().build();
        theme2.setTitle("Test 2");
        themes.add(theme2);

        stacCollectionModel.setThemes(themes);

        // assert
        assertEquals(10, mockRankingService.evaluateCompleteness(stacCollectionModel));
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
    }
}
