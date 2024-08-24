package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.service.ArdcVocabsService;
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
public class VocabUtilsTest {
    @Autowired
    ArdcVocabsService ardcVocabsService;

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
        List<String> parameterVocabs = ardcVocabsService.getParameterVocabs(themes);

        // Assertions
        assertNotNull(parameterVocabs);
        assertTrue(parameterVocabs.contains("alkalinity"));
        assertTrue(parameterVocabs.contains("temperature"));
        assertTrue(parameterVocabs.contains("salinity"));
        assertTrue(parameterVocabs.contains("carbon"));
        assertEquals(4, parameterVocabs.size());
    }
}
