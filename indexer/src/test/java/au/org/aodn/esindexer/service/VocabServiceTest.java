package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImpl;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImplTest;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.*;

// JSONAssert is a useful dependency for comparing JSON values, replacing the traditional string-to-string approach when dealing with JSON.
// More details: https://www.baeldung.com/jsonassert#overview, https://github.com/skyscreamer/JSONassert
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VocabServiceTest extends BaseTestClass {

    @Autowired
    VocabService vocabService;

    @Autowired
    ArdcVocabService ardcVocabService;

    @Autowired
    protected ObjectMapper indexerObjectMapper;

    @Test
    void testExtractParameterVocabLabelsFromThemes() throws IOException {
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
        List<String> parameterVocabs = vocabService.extractVocabLabelsFromThemes(themes, AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS);

        // Assertions
        assertNotNull(parameterVocabs);
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("alkalinity")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("temperature")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("salinity")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("carbon")));
        assertTrue(parameterVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("pH (total scale) of the water body")));
        assertEquals(5, parameterVocabs.size());
    }

    @Test
    void testExtractPlatformVocabLabelsFromThemes() throws IOException {
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
        List<String> platformVocabs = vocabService.extractVocabLabelsFromThemes(themes, AppConstants.AODN_PLATFORM_VOCABS);

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
    void testExtractPOrganisationVocabLabelsFromThemes() throws IOException {
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
        List<String> organisationVocabs = vocabService.extractVocabLabelsFromThemes(themes, AppConstants.AODN_ORGANISATION_VOCABS);

        // Assertions
        assertNotNull(organisationVocabs);
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("New South Wales Councils")));
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("Northern Territory Government")));
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("Department of Agriculture, Water and the Environment (DAWE), Australian Government")));
        assertTrue(organisationVocabs.stream().anyMatch(vocab -> vocab.equalsIgnoreCase("Department of the Environment (DoE), Australian Government")));
        assertEquals(4, organisationVocabs.size());
    }

    @Test
    void testProcessParameterVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> parameterVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PARAMETER_VOCAB);

        // verify the contents randomly
        assertNotNull(parameterVocabsFromArdc);

        assertTrue(parameterVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Physical-Atmosphere")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Air pressure")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Pressure (measured variable) exerted by the atmosphere")))));

        assertTrue(parameterVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Chemical")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Alkalinity")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Concentration of carbonate ions per unit mass of the water body")))));

        assertTrue(parameterVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Biological")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Ocean Biota")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Mean unit biovolume")))));

        assertTrue(parameterVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Physical-Water")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Wave")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Direction at spectral maximum of waves on the water body")))));


        // read from Elastic search
        List<JsonNode> parameterVocabsFromEs = vocabService.getParameterVocabs();
        assertNotNull(parameterVocabsFromEs);
        assertEquals(parameterVocabsFromEs.size(), parameterVocabsFromArdc.size());

        JSONAssert.assertEquals(indexerObjectMapper.valueToTree(parameterVocabsFromEs).toPrettyString(), indexerObjectMapper.valueToTree(parameterVocabsFromArdc).toPrettyString(), JSONCompareMode.LENIENT);

        // even more tests
        String cannedData = readResourceFile("classpath:canned/aodn_discovery_parameter_vocabs.json");
        // This will assert that all fields in cannedData are present in actual data,
        // but ignores any extra fields in actual data (scenario: source from ARDC is updated with more vocabs added)
        JSONAssert.assertEquals(cannedData, indexerObjectMapper.valueToTree(parameterVocabsFromEs).toPrettyString(), JSONCompareMode.LENIENT);
    }

    @Test
    void testProcessPlatformVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> platformVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PLATFORM_VOCAB);

        // verify the contents randomly
        assertNotNull(platformVocabsFromArdc);

        assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Fixed station")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("fixed benthic node")
                && internalNode.getNarrower() == null)));

        assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Float")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("drifting subsurface profiling float")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("NINJA Argo Float with SBE")))));

        assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Vessel")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("small boat")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Kinetic Energy")))));

        assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Mooring and buoy")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("moored surface buoy")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Lizard Island Sensor Float 1")))));

        // read from Elastic search
        List<JsonNode> platformVocabsFromEs = vocabService.getPlatformVocabs();
        assertNotNull(platformVocabsFromEs);
        assertEquals(platformVocabsFromEs.size(), platformVocabsFromArdc.size());

        JSONAssert.assertEquals(indexerObjectMapper.valueToTree(platformVocabsFromEs).toPrettyString(), indexerObjectMapper.valueToTree(platformVocabsFromArdc).toPrettyString(), JSONCompareMode.LENIENT);

        // even more tests
        String cannedData = readResourceFile("classpath:canned/aodn_platform_vocabs.json");
        // This will assert that all fields in cannedData are present in actual data,
        // but ignores any extra fields in actual data (scenario: source from ARDC is updated with more vocabs added)
        JSONAssert.assertEquals(cannedData, indexerObjectMapper.valueToTree(platformVocabsFromEs).toPrettyString(), JSONCompareMode.LENIENT);
    }

    @Test
    void testProcessOrganisationVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> organisationVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.ORGANISATION_VOCAB);

        // verify the contents randomly
        assertNotNull(organisationVocabsFromArdc);

        assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("State and Territory Government Departments and Agencies")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Victorian Government")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Victorian Institute of Marine Sciences (VIMS)")))));

        assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Industry")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("EOMAP Australia Pty Ltd")
                && internalNode.getNarrower() == null)));

        assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Local Government")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("New South Wales Councils")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Hornsby Shire Council")))));

        assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Australian Universities")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Curtin University")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Centre for Marine Science and Technology (CMST), Curtin University")))));

        // case vocab doesn't have broadMatch node, it became a root node
        assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Integrated Marine Observing System (IMOS)") && rootNode.getAbout().equals("http://vocab.aodn.org.au/def/organisation/entity/133")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("National Mooring Network Facility, Integrated Marine Observing System (IMOS)")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("New South Wales Moorings Sub-Facility, Integrated Marine Observing System (IMOS)")))));

        // to further confirm the vocab service accuracy, this IMOS root node doesn't have much information
        // notice the url has "organisation_classes/category"
        // http://vocab.aodn.org.au/def/organisation/entity/133 is related to but not directly sit ABOVE http://vocab.aodn.org.au/def/organisation_classes/category/26
        // https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-organisation-category-vocabulary/version-2-5/resource.json?uri=http://vocab.aodn.org.au/def/organisation_classes/category/26
        assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Integrated Marine Observing System (IMOS)") && rootNode.getAbout().equals("http://vocab.aodn.org.au/def/organisation_classes/category/26")
                        && rootNode.getNarrower() == null));

        // read from Elastic search
        List<JsonNode> organisationVocabsFromEs = vocabService.getOrganisationVocabs();
        assertNotNull(organisationVocabsFromEs);
        assertEquals(organisationVocabsFromEs.size(), organisationVocabsFromArdc.size());

        JSONAssert.assertEquals(indexerObjectMapper.valueToTree(organisationVocabsFromEs).toPrettyString(), indexerObjectMapper.valueToTree(organisationVocabsFromArdc).toPrettyString(), JSONCompareMode.LENIENT);

        // even more tests
        String cannedData = readResourceFile("classpath:canned/aodn_organisation_vocabs.json");
        // This will assert that all fields in cannedData are present in actual data,
        // but ignores any extra fields in actual data (scenario: source from ARDC is updated with more vocabs added)
        JSONAssert.assertEquals(cannedData, indexerObjectMapper.valueToTree(organisationVocabsFromEs).toPrettyString(), JSONCompareMode.LENIENT);
    }
}
