package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.configuration.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    protected ObjectMapper objectMapper = new ObjectMapper();

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
        assertEquals(4, parameterVocabs.size());
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
    void testProcessParameterVocabs() throws IOException {
        // read from ARDC
        List<VocabModel> parameterVocabsFromArdc = vocabService.getVocabTreeFromArdcByType(AppConstants.ARDC_VOCAB_API_BASE, VocabApiPaths.PARAMETER_VOCAB);

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
        assertEquals(objectMapper.valueToTree(parameterVocabsFromEs).toPrettyString(), objectMapper.valueToTree(parameterVocabsFromArdc).toPrettyString());
    }
}
