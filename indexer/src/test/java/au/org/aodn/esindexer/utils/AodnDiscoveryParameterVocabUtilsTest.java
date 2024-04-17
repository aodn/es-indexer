package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.abstracts.OgcApiRequestEntityCreator;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AodnDiscoveryParameterVocabUtilsTest extends BaseTestClass {
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OgcApiRequestEntityCreator ogcApiRequestEntityCreator;

    @Autowired
    AodnDiscoveryParameterVocabUtils aodnDiscoveryParameterVocabUtils;

    @BeforeEach
    void setup() throws IOException {
        String jsonString = readResourceFile("classpath:canned/aodn_discovery_parameter_vocabs.json");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJsonNode = objectMapper.readTree(jsonString);
        HttpEntity<String> mockHttpEntity = new HttpEntity<>(MediaType.APPLICATION_JSON_VALUE);
        ResponseEntity<JsonNode> mockResponseEntity = ResponseEntity.ok(responseJsonNode);

        when(ogcApiRequestEntityCreator.getRequestEntity(MediaType.APPLICATION_JSON, null)).thenReturn(mockHttpEntity);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(mockHttpEntity),
                eq(JsonNode.class)
        )).thenReturn(mockResponseEntity);
    }

    @Test
    void testFetchAodnDiscoveryParameterVocabs() {
        JsonNode results = aodnDiscoveryParameterVocabUtils.fetchAodnDiscoveryParameterVocabs();
        // Verification
        assertEquals(33, results.size());
    }

    @Test
    void testGetAodnDiscoveryCategories() throws Exception {
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
        List<String> categories = aodnDiscoveryParameterVocabUtils.getAodnDiscoveryCategories(themes);

        // Assertions
        assertNotNull(categories);
        assertTrue(categories.contains("Alkalinity"));
        assertTrue(categories.contains("Temperature"));
        assertTrue(categories.contains("Salinity"));
        assertEquals(3, categories.size());
    }
}
