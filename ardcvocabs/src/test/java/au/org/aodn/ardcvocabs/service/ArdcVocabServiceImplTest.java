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
import org.mockito.stubbing.Answer;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ArdcVocabServiceImplTest extends BaseTestClass {

    protected ArdcVocabServiceImpl ardcVocabService;
    protected ObjectMapper mapper = new ObjectMapper();

    @Mock
    RestTemplate mockRestTemplate;

    /**
     * Check the url and return the canned file content
     * @param template
     * @return
     * @throws IOException
     */
    public static RestTemplate setupParameterVocabMockRestTemplate(RestTemplate template) throws IOException {

        final ObjectMapper objectMapper = new ObjectMapper();

        Mockito.doAnswer(f -> {
            String url = f.getArgument(0);
            if(url.contains("/aodn-parameter-category-vocabulary/version-2-1/concept.json")) {
                if(url.contains("_page")) {
                    String page = url.split("=")[1];
                    return objectMapper.readValue(readResourceFile("/databag/category/page" + page + ".json"), ObjectNode.class);
                }
                else {
                    return objectMapper.readValue(readResourceFile("/databag/category/page0.json"), ObjectNode.class);
                }
            }
            else if (url.contains("/aodn-parameter-category-vocabulary/version-2-1/resource.json?uri=http://vocab.aodn.org.au/def/parameter_classes/category/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/category/vocab" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else if (url.contains("/aodn-discovery-parameter-vocabulary/version-1-6/concept.json")) {
                if(url.contains("_page")) {
                    String page = url.split("=")[1];
                    return objectMapper.readValue(readResourceFile("/databag/parameter/page" + page + ".json"), ObjectNode.class);
                }
                else {
                    return objectMapper.readValue(readResourceFile("/databag/parameter/page0.json"), ObjectNode.class);
                }
            }
            else if (url.contains("/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/entity/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/parameter/entity" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else if (url.contains("/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.aodn.org.au/def/discovery_parameter/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/parameter/param" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else if (url.contains("/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=http://vocab.nerc.ac.uk/collection/P01/current/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/parameter/nerc" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else {
                throw new FileNotFoundException(url);
            }
        })
        .when(template)
        .getForObject(
                argThat(s -> s.contains("/aodn-discovery-parameter-vocabulary/version-1-6/") || s.contains("/aodn-parameter-category-vocabulary/version-2-1/")),
                eq(ObjectNode.class),
                any(Object[].class)     // It is important to have this any otherwise it will match getForObject(URI, Class<T>)
        );

        return  template;
    }

    public static RestTemplate setupPlatformMockRestTemplate(RestTemplate template) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        Mockito.doAnswer(f -> {
            String url = f.getArgument(0);
            if(url.contains("/aodn-platform-category-vocabulary/version-1-2/concept.json")) {
                if(url.contains("_page")) {
                    String page = url.split("=")[1];
                    return objectMapper.readValue(readResourceFile("/databag/platform/page" + page + ".json"), ObjectNode.class);
                }
                else {
                    return objectMapper.readValue(readResourceFile("/databag/platform/page0.json"), ObjectNode.class);
                }
            }
            else if (url.contains("/aodn-platform-vocabulary/version-6-1/concept.json")) {
                if(url.contains("_page")) {
                    String page = url.split("=")[1];
                    return objectMapper.readValue(readResourceFile("/databag/platform/vocab" + page + ".json"), ObjectNode.class);
                }
                else {
                    return objectMapper.readValue(readResourceFile("/databag/platform/vocab0.json"), ObjectNode.class);
                }
            }
            else if(url.contains("/aodn-platform-vocabulary/version-6-1/resource.json?uri=http://vocab.aodn.org.au/def/platform/entity/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/platform/entity" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else if (url.contains("/aodn-platform-vocabulary/version-6-1/resource.json?uri=http://vocab.nerc.ac.uk/collection/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/platform/nerc" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else {
                throw new FileNotFoundException(url);
            }
        })
        .when(template)
        .getForObject(
                argThat(s -> s!= null && (s.contains("/aodn-platform-vocabulary/version-6-1/") || s.contains("/aodn-platform-category-vocabulary/version-1-2/"))),
                eq(ObjectNode.class),
                any(Object[].class)     // It is important to have this any otherwise it will match getForObject(URI, Class<T>)
        );

        return  template;
    }

    public static RestTemplate setupOrganizationMockRestTemplate(RestTemplate template) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        Mockito.doAnswer(f -> {
            String url = f.getArgument(0);
            if(url.contains("/aodn-organisation-category-vocabulary/version-2-5/concept.json")) {
                if(url.contains("_page")) {
                    String page = url.split("=")[1];
                    return objectMapper.readValue(readResourceFile("/databag/organization/page" + page + ".json"), ObjectNode.class);
                }
                else {
                    return objectMapper.readValue(readResourceFile("/databag/organization/page0.json"), ObjectNode.class);
                }
            }
            else if(url.contains("/aodn-organisation-vocabulary/version-2-5/concept.json")) {
                if(url.contains("_page")) {
                    String page = url.split("=")[1];
                    return objectMapper.readValue(readResourceFile("/databag/organization/vocab" + page + ".json"), ObjectNode.class);
                }
                else {
                    return objectMapper.readValue(readResourceFile("/databag/organization/vocab0.json"), ObjectNode.class);
                }
            }
            else if(url.contains("/aodn-organisation-vocabulary/version-2-5/resource.json?uri=http://vocab.aodn.org.au/def/organisation/entity/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/organization/entity" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else if(url.contains("/aodn-organisation-category-vocabulary/version-2-5/resource.json?uri=http://vocab.aodn.org.au/def/organisation_classes/category/")) {
                String[] token = url.split("/");
                return objectMapper.readValue(readResourceFile("/databag/organization/category" + token[token.length - 1] + ".json"), ObjectNode.class);
            }
            else {
                throw new FileNotFoundException(url);
            }
        })
        .when(template)
        .getForObject(
                argThat(s -> s.contains("/aodn-organisation-category-vocabulary/version-2-5/") || s.contains("/aodn-organisation-vocabulary/version-2-5/")),
                eq(ObjectNode.class),
                any(Object[].class)     // It is important to have this any otherwise it will match getForObject(URI, Class<T>)
        );

        return  template;
    }

    @BeforeEach
    public void init() {
        // If you want real download for testing, uncomment below and do not use mock
        //this.ardcVocabService = new ArdcVocabServiceImpl(new RestTemplate());
        this.ardcVocabService = new ArdcVocabServiceImpl(mockRestTemplate, new RetryTemplate());
        this.ardcVocabService.vocabApiBase = "https://vocabs.ardc.edu.au/repository/api/lda/aodn";
    }

    @AfterEach void clear() {
        Mockito.reset(mockRestTemplate);
    }

    @Test
    public void verifyParameterVocab() throws IOException, JSONException {

        mockRestTemplate = setupParameterVocabMockRestTemplate(mockRestTemplate);

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

    @Test
    public void verifyPlatform() throws IOException, JSONException {
        mockRestTemplate = setupPlatformMockRestTemplate(mockRestTemplate);

        List<VocabModel> platformVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.PLATFORM_VOCAB);

        // verify the contents randomly
        assertNotNull(platformVocabsFromArdc);

        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Fixed station")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("fixed benthic node")
                && internalNode.getNarrower() == null)));

        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Float")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("drifting subsurface profiling float")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("NINJA Argo Float with SBE")))));

        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Vessel")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("small boat")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Kinetic Energy")))));

        Assertions.assertTrue(platformVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Mooring and buoy")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("moored surface buoy")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Lizard Island Sensor Float 1")))));

        final String expectedJson = readResourceFile("/databag/aodn_platform_vocabs.json");
        JSONAssert.assertEquals(
                expectedJson,
                mapper.valueToTree(platformVocabsFromArdc).toPrettyString(),
                JSONCompareMode.STRICT
        );
    }

    @Test
    public void verifyOrganization() throws IOException, JSONException {
        mockRestTemplate = setupOrganizationMockRestTemplate(mockRestTemplate);

        List<VocabModel> organisationVocabsFromArdc = ardcVocabService.getVocabTreeFromArdcByType(VocabApiPaths.ORGANISATION_VOCAB);

        // verify the contents randomly
        assertNotNull(organisationVocabsFromArdc);

        var i = organisationVocabsFromArdc.stream().filter(rootNode -> rootNode.getLabel().equalsIgnoreCase("State and Territory Government Departments and Agencies")).findFirst();

        Assertions.assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("State and Territory Government Departments and Agencies")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Victorian Government")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Victorian Institute of Marine Sciences (VIMS)")))));

        Assertions.assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Industry")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("EOMAP Australia Pty Ltd")
                && internalNode.getNarrower() == null)));

        Assertions.assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Local Government")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("New South Wales Councils")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Hornsby Shire Council")))));

        Assertions.assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Australian Universities")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("Curtin University")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("Centre for Marine Science and Technology (CMST), Curtin University")))));

        // case vocab doesn't have broadMatch node, it became a root node
        Assertions.assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Integrated Marine Observing System (IMOS)") && rootNode.getAbout().equals("http://vocab.aodn.org.au/def/organisation/entity/133")
                && rootNode.getNarrower() != null && !rootNode.getNarrower().isEmpty()
                && rootNode.getNarrower().stream().anyMatch(internalNode -> internalNode.getLabel().equalsIgnoreCase("National Mooring Network Facility, Integrated Marine Observing System (IMOS)")
                && internalNode.getNarrower() != null && !internalNode.getNarrower().isEmpty()
                && internalNode.getNarrower().stream().anyMatch(leafNode -> leafNode.getLabel().equalsIgnoreCase("New South Wales Moorings Sub-Facility, Integrated Marine Observing System (IMOS)")))));

        // to further confirm the vocab service accuracy, this IMOS root node doesn't have much information
        // notice the url has "organisation_classes/category"
        // http://vocab.aodn.org.au/def/organisation/entity/133 is related to but not directly sit ABOVE http://vocab.aodn.org.au/def/organisation_classes/category/26
        // https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-organisation-category-vocabulary/version-2-5/resource.json?uri=http://vocab.aodn.org.au/def/organisation_classes/category/26
        Assertions.assertTrue(organisationVocabsFromArdc.stream().anyMatch(rootNode -> rootNode.getLabel().equalsIgnoreCase("Integrated Marine Observing System (IMOS)") && rootNode.getAbout().equals("http://vocab.aodn.org.au/def/organisation_classes/category/26")
                && rootNode.getNarrower() == null));


        final String expectedJson = readResourceFile("/databag/aodn_organisation_vocabs.json");
        JSONAssert.assertEquals(
                expectedJson,
                mapper.valueToTree(organisationVocabsFromArdc).toPrettyString(),
                JSONCompareMode.STRICT
        );
    }
}
