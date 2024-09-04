package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.BaseTestClass;
import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ArdcVocabServiceImplTest extends BaseTestClass {

    protected ArdcVocabServiceImpl ardcVocabService;

    @Mock
    RestTemplate mockRestTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    protected void setupMockRestTemplate() throws IOException {
        Map<String, String> links = Stream.of(new String[][] {
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json", "page0.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=1","page1.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=2", "page2.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=3", "page3.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/1", "vocab1.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/2", "vocab2.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/3", "vocab3.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/4", "vocab4.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/5", "vocab5.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/6", "vocab6.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/7", "vocab7.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/8", "vocab8.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/9", "vocab9.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/10", "vocab10.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/11", "vocab11.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/19", "vocab19.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/22", "vocab22.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/23", "vocab23.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/25", "vocab25.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/26", "vocab26.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/27", "vocab27.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/28", "vocab28.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/30", "vocab30.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/45", "vocab45.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/46", "vocab46.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/47", "vocab47.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/48", "vocab48.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/49", "vocab49.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/50", "vocab50.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/51", "vocab51.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/52", "vocab52.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/57", "vocab57.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/58", "vocab58.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json", "vocab_discovery0.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/390", "vocab_entity_390.json" },
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        // Create expect result
        for(Map.Entry<String, String> entry : links.entrySet()) {
            Mockito.when(mockRestTemplate.getForObject(endsWith(entry.getKey()), eq(ObjectNode.class)))
                    .thenReturn((ObjectNode)objectMapper.readTree(readResourceFile("classpath:databag/category/" + entry.getValue())));
        }
    }

    @BeforeEach
    public void init() {
        this.ardcVocabService = new ArdcVocabServiceImpl(mockRestTemplate);
        this.ardcVocabService.vocabApiBase = "https://vocabs.ardc.edu.au/repository/api/lda/aodn";
    }

    @AfterEach void clear() {
        Mockito.reset(mockRestTemplate);
    }

    @Test
    public void verifyGetParameterVocab() throws IOException {

        setupMockRestTemplate();

        List<VocabModel> parameterVocabModelList = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PARAMETER_VOCAB);
        assertEquals(4, parameterVocabModelList.size(), "Total equals");

        Optional<VocabModel> c = parameterVocabModelList
                .stream()
                .filter(p -> p.getLabel().equals("Chemical"))
                .findFirst();

        assertTrue("Find target Chemical", c.isPresent());
        assertEquals(5, c.get().getNarrower().size(), "Have narrower equals");


        Optional<VocabModel> b = parameterVocabModelList
                .stream()
                .filter(p -> p.getLabel().equals("Biological"))
                .findFirst();

        assertTrue("Find target Biological", b.isPresent());
        assertEquals(5, b.get().getNarrower().size(), "Have narrower equals");


        Optional<VocabModel> pa = parameterVocabModelList
                .stream()
                .filter(p -> p.getLabel().equals("Physical-Atmosphere"))
                .findFirst();

        assertTrue("Find target Physical-Atmosphere", pa.isPresent());
        assertEquals(8, pa.get().getNarrower().size(), "Have narrower equals");

        Optional<VocabModel> airTemperature = pa.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Air temperature"))
                .findFirst();
        assertTrue("Find target Air temperature", airTemperature.isPresent());

        Optional<VocabModel> visibility = pa.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Visibility"))
                .findFirst();

        assertTrue("Find target Visibility", visibility.isPresent());

        Optional<VocabModel> horizontalVisibilityInTheAtmosphere = visibility.get().getNarrower()
                .stream()
                .filter(p -> p.getLabel().equals("Horizontal visibility in the atmosphere"))
                .findFirst();

        assertTrue("Horizontal visibility in the atmosphere found", horizontalVisibilityInTheAtmosphere.isPresent());

        Optional<VocabModel> pw = parameterVocabModelList
                .stream()
                .filter(p -> p.getLabel().equals("Physical-Water"))
                .findFirst();

        assertTrue("Find target Physical-Water", pw.isPresent());
        assertEquals(14, pw.get().getNarrower().size(), "Have narrower equals");

    }

}
