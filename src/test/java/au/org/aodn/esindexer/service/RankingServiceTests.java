package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.model.ContactsModel;
import au.org.aodn.esindexer.model.ExtentModel;
import au.org.aodn.esindexer.model.LinkModel;
import au.org.aodn.esindexer.model.StacCollectionModel;
import au.org.aodn.esindexer.model.ThemesModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

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

    @AfterAll
    public void clear() throws IOException {
        super.clearElasticIndex();
    }

    @Spy
    @InjectMocks
    RankingServiceImpl mockRankingService;

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
        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);
        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(0, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testTitleFound() {
        // arrange
        stacCollectionModel.setTitle("Test");
        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);
        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(15, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testDescriptionFound() {
        // arrange
        stacCollectionModel.setDescription("Test");
        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);
        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(15, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testExtentFound() {
        // arrange
        List<List<BigDecimal>> bbox = new ArrayList<>();
        List<BigDecimal> bigDecimalList1 = new ArrayList<>();
        bigDecimalList1.add(new BigDecimal(1));
        bigDecimalList1.add(new BigDecimal(2));
        bigDecimalList1.add(new BigDecimal(3));
        bbox.add(bigDecimalList1);
        extentModel.setBbox(bbox);
        stacCollectionModel.setExtent(extentModel);

        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);

        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(10, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testTemporalFound() {
        // arrange
        List<String[]> temporal = new ArrayList<>();
        String[] temporal1 = new String[2];
        temporal1[0] = "2020-01-01";
        temporal1[1] = "2020-01-02";
        temporal.add(temporal1);

        extentModel.setTemporal(temporal);
        stacCollectionModel.setExtent(extentModel);

        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);

        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(10, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testContactsFound() {
        // arrange
        ContactsModel contact1 = ContactsModel.builder().build();
        contact1.setName("Test");
        List<ContactsModel> contacts = new ArrayList<>();
        contacts.add(contact1);
        stacCollectionModel.setContacts(contacts);

        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);

        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(10, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testLinksFound() {
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

        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);

        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(15, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }

    @Test
    public void testThemesFound() {
        // arrange
        List<ThemesModel> themes = new ArrayList<>();
        ThemesModel theme1 = ThemesModel.builder().build();
        theme1.setTitle("Test");
        themes.add(theme1);

        ThemesModel theme2 = ThemesModel.builder().build();
        theme2.setTitle("Test 2");
        themes.add(theme2);

        stacCollectionModel.setThemes(themes);

        // act
        mockRankingService.evaluateCompleteness(stacCollectionModel);

        // assert
        verify(mockRankingService, times(1)).evaluateCompleteness(stacCollectionModel);
        assertEquals(10, mockRankingService.evaluateCompleteness(stacCollectionModel));
    }
}

