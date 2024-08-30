package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VocabServiceTest extends BaseTestClass {

    @Autowired
    VocabService vocabServiceImpl;

//    @BeforeEach
//    public void setup() throws IOException {
//        vocabServiceImpl = mockVocabServiceWithData();
//    }

    @Test
    void testGetDiscoveryParameterVocabs() throws IOException {
        // Prepare themes
        List<ThemesModel> themes = List.of(
                new ThemesModel(Arrays.asList(
                        new ConceptModel("Temperature of the water body", "http://vocab.nerc.ac.uk/collection/P01/current/TEMPPR01"),
                        new ConceptModel("Practical salinity of the water body", "http://vocab.nerc.ac.uk/collection/P01/current/PSLTZZ01"),
                        new ConceptModel("Concentration of carbon (total inorganic) per unit mass of the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/1"),
                        new ConceptModel("Total alkalinity per unit mass of the water body", "http://vocab.nerc.ac.uk/collection/P01/current/MDMAP014"),
                        new ConceptModel("Saturation state of aragonite in the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/24"),
                        new ConceptModel("Saturation state of aragonite in the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/24"),
                        new ConceptModel("pH (total scale) of the water body", "http://vocab.aodn.org.au/def/discovery_parameter/entity/27")
                ), "theme", null, "AODN Discovery Parameter Vocabulary")
        );

        // Perform the test
        List<String> parameterVocabs = vocabServiceImpl.extractVocabLabelsFromThemes(themes, AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS);

        // Assertions
        assertNotNull(parameterVocabs);
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("alkalinity")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("temperature")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("salinity")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("carbon")));
        assertEquals(4, parameterVocabs.size());
    }

    @Test
    void testGetPlatformVocabs() throws IOException {
        // Prepare themes
        List<ThemesModel> themes = List.of(
                new ThemesModel(Arrays.asList(
                        new ConceptModel("Lizard Island Base Station", "http://vocab.aodn.org.au/def/platform/entity/308"),
                        new ConceptModel("Orpheus Island Base Station", "http://vocab.aodn.org.au/def/platform/entity/315"),
                        new ConceptModel("OOCL Panama", "http://vocab.aodn.org.au/def/platform/entity/1"),
                        new ConceptModel("Wana Bhum", "http://vocab.nerc.ac.uk/collection/P01/current/MDMAP014"),
                        new ConceptModel("Lizard Island Sensor Float 4", "http://vocab.aodn.org.au/def/platform/entity/314"),
                        new ConceptModel("Bateman's Marine Park 120m Mooring", "http://vocab.aodn.org.au/def/platform/entity/121"),
                        new ConceptModel("NEMO Argo Float", "http://vocab.aodn.org.au/def/platform/entity/296")
                ), "theme", null, "AODN Platform Vocabulary")
        );

        // Perform the test
        List<String> platformVocabs = vocabServiceImpl.extractVocabLabelsFromThemes(themes, AppConstants.AODN_PLATFORM_VOCABS);

        // Assertions
        assertNotNull(platformVocabs);
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("land/onshore structure")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("vessel of opportunity")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("moored surface buoy")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("mooring")));
        assertTrue(platformVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("drifting subsurface profiling float")));
        assertEquals(5, platformVocabs.size());
    }

    @Test
    void testOrganisationVocabs() throws IOException {
        // Prepare themes
        List<ThemesModel> themes = List.of(
                new ThemesModel(Arrays.asList(
                        new ConceptModel("Hornsby Shire Council", "http://vocab.aodn.org.au/def/organisation/entity/408"),
                        new ConceptModel("Department of Environment and Natural Resources (DENR), Northern Territory Government", "http://vocab.aodn.org.au/def/organisation/entity/487"),
                        new ConceptModel("Parks Australia, Department of Agriculture, Water and the Environment (DAWE), Australian Government", "http://vocab.aodn.org.au/def/organisation/entity/637"),
                        new ConceptModel("Bureau of Meteorology (BOM), Department of Environment (DoE), Australian Government", "http://vocab.aodn.org.au/def/organisation/entity/11")
                ), "theme", null, "AODN Organisation Vocabulary")
        );

        // Perform the test
        List<String> organisationVocabs = vocabServiceImpl.extractVocabLabelsFromThemes(themes, AppConstants.AODN_ORGANISATION_VOCABS);

        // Assertions
        assertNotNull(organisationVocabs);
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("New South Wales Councils")));
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("Northern Territory Government")));
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("Department of Agriculture, Water and the Environment (DAWE), Australian Government")));
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("Department of the Environment (DoE), Australian Government")));
        assertEquals(4, organisationVocabs.size());
    }
}
