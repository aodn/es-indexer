package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.*;

// JSONAssert is a useful dependency for comparing JSON values, replacing the traditional string-to-string approach when dealing with JSON.
// More details: https://www.baeldung.com/jsonassert#overview, https://github.com/skyscreamer/JSONassert
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

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
    void testProcessParameterVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> parameterVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PARAMETER_VOCAB);

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

    @Test
    void testProcessPlatformVocabs() throws IOException, JSONException {
        // read from ARDC
        List<VocabModel> platformVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PLATFORM_VOCAB);

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
        List<VocabModel> organisationVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.ORGANISATION_VOCAB);

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
}
