package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.CategoryVocabModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ArdcVocabsServiceTest {

    @Mock
    RestTemplate mockRestTemplate;

    @InjectMocks
    protected ArdcVocabsService ardcVocabsService;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void verifyGetParameterCategory() throws IOException {
        // Create expect result
        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab0.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=1"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab1.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=2"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab2.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=3"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab3.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-discovery-parameter-vocabulary/version-1-6/concept.json"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab_discovery0.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/390"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/vocab_entity_390.json")));

        List<CategoryVocabModel> categoryVocabModelList = ardcVocabsService.getParameterCategory("");
        assertEquals("Total equals", 33, categoryVocabModelList.size());

        Optional<CategoryVocabModel> m = categoryVocabModelList
                .stream()
                .filter(p -> !p.getNarrower().isEmpty() && p.getLabel().equals("Physical-Atmosphere"))
                .findFirst();

        assertTrue("Find target Physical-Atmosphere", m.isPresent());
        assertEquals("Have narrower equals", 6, m.get().getNarrower().size());

        Optional<CategoryVocabModel> visibility = m.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Visibility"))
                .findFirst();

        assertTrue("Find target Visibility", visibility.isPresent());

        Optional<CategoryVocabModel> airSeaLevel = visibility.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Horizontal visibility in the atmosphere"))
                .findFirst();

        assertTrue("Horizontal visibility in the atmosphere found", airSeaLevel.isPresent());
    }
}
