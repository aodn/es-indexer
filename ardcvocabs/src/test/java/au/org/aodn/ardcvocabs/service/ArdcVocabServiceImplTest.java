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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ArdcVocabServiceImplTest extends BaseTestClass {

    protected ArdcVocabServiceImpl ardcVocabService;

    @Mock
    RestTemplate mockRestTemplate;

    public static void setupMockRestTemplate(RestTemplate template) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, String> links = Stream.of(new String[][] {
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json", "/category/page0.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=1", "/category/page1.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=2", "/category/page2.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=3", "/category/page3.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/1", "/category/vocab1.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/2", "/category/vocab2.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/3", "/category/vocab3.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/4", "/category/vocab4.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/5", "/category/vocab5.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/6", "/category/vocab6.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/7", "/category/vocab7.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/8", "/category/vocab8.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/9", "/category/vocab9.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/10", "/category/vocab10.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/11", "/category/vocab11.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/19", "/category/vocab19.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/22", "/category/vocab22.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/23", "/category/vocab23.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/25", "/category/vocab25.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/26", "/category/vocab26.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/27", "/category/vocab27.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/28", "/category/vocab28.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/30", "/category/vocab30.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/45", "/category/vocab45.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/46", "/category/vocab46.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/47", "/category/vocab47.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/48", "/category/vocab48.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/49", "/category/vocab49.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/50", "/category/vocab50.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/51", "/category/vocab51.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/52", "/category/vocab52.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/57", "/category/vocab57.json" },
                { "/aodn/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/58", "/category/vocab58.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json", "/platform/page0.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=1", "/platform/page1.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=2", "/platform/page2.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=3", "/platform/page3.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=4", "/platform/page4.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=5", "/platform/page5.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=6", "/platform/page6.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=7", "/platform/page7.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=8", "/platform/page8.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=9", "/platform/page9.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=10", "/platform/page10.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=11", "/platform/page11.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=12", "/platform/page12.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=13", "/platform/page13.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=14", "/platform/page14.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=15", "/platform/page15.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=16", "/platform/page16.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=17", "/platform/page17.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=18", "/platform/page18.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=19", "/platform/page19.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=20", "/platform/page20.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=21", "/platform/page21.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/1", "/platform/entity1.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/2", "/platform/entity2.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/3", "/platform/entity3.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/4", "/platform/entity4.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/8", "/platform/entity8.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/9", "/platform/entity9.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/10", "/platform/entity10.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/11", "/platform/entity11.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/12", "/platform/entity12.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/13", "/platform/entity13.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/14", "/platform/entity14.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/15", "/platform/entity15.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/16", "/platform/entity16.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/17", "/platform/entity17.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/18", "/platform/entity18.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/19", "/platform/entity19.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/20", "/platform/entity20.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/21", "/platform/entity21.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/22", "/platform/entity22.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/23", "/platform/entity23.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/24", "/platform/entity24.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/25", "/platform/entity25.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/26", "/platform/entity26.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/27", "/platform/entity27.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/28", "/platform/entity28.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/29", "/platform/entity29.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/30", "/platform/entity30.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/31", "/platform/entity31.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/32", "/platform/entity32.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/33", "/platform/entity33.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/34", "/platform/entity34.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/35", "/platform/entity35.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/36", "/platform/entity36.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/37", "/platform/entity37.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/38", "/platform/entity38.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/39", "/platform/entity39.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/40", "/platform/entity40.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/41", "/platform/entity41.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/42", "/platform/entity42.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/43", "/platform/entity43.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/44", "/platform/entity44.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/45", "/platform/entity45.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/46", "/platform/entity46.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/47", "/platform/entity47.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/48", "/platform/entity48.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/49", "/platform/entity49.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/50", "/platform/entity50.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/51", "/platform/entity51.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/52", "/platform/entity52.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/53", "/platform/entity53.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/54", "/platform/entity54.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/55", "/platform/entity55.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/56", "/platform/entity56.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/57", "/platform/entity57.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/58", "/platform/entity58.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/59", "/platform/entity59.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/60", "/platform/entity60.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/61", "/platform/entity61.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/62", "/platform/entity62.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/63", "/platform/entity63.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/64", "/platform/entity64.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/65", "/platform/entity65.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/66", "/platform/entity66.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/77", "/platform/entity77.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/84", "/platform/entity84.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/94", "/platform/entity94.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/97", "/platform/entity97.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/372", "/platform/entity372.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/373", "/platform/entity373.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/374", "/platform/entity374.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/375", "/platform/entity375.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/376", "/platform/entity376.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/377", "/platform/entity377.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/378", "/platform/entity378.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/379", "/platform/entity379.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/380", "/platform/entity380.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/383", "/platform/entity383.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/386", "/platform/entity386.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/395", "/platform/entity395.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/396", "/platform/entity396.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/397", "/platform/entity397.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/399", "/platform/entity399.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/401", "/platform/entity401.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/405", "/platform/entity401.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/406", "/platform/entity406.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/407", "/platform/entity407.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/411", "/platform/entity411.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/412", "/platform/entity412.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/413", "/platform/entity413.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/414", "/platform/entity414.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/417", "/platform/entity417.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/422", "/platform/entity422.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/425", "/platform/entity425.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/427", "/platform/entity427.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/478", "/platform/entity478.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/488", "/platform/entity488.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/489", "/platform/entity489.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/5", "/platform/entity5.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/565", "/platform/entity565.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/566", "/platform/entity566.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/570", "/platform/entity570.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/571", "/platform/entity571.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/572", "/platform/entity572.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/573", "/platform/entity573.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/574", "/platform/entity574.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/576", "/platform/entity576.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/590", "/platform/entity590.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/591", "/platform/entity591.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/596", "/platform/entity596.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/597", "/platform/entity597.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/6", "/platform/entity6.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/629", "/platform/entity629.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/631", "/platform/entity631.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/637", "/platform/entity637.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/638", "/platform/entity638.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/639", "/platform/entity639.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/640", "/platform/entity640.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/641", "/platform/entity641.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/642", "/platform/entity642.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/643", "/platform/entity643.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/646", "/platform/entity646.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/647", "/platform/entity647.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/648", "/platform/entity648.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/649", "/platform/entity649.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/650", "/platform/entity650.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/651", "/platform/entity651.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/652", "/platform/entity652.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/653", "/platform/entity653.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/654", "/platform/entity654.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/655", "/platform/entity655.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/656", "/platform/entity656.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/658", "/platform/entity658.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/659", "/platform/entity659.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/660", "/platform/entity660.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/7", "/platform/entity7.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/728", "/platform/entity728.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/729", "/platform/entity729.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/730", "/platform/entity730.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/733", "/platform/entity733.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/734", "/platform/entity734.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/735", "/platform/entity735.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/736", "/platform/entity736.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/737", "/platform/entity737.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/738", "/platform/entity738.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/739", "/platform/entity739.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/740", "/platform/entity740.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/741", "/platform/entity741.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/742", "/platform/entity742.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/743", "/platform/entity743.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/744", "/platform/entity744.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/745", "/platform/entity745.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/746", "/platform/entity746.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/747", "/platform/entity747.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/748", "/platform/entity748.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/749", "/platform/entity749.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/750", "/platform/entity750.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/751", "/platform/entity751.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/752", "/platform/entity752.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/753", "/platform/entity753.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/754", "/platform/entity754.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/755", "/platform/entity755.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/756", "/platform/entity756.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/757", "/platform/entity757.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/758", "/platform/entity758.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/759", "/platform/entity759.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/760", "/platform/entity760.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/761", "/platform/entity761.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/762", "/platform/entity762.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/763", "/platform/entity763.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/764", "/platform/entity764.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/765", "/platform/entity765.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/766", "/platform/entity766.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/767", "/platform/entity767.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/390", "/platform/vocab_entity_390.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/890", "/platform/param890.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/891", "/platform/param891.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/892", "/platform/param892.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/893", "/platform/param893.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/894", "/platform/param894.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ALLOXXPX", "/platform/nercALLOXXPX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CAPHZZ01", "/platform/nercCAPHZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CDEWZZ01", "/platform/nercCDEWZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CHLTMASS", "/platform/nercCHLTMASS.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CHUMSS01", "/platform/nercCHUMSS01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CHLTVOLU", "/platform/nercCHLTVOLU.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CNDCZZ01", "/platform/nercCNDCZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CRELZZ01", "/platform/nercCRELZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CTMPZZ01", "/platform/nercCTMPZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CWETZZ01", "/platform/nercCWETZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ERWDZZ01", "/platform/nercERWDZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ESSAZZ01", "/platform/nercESSAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ESNSZZXX", "/platform/nercESNSZZXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ESEWZZXX", "/platform/nercESEWZZXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ERWSZZ01", "/platform/nercERWSZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/EWDAZZ01", "/platform/nercEWDAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FUCXZZZZ", "/platform/nercFUCXZZZZ.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FCO2WTAT", "/platform/nercFCO2WTAT.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FCO2XXXX", "/platform/nercFCO2XXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FLUOZZZZ", "/platform/nercFLUOZZZZ.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GCMXZZ01", "/platform/nercGCMXZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GSPRZZ01", "/platform/nercGSPRZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GSZZXXXX", "/platform/nercGSZZXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GTCAZZ01", "/platform/nercGTCAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GWDRZZ01", "/platform/nercGWDRZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LCDAZZ01", "/platform/nercLCDAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LCEWZZ01", "/platform/nercLCEWZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LCNSZZ01", "/platform/nercLCNSZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LRZAZZZZ", "/platform/nercLRZAZZZZ.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LWRDZZ01", "/platform/nercLWRDZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/MDMAP014", "/platform/nercMDMAP014.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/PERDXXXX", "/platform/nercPERDXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/PSLTZZ01", "/platform/nercPSLTZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/SVELXXXX", "/platform/nercSVELXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/TEMPPR01", "/platform/nercTEMPPR01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/TURBXXXX", "/platform/nercTURBXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/XCO2DRAT", "/platform/nercXCO2DRAT.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/XCO2WBDY", "/platform/nercXCO2WBDY.json"},
                { "aodn-platform-vocabulary/version-6-1/concept.json", "/platform/concept0.json"}
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        // Create expect result
        for(Map.Entry<String, String> entry : links.entrySet()) {
            Mockito.when(template.getForObject(endsWith(entry.getKey()), eq(ObjectNode.class)))
                    .thenReturn((ObjectNode)objectMapper.readTree(readResourceFile("/databag" + entry.getValue())));
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

        setupMockRestTemplate(mockRestTemplate);

        List<VocabModel> parameterVocabModelList = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PARAMETER_VOCAB);
        assertEquals(4, parameterVocabModelList.size(), "Total equals");

        Optional<VocabModel> c = parameterVocabModelList
                .stream()
                .filter(p -> p.getLabel().equals("Chemical"))
                .findFirst();

        assertTrue("Find target Chemical", c.isPresent());
        assertEquals(6, c.get().getNarrower().size(), "Have narrower equals");


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
