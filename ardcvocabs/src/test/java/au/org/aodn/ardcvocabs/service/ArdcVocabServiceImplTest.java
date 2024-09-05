package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.BaseTestClass;
import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ArdcVocabServiceImplTest extends BaseTestClass {

    protected ArdcVocabServiceImpl ardcVocabService;
    protected ObjectMapper mapper = new ObjectMapper();

    @Mock
    RestTemplate mockRestTemplate;

    public static void setupMockRestTemplate(RestTemplate template) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, String> links = Stream.of(new String[][] {
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json", "/category/page0.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=1", "/category/page1.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=2", "/category/page2.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=3", "/category/page3.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/concept.json?_page=4", "/category/page4.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/1", "/category/vocab1.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/2", "/category/vocab2.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/3", "/category/vocab3.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/4", "/category/vocab4.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/5", "/category/vocab5.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/6", "/category/vocab6.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/7", "/category/vocab7.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/8", "/category/vocab8.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/9", "/category/vocab9.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/10", "/category/vocab10.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/11", "/category/vocab11.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/19", "/category/vocab19.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/22", "/category/vocab22.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/23", "/category/vocab23.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/25", "/category/vocab25.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/26", "/category/vocab26.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/27", "/category/vocab27.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/28", "/category/vocab28.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/30", "/category/vocab30.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/45", "/category/vocab45.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/46", "/category/vocab46.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/47", "/category/vocab47.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/48", "/category/vocab48.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/49", "/category/vocab49.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/50", "/category/vocab50.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/51", "/category/vocab51.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/52", "/category/vocab52.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/57", "/category/vocab57.json" },
                { "/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/58", "/category/vocab58.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json", "/parameter/page0.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=1", "/parameter/page1.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=2", "/parameter/page2.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=3", "/parameter/page3.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=4", "/parameter/page4.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=5", "/parameter/page5.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=6", "/parameter/page6.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=7", "/parameter/page7.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=8", "/parameter/page8.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=9", "/parameter/page9.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=10", "/parameter/page10.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=11", "/parameter/page11.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=12", "/parameter/page12.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=13", "/parameter/page13.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=14", "/parameter/page14.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=15", "/parameter/page15.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=16", "/parameter/page16.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=17", "/parameter/page17.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=18", "/parameter/page18.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=19", "/parameter/page19.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=20", "/parameter/page20.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json?_page=21", "/parameter/page21.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/1", "/parameter/entity1.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/2", "/parameter/entity2.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/3", "/parameter/entity3.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/4", "/parameter/entity4.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/8", "/parameter/entity8.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/9", "/parameter/entity9.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/10", "/parameter/entity10.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/11", "/parameter/entity11.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/12", "/parameter/entity12.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/13", "/parameter/entity13.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/14", "/parameter/entity14.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/15", "/parameter/entity15.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/16", "/parameter/entity16.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/17", "/parameter/entity17.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/18", "/parameter/entity18.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/19", "/parameter/entity19.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/20", "/parameter/entity20.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/21", "/parameter/entity21.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/22", "/parameter/entity22.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/23", "/parameter/entity23.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/24", "/parameter/entity24.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/25", "/parameter/entity25.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/26", "/parameter/entity26.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/27", "/parameter/entity27.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/28", "/parameter/entity28.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/29", "/parameter/entity29.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/30", "/parameter/entity30.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/31", "/parameter/entity31.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/32", "/parameter/entity32.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/33", "/parameter/entity33.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/34", "/parameter/entity34.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/35", "/parameter/entity35.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/36", "/parameter/entity36.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/37", "/parameter/entity37.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/38", "/parameter/entity38.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/39", "/parameter/entity39.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/40", "/parameter/entity40.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/41", "/parameter/entity41.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/42", "/parameter/entity42.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/43", "/parameter/entity43.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/44", "/parameter/entity44.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/45", "/parameter/entity45.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/46", "/parameter/entity46.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/47", "/parameter/entity47.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/48", "/parameter/entity48.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/49", "/parameter/entity49.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/50", "/parameter/entity50.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/51", "/parameter/entity51.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/52", "/parameter/entity52.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/53", "/parameter/entity53.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/54", "/parameter/entity54.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/55", "/parameter/entity55.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/56", "/parameter/entity56.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/57", "/parameter/entity57.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/58", "/parameter/entity58.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/59", "/parameter/entity59.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/60", "/parameter/entity60.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/61", "/parameter/entity61.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/62", "/parameter/entity62.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/63", "/parameter/entity63.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/64", "/parameter/entity64.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/65", "/parameter/entity65.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/66", "/parameter/entity66.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/77", "/parameter/entity77.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/84", "/parameter/entity84.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/94", "/parameter/entity94.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/97", "/parameter/entity97.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/372", "/parameter/entity372.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/373", "/parameter/entity373.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/374", "/parameter/entity374.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/375", "/parameter/entity375.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/376", "/parameter/entity376.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/377", "/parameter/entity377.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/378", "/parameter/entity378.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/379", "/parameter/entity379.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/380", "/parameter/entity380.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/383", "/parameter/entity383.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/386", "/parameter/entity386.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/395", "/parameter/entity395.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/396", "/parameter/entity396.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/397", "/parameter/entity397.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/399", "/parameter/entity399.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/401", "/parameter/entity401.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/405", "/parameter/entity405.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/406", "/parameter/entity406.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/407", "/parameter/entity407.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/411", "/parameter/entity411.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/412", "/parameter/entity412.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/413", "/parameter/entity413.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/414", "/parameter/entity414.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/417", "/parameter/entity417.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/422", "/parameter/entity422.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/425", "/parameter/entity425.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/427", "/parameter/entity427.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/478", "/parameter/entity478.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/488", "/parameter/entity488.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/489", "/parameter/entity489.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/5", "/parameter/entity5.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/565", "/parameter/entity565.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/566", "/parameter/entity566.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/570", "/parameter/entity570.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/571", "/parameter/entity571.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/572", "/parameter/entity572.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/573", "/parameter/entity573.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/574", "/parameter/entity574.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/576", "/parameter/entity576.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/590", "/parameter/entity590.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/591", "/parameter/entity591.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/596", "/parameter/entity596.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/597", "/parameter/entity597.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/6", "/parameter/entity6.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/629", "/parameter/entity629.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/631", "/parameter/entity631.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/637", "/parameter/entity637.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/638", "/parameter/entity638.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/639", "/parameter/entity639.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/640", "/parameter/entity640.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/641", "/parameter/entity641.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/642", "/parameter/entity642.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/643", "/parameter/entity643.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/646", "/parameter/entity646.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/647", "/parameter/entity647.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/648", "/parameter/entity648.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/649", "/parameter/entity649.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/650", "/parameter/entity650.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/651", "/parameter/entity651.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/652", "/parameter/entity652.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/653", "/parameter/entity653.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/654", "/parameter/entity654.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/655", "/parameter/entity655.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/656", "/parameter/entity656.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/658", "/parameter/entity658.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/659", "/parameter/entity659.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/660", "/parameter/entity660.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/7", "/parameter/entity7.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/728", "/parameter/entity728.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/729", "/parameter/entity729.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/730", "/parameter/entity730.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/733", "/parameter/entity733.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/734", "/parameter/entity734.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/735", "/parameter/entity735.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/736", "/parameter/entity736.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/737", "/parameter/entity737.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/738", "/parameter/entity738.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/739", "/parameter/entity739.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/740", "/parameter/entity740.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/741", "/parameter/entity741.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/742", "/parameter/entity742.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/743", "/parameter/entity743.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/744", "/parameter/entity744.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/745", "/parameter/entity745.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/746", "/parameter/entity746.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/747", "/parameter/entity747.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/748", "/parameter/entity748.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/749", "/parameter/entity749.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/750", "/parameter/entity750.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/751", "/parameter/entity751.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/752", "/parameter/entity752.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/753", "/parameter/entity753.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/754", "/parameter/entity754.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/755", "/parameter/entity755.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/756", "/parameter/entity756.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/757", "/parameter/entity757.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/758", "/parameter/entity758.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/759", "/parameter/entity759.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/760", "/parameter/entity760.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/761", "/parameter/entity761.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/762", "/parameter/entity762.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/763", "/parameter/entity763.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/764", "/parameter/entity764.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/765", "/parameter/entity765.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/766", "/parameter/entity766.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/767", "/parameter/entity767.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/390", "/parameter/vocab_entity_390.json" },
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/890", "/parameter/param890.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/891", "/parameter/param891.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/892", "/parameter/param892.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/893", "/parameter/param893.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/894", "/parameter/param894.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ALLOXXPX", "/parameter/nercALLOXXPX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CAPHZZ01", "/parameter/nercCAPHZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CDEWZZ01", "/parameter/nercCDEWZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CHLTMASS", "/parameter/nercCHLTMASS.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CHUMSS01", "/parameter/nercCHUMSS01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CHLTVOLU", "/parameter/nercCHLTVOLU.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CNDCZZ01", "/parameter/nercCNDCZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CRELZZ01", "/parameter/nercCRELZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CTMPZZ01", "/parameter/nercCTMPZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/CWETZZ01", "/parameter/nercCWETZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ERWDZZ01", "/parameter/nercERWDZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ESSAZZ01", "/parameter/nercESSAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ESNSZZXX", "/parameter/nercESNSZZXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ESEWZZXX", "/parameter/nercESEWZZXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/ERWSZZ01", "/parameter/nercERWSZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/EWDAZZ01", "/parameter/nercEWDAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FUCXZZZZ", "/parameter/nercFUCXZZZZ.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FCO2WTAT", "/parameter/nercFCO2WTAT.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FCO2XXXX", "/parameter/nercFCO2XXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/FLUOZZZZ", "/parameter/nercFLUOZZZZ.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GCMXZZ01", "/parameter/nercGCMXZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GSPRZZ01", "/parameter/nercGSPRZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GSZZXXXX", "/parameter/nercGSZZXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GTCAZZ01", "/parameter/nercGTCAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/GWDRZZ01", "/parameter/nercGWDRZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LCDAZZ01", "/parameter/nercLCDAZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LCEWZZ01", "/parameter/nercLCEWZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LCNSZZ01", "/parameter/nercLCNSZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LRZAZZZZ", "/parameter/nercLRZAZZZZ.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/LWRDZZ01", "/parameter/nercLWRDZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/MDMAP014", "/parameter/nercMDMAP014.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/PERDXXXX", "/parameter/nercPERDXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/PSLTZZ01", "/parameter/nercPSLTZZ01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/SVELXXXX", "/parameter/nercSVELXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/TEMPPR01", "/parameter/nercTEMPPR01.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/TURBXXXX", "/parameter/nercTURBXXXX.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/XCO2DRAT", "/parameter/nercXCO2DRAT.json"},
                { "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/XCO2WBDY", "/parameter/nercXCO2WBDY.json"},
                { "/aodn-platform-vocabulary/version-6-1/concept.json", "/parameter/concept0.json"}
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        // Create expect result
        for(Map.Entry<String, String> entry : links.entrySet()) {
            Mockito.when(template.getForObject(endsWith(entry.getKey()), eq(ObjectNode.class)))
                    .thenReturn((ObjectNode)objectMapper.readTree(readResourceFile("/databag" + entry.getValue())));
        }
    }

    @BeforeEach
    public void init() {
        // If you want real download for testing, uncomment below and do not use mock
        //this.ardcVocabService = new ArdcVocabServiceImpl(new RestTemplate());
        this.ardcVocabService = new ArdcVocabServiceImpl(mockRestTemplate);
        this.ardcVocabService.vocabApiBase = "https://vocabs.ardc.edu.au/repository/api/lda/aodn";
    }

    @AfterEach void clear() {
        Mockito.reset(mockRestTemplate);
    }

    @Test
    public void verifyGetParameterVocab() throws IOException, JSONException {

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

        Optional<VocabModel> horizontalVisibilityInTheAtmosphere = visibility.get()
                .getNarrower()
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

        Assertions.assertTrue(parameterVocabModelList.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Physical-Atmosphere")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Air pressure")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Pressure (measured variable) exerted by the atmosphere")))));

        Assertions.assertTrue(parameterVocabModelList.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Chemical")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Alkalinity")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Concentration of carbonate ions per unit mass of the water body")))));

        Assertions.assertTrue(parameterVocabModelList.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Biological")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Ocean Biota")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Mean unit biovolume")))));

        Assertions.assertTrue(parameterVocabModelList.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Physical-Water")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Wave")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Direction at spectral maximum of waves on the water body")))));

        final String expectedJson = readResourceFile("/databag/aodn_discovery_parameter_vocabs.json");
        JSONAssert.assertEquals(
                expectedJson,
                mapper.valueToTree(parameterVocabModelList).toPrettyString(),
                JSONCompareMode.STRICT
        );
    }

//    @Test
//    public void verifyPlatform() throws IOException {
//        setupMockRestTemplate(mockRestTemplate);
//
//        List<VocabModel> platformVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PLATFORM_VOCAB);
//
//        // verify the contents randomly
//        assertNotNull(platformVocabsFromArdc);
//
//        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Fixed station")
//                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
//                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("fixed benthic node")
//                && internalNode.getNarrower() == null)));
//
//        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Float")
//                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
//                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("drifting subsurface profiling float")
//                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
//                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("NINJA Argo Float with SBE")))));
//
//        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Vessel")
//                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
//                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("small boat")
//                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
//                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Kinetic Energy")))));
//
//        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Mooring and buoy")
//                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
//                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("moored surface buoy")
//                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
//                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Lizard Island Sensor Float 1")))));
//
//
//    }
}
