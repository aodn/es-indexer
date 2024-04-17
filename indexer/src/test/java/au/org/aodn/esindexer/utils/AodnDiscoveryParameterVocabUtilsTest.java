package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.model.CategoryVocabModel;
import au.org.aodn.esindexer.abstracts.OgcApiRequestEntityCreator;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AodnDiscoveryParameterVocabUtilsTest {
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OgcApiRequestEntityCreator ogcApiRequestEntityCreator;

    @InjectMocks
    AodnDiscoveryParameterVocabUtils aodnDiscoveryParameterVocabUtils;

    @BeforeEach
    void setup() throws IOException {
        File f = ResourceUtils.getFile("classpath:canned/aodn_discovery_parameter_vocabs.json");
        String jsonString = new String(Files.readAllBytes(f.toPath()));
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
        List<CategoryVocabModel> results = aodnDiscoveryParameterVocabUtils.fetchAodnDiscoveryParameterVocabs();
        // Verification
        assertEquals(33, results.size());
    }

    @Test
    void testGetAodnDiscoveryCategories() {
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
