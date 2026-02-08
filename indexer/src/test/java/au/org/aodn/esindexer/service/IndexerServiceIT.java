package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import au.org.aodn.esindexer.model.MockServer;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.stac.model.LinkModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IndexerServiceIT extends BaseTestClass {

    @Autowired
    protected GeoNetworkServiceImpl geoNetworkService;

    @Qualifier("gn4ElasticsearchClient")
    ElasticsearchClient gn4ElasticsearchClient;

    @Autowired
    protected IndexerMetadataService indexerService;

    @Autowired
    protected ObjectMapper indexerObjectMapper;

    @Autowired
    protected ElasticSearchIndexService elasticSearchIndexService;

    @Autowired
    protected MockServer mockServer;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @MockitoBean
    protected DataDiscoveryAiService dataDiscoveryAiService;

    @BeforeAll
    public void setup() {
        // Update the server for geonetwork RESTful URL
        geoNetworkService.setServer(String.format("http://%s:%s",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT))
        );
        // Old test case do not have this link, so it is expect to be empty
        mockServer.getServer().expect(manyTimes(), requestTo(containsString("/notebook_url")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
    }

    @AfterAll
    public void resetMock() {
        mockServer.getServer().reset();
    }

    @AfterEach
    public void clear() throws IOException {
        clearElasticIndex(INDEX_NAME);
    }

    @Test
    public void verifyIsMetadataPublished() throws IOException {
        String uuid1 = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        String uuid2 = "830f9a83-ae6b-4260-a82a-24c4851f7119";
        try {
            insertMetadataRecords(uuid1, "classpath:canned/sample1.xml");
            insertMetadataRecords(uuid2, "classpath:canned/sample2.xml");

            Assertions.assertTrue(indexerService.isMetadataPublished(uuid1), uuid1 + " published");
            Assertions.assertTrue(indexerService.isMetadataPublished(uuid2), uuid2 + " published");
            Assertions.assertFalse(indexerService.isMetadataPublished("not-exist"), "Not exist and not published");
        }
        finally {
            deleteRecord(uuid1,uuid2);
        }
    }

    @Test
    public void verifyGetDocumentCount() throws IOException {
        String uuid1 = "830f9a83-ae6b-4260-a82a-24c4851f7119";
        String uuid2 = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        try {
            insertMetadataRecords(uuid1, "classpath:canned/sample2.xml");
            insertMetadataRecords(uuid2, "classpath:canned/sample1.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);

            Assertions.assertEquals(2L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");
        }
        finally {
            deleteRecord(uuid2,uuid1);
        }
    }

    @Test
    public void verifyDeleteDocumentByUUID() throws IOException {
        String uuid1 = "830f9a83-ae6b-4260-a82a-24c4851f7119";
        String uuid2 = "06b09398-d3d0-47dc-a54a-a745319fbece";
        try {
            insertMetadataRecords(uuid1, "classpath:canned/sample2.xml");
            insertMetadataRecords(uuid2, "classpath:canned/sample3.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Assertions.assertEquals(2L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");

            // Only 2 doc in elastic, if we delete it then should be zero
            indexerService.deleteDocumentByUUID(uuid1);
            Assertions.assertEquals(1L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");

        }
        finally {
            deleteRecord(uuid1, uuid2);
        }
    }

    @Test
    public void verifyGetDocumentByUUID() throws IOException {
        String uuid = "7709f541-fc0c-4318-b5b9-9053aa474e0e";
        try {
            String expectedData = readResourceFile("classpath:canned/sample4_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample4.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = String.valueOf(Objects.requireNonNull(objectNodeHit.source()));

            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(test).toPrettyString();
            logger.info(actual);
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(uuid);
        }
    }
    /**
     * Alias is used to point which index is the current in use index
     */
    @Test
    public void verifyAlias() {
        var uuid = "7709f541-fc0c-4318-b5b9-9053aa474e0e";
        try {

            String expectedData = readResourceFile("classpath:canned/sample4_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample4.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);

            // alias can be used to get the document
            var objectHit = indexerService.getDocumentByUUID(uuid, INDEX_NAME);
            var source = String.valueOf(Objects.requireNonNull(objectHit.source()));
            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(source).toPrettyString();
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(uuid);
        }
    }

    @Test
    public void verifyAssociatedRecordIndexer() throws IOException{

        var targetRecordId = "4637bd9b-8fba-4a10-bf23-26a511e17042";
        var parentId = "a35d02d7-3bd2-40f8-b982-a0e30b64dc40";
        var siblingId = "0ede6b3d-8635-472f-b91c-56a758b4e091";
        var childId = "06b09398-d3d0-47dc-a54a-a745319fbece";

        String expectedData = readResourceFile("classpath:canned/associated/self.json");

        try {
            insertMetadataRecords(targetRecordId, "classpath:canned/associated/self.xml");
            insertMetadataRecords(parentId, "classpath:canned/associated/parent.xml");
            insertMetadataRecords(siblingId, "classpath:canned/associated/sibling.xml");
            insertMetadataRecords(childId, "classpath:canned/associated/child.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            var targetResult = indexerService.getDocumentByUUID(targetRecordId);
            String resultJson = String.valueOf(Objects.requireNonNull(targetResult.source()));

            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(resultJson).toPrettyString();
            logger.info(actual);
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(targetRecordId, parentId, siblingId, childId);
        }
    }

    /**
     * Some dataset can provide links to logos, this test is use to verify the logo links added correctly to the STAC,
     * this function is better test with docker image as it need to invoke some additional function where we need to
     * verify it works too.
     *
     * @throws IOException - If file not found
     */
    @Test
    public void verifyLogoLinkAddedOnIndex() throws IOException {
        String uuid = "2852a776-cbfc-4bc8-a126-f3c036814892";
        try {
            String expectedData = readResourceFile("classpath:canned/sample5_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample5.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null,true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = String.valueOf(Objects.requireNonNull(objectNodeHit.source()));

            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(test).toPrettyString();
            logger.info(actual);
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(uuid);
        }
    }
    /**
     * Verify we can add thumbnail link to STAC if exist
     * @throws IOException - If file not found
     */
    @Test
    public void verifyThumbnailLinkAddedOnIndex() throws IOException {
        String uuid = "e18eee85-c6c4-4be2-ac8c-930991cf2534";
        try {
            String expectedData = readResourceFile("classpath:canned/sample6_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample6.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = String.valueOf(Objects.requireNonNull(objectNodeHit.source()));

            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(test).toPrettyString();
            logger.info(actual);
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(uuid);
        }
    }
    /**
     * This test is use to make sure if the geonetwork return an empty thumbnails [] array, we are still ok,
     * in the related/5905b3eb-aad0-4f9c-a03e-a02fb3488082.json, the thumbnails: [] is empty
     */
    @Test
    public void verifyThumbnailLinkNullAdbbdedOnIndex() throws IOException {
        String uuid = "5905b3eb-aad0-4f9c-a03e-a02fb3488082";
        try {
            String expectedData = readResourceFile("classpath:canned/sample7_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample7.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null,true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = String.valueOf(Objects.requireNonNull(objectNodeHit.source()));
            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(test).toPrettyString();
            logger.info(actual);
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(uuid);
        }
    }

    @Test
    public void verifyExtractedVocabsFromActualRecord() throws IOException {
        String uuid = "fa93c66e-0e56-7e1d-e043-08114f8c1b76";
        try {
            insertMetadataRecords(uuid, "classpath:canned/sample11.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = String.valueOf(Objects.requireNonNull(objectNodeHit.source()));
            JsonNode rootNode = indexerObjectMapper.readTree(test);

            List<String> expectedParameterVocabs = Arrays.asList("oxygen", "alkalinity", "nutrient", "carbon", "salinity" );
            List<String> actualParameterVocabs = indexerObjectMapper.convertValue(rootNode.path("summaries").path("parameter_vocabs"), indexerObjectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            Assertions.assertEquals(expectedParameterVocabs.size(), actualParameterVocabs.size(), "ParameterVocabs not equals for sample11.");

            List<String> expectedPlatformVocabs = List.of("small boat", "Vessel");
            List<String> actualPlatformVocabs = indexerObjectMapper.convertValue(rootNode.path("summaries").path("platform_vocabs"), indexerObjectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            Assertions.assertEquals(expectedPlatformVocabs.size(), actualPlatformVocabs.size(), "PlatformVocabs not equals for sample11.");
        } finally {
            deleteRecord(uuid);
        }
    }

    /**
     * Verify extracted phrases from abstract (extractTokensFromDescription method)
     * @throws IOException - If file not found
     */
    @Test
    public void verifyAbstractPhrases() throws IOException {
        String uuid = "5905b3eb-aad0-4f9c-a03e-a02fb3488082";
        try {
            List<String> expected = Arrays.asList(
                    "community compostion colony", "community compostion", "two nikon dslr", "any coral reefs environment",
                    "diver rig", "shooting", "resulted extracting", "resolution", "building", "capture approximately photos",
                    "were distributed", "photos nadiral oblique", "community composition", "rapidbenthos different",
                    "high visibility", "dome ports", "prime", "information from close range", "lenses", "planar",
                    "diver scuba over", "community composition information", "community", "photogrammetry any coral",
                    "calssifying", "sustainably scale photogrammetry", "information from close", "size", "publication titled",
                    "applyed", "each gpc", "were selected", "metrics", "also recorded each", "orthomosaics method",
                    "automated segmentation multi", "two nikon", "second intervals full camera", "manual",
                    "oblique imagery captured", "lawn mowing swim", "composition", "assess performance", "segmentation",
                    "were captured using two", "passes swim", "spatially adjacent photos", "reefs surveyed compared",
                    "devlopping workflow", "close", "compared", "replicate", "between adjacent images", "approximately using",
                    "workflow described", "inch dome ports", "cm", "workflow automatically", "mounted", "classification allowing sustainably",
                    "spatially adjacent", "consisting", "were distributed across", "benthic images", "plots segmenting",
                    "information", "data extraction due", "inch", "standardized diver", "reefs environment we eveluated",
                    "communities from photogrammetric reconstruction", "captured using two nikon", "nadiral oblique",
                    "automatically extract community", "sites", "photogrammetry mointoring", "cameras were housed", "prior",
                    "level metrics", "gpc", "we eveluated", "rig photogrammetry workflow", "scale resultant models",
                    "area", "visibility", "segmentation classification", "underwater housings", "photogrammetric models underlying",
                    "extracting community compostion", "workflow automatically extract", "photogrammetric reconstruction sites",
                    "prior imaging", "segmentation classification allowing", "segmentation multi view", "scuba over",
                    "gpc incorporate bathymetric", "scale photogrammetry", "extraction due", "site imaged", "titled rapidbenthos",
                    "reefs turbid", "imaged using standardized", "distance cm", "longitudinal", "planar area",
                    "building rapidbenthos workflow", "sustainably scale", "perpendicular", "additional", "provided",
                    "back", "photogrammetric", "images replicate", "lens", "metrics colony", "research lies devlopping",
                    "data can", "passes", "passes additional", "captured", "reefs turbid inshore", "six gpcs",
                    "standardized", "high resolution", "over", "colony planar", "across depth", "lagoon all", "six",
                    "nikkor prime lens", "captured altitude", "recorded", "manual segmentation classification",
                    "colony frequency", "significance", "environment", "approximately", "using two nikon",
                    "single diver", "allowing sustainably scale", "rig photogrammetry", "mowing", "reef front flank",
                    "housed nauticam underwater", "replication size", "reconstruction sites were", "images figure",
                    "details provided gordon", "from high", "all sites were", "environments reef front", "colony level",
                    "from photogrammetric reconstruction", "composition information from close", "cameras mm", "accessed",
                    "habitats ranged", "rig", "imagery", "axes details provided", "model", "titled rapidbenthos automated segmentation",
                    "second intervals", "period", "large", "all sites were imaged", "nikon dslr cameras", "distributed",
                    "underlying", "coral reefs environment we", "classification", "colony planar area", "depth also recorded",
                    "both replication", "models underlying", "pixels were captured", "any coral", "incorporate bathymetric information",
                    "adjacent images", "swim pattern speed", "compostion colony", "flank back", "allowing sustainably",
                    "respectively", "distributed across", "diver", "captured using two", "housings", "included range",
                    "standardized diver rig photogrammetry", "were housed nauticam", "front flank back", "cameras",
                    "spatially adjacent photos respectively", "time faster than manual", "coral reef communities",
                    "recorded each gpc", "reef communities from photogrammetric", "mowing swim pattern consisting",
                    "imaged single", "range photogrammetry any", "dslr cameras", "area colony", "intervals full camera settings",
                    "camera", "than manual", "surveyed compared manual", "used ensured", "nauticam", "imagery captured",
                    "building rapidbenthos", "flank back lagoon", "were mounted", "speed used ensured",
                    "swim pattern consisting", "eveluated", "pattern consisting", "workflow applyed",
                    "method resulted extracting", "temporally spatially adjacent", "were selected assess", "lenses overlap",
                    "coral reefs", "reefal", "aluminium", "using lawn", "each site imaged", "over period", "coral reef",
                    "information model", "communities", "compared manual data", "nadiral", "environments", "automatically extract",
                    "rapidbenthos automated segmentation", "resolution benthic images", "environments reef", "replicate method presented",
                    "standardized diver rig", "applyed each", "performance rapidbenthos", "photos", "spanned depths from",
                    "second", "rapidbenthos workflow applyed", "from close range photogrammetry", "photogrammetric reconstruction",
                    "high", "turbid inshore reefs spanned", "extract community composition", "faster", "respectively figure six",
                    "using standardized diver", "coral", "publication", "between temporally", "than", "benthic constituents",
                    "different", "models underlying images", "selected", "research lies", "all", "mm", "settings", "time faster",
                    "workflow", "ranged from high visibility", "level", "scuba", "imaging scale", "lenses overlap between",
                    "from high visibility", "prior imaging scale", "each plots", "intervals", "manual data extraction due",
                    "between lenses", "planar area colony", "reefal environments reef", "were", "details provided", "depths from",
                    "overlap between adjacent images", "rapidbenthos automated", "intervals full", "camera settings", "our method",
                    "inshore reefs spanned depths", "each site", "speed used", "meters", "nikon", "sites were selected", "turbid",
                    "calssifying benthic", "shooting second", "approximately photos", "environmental conditions", "conditions reef",
                    "included", "visibility offshore", "all sites", "range photogrammetry", "replicate method", "view classification coral",
                    "adjacent images figure", "adjacent", "model building", "imaging scale resultant", "any", "surveyed", "plots", "mounted aluminium",
                    "spatially", "due", "were imaged using", "reconstruction", "using lawn mowing", "across depth gradient", "were captured",
                    "using two", "photogrammetry any", "intra reefal", "colony level metrics", "metrics colony planar",
                    "different environmental conditions", "lagoon all sites", "offshore reefs", "six gpcs were", "sites were imaged",
                    "mm nikkor prime", "spanned", "full camera", "housed nauticam", "models", "reef", "reefs spanned depths from",
                    "using", "environmental", "using standardized", "reefs spanned", "scuba over period", "multi view", "photogrammetry workflow described",
                    "range intra reefal", "cameras were", "each", "close range", "meters included", "manual segmentation",
                    "ranged from high", "underwater", "manual data", "due large", "conditions", "inch dome", "temporally spatially", "figure each", "oblique imagery",
                    "gpcs were distributed", "figure each site", "reefs environment", "close range photogrammetry", "size reefs", "automatically", "faster than",
                    "minutes capture", "ports", "two", "distributed across depth", "consisting longitudinal", "ports were", "reef habitats",
                    "were imaged", "mowing swim pattern", "pattern speed", "extracting community", "site prior", "colony frequency significance",
                    "photos respectively", "front flank", "photogrammetric models", "altitude approximately", "pixels were", "gradient",
                    "between temporally spatially", "classification allowing", "underlying images replicate", "information from", "gordon",
                    "faster than manual segmentation", "lagoon", "publication titled rapidbenthos", "single", "automated", "were captured using",
                    "calssifying benthic constituents", "depths", "faster than manual", "orthomosaics method resulted", "dslr", "axes details",
                    "bathymetric information", "resulted", "size reefs surveyed", "depth gradient", "cm between lenses", "incorporate",
                    "extract community", "nadiral oblique imagery", "model building rapidbenthos", "research", "pixels were captured using",
                    "housed", "frequency", "view", "reef communities", "aluminium rig", "cm between", "provided gordon", "flank",
                    "back lagoon", "details", "extract community composition information", "swim pattern", "from close", "view classification",
                    "reef front", "reefs", "high resolution benthic images", "imaging", "surveyed compared", "offshore",
                    "resolution benthic", "applyed each plots", "overlap", "extract", "scale resultant", "single diver scuba", "composition information from",
                    "incorporate bathymetric", "ports were mounted", "files data can", "between adjacent", "swim", "lagoon all sites were", "full", "inshore",
                    "photos respectively figure", "distance", "used ensured minimum", "scale photogrammetry mointoring", "data files",
                    "speed", "we", "nikkor prime", "community composition information from", "nikkor", "nikkor prime lens shooting", "ranged",
                    "high visibility offshore reefs", "lies", "ensured minimum", "presented publication", "different environmental",
                    "lies devlopping", "images figure each", "level metrics colony", "reefs environment we", "from photogrammetric", "figure",
                    "recorded each", "nauticam underwater", "were housed", "inshore reefs spanned", "depth also", "files data", "devlopping",
                    "communities from photogrammetric", "composition information", "spanned depths", "ensured", "area colony frequency", "time",
                    "titled", "also recorded", "automatically extract community composition", "oblique", "method resulted", "offshore reefs turbid",
                    "axes", "longitudinal passes", "extracting", "six gpcs were distributed", "multi", "capture approximately", "conditions reef habitats",
                    "camera settings described", "captured using", "mowing swim", "full camera settings described", "adjacent photos", "settings described",
                    "lawn mowing swim pattern", "between", "overlap between adjacent", "resultant", "images", "method", "any coral reefs", "turbid inshore",
                    "reef communities from", "gpcs", "extraction", "publication titled rapidbenthos automated", "additional perpendicular", "described gordon",
                    "sites were", "workflow described gordon", "rig distance", "imaged", "pattern speed used", "mointoring both", "accessed request",
                    "eveluated our", "mointoring both replication", "lawn", "between adjacent images figure", "rig photogrammetry workflow described",
                    "habitats", "high resolution benthic", "classification coral", "reef front flank back", "prime lens shooting", "pattern", "range",
                    "rapidbenthos", "dome", "our", "mointoring", "across", "resultant models", "reefs surveyed", "dome ports were", "coral reef communities from",
                    "benthic", "inshore reefs", "respectively figure", "communities from", "perpendicular passes swim", "replication", "bathymetric",
                    "segmentation multi", "gpcs were", "compostion colony level", "compostion", "full camera settings", "segmenting calssifying",
                    "segmenting calssifying benthic", "perpendicular passes", "reconstruction sites", "passes swim pattern", "we eveluated our",
                    "photogrammetry mointoring both", "site", "significance research", "depth", "large data", "assess", "multi view classification",
                    "settings described gordon", "approximately photos nadiral", "environment we eveluated", "selected assess",
                    "ranged from", "intervals full camera", "minutes capture approximately",
                    "rapidbenthos different environmental", "minimum", "back lagoon all", "eveluated our method",
                    "resulted extracting community", "data", "mm nikkor", "overlap between",
                    "manual data extraction", "photogrammetry workflow", "used", "between lenses overlap", "minimum overlap",
                    "ensured minimum overlap", "temporally", "frequency significance",
                    "presented", "each gpc incorporate", "reefs spanned depths", "adjacent photos respectively figure",
                    "from", "described", "housings inch", "diver rig photogrammetry", "turbid inshore reefs",
                    "range intra", "intra reefal environments", "plots segmenting calssifying", "intra", "each plots segmenting", "minutes", "large data files", "capture", "site prior imaging", "sustainably",
                    "figure six gpcs", "imaged using", "both", "underlying images", "data files data", "pixels", "prime lens", "high visibility offshore", "second intervals full", "than manual segmentation",
                    "environment we", "files", "lawn mowing", "from close range", "altitude", "request", "colony", "gpc incorporate", "photogrammetry", "link", "diver rig photogrammetry workflow", "scale",
                    "photos nadiral", "compared manual", "can", "data extraction", "can accessed", "constituents orthomosaics", "mm nikkor prime lens", "nauticam underwater housings",
                    "lens shooting", "adjacent photos respectively", "gradient site", "nikon dslr", "from high visibility offshore", "rapidbenthos workflow", "titled rapidbenthos automated", "method presented",
                    "figure six", "also", "coral reefs environment", "reefal environments", "method time", "allowing", "classification coral reef", "environmental conditions reef", "sites were imaged using",
                    "performance", "segmenting", "data can accessed", "diver scuba", "orthomosaics", "time faster than", "front", "visibility offshore reefs", "constituents",
                    "automated segmentation", "gpcs were distributed across"
            );

            insertMetadataRecords(uuid, "classpath:canned/sample7.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null,true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = Objects.requireNonNull(objectNodeHit.source()).toPrettyString();

            // Parse the JSON string into a JsonNode
            JsonNode rootNode = indexerObjectMapper.readTree(test);
            JsonNode abstractPhrasesNode = rootNode.path("search_suggestions").path("abstract_phrases");
            List<String> actual = indexerObjectMapper.convertValue(abstractPhrasesNode, indexerObjectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            logger.info(test);
            Assertions.assertEquals(expected.size(), actual.size(), "abstractPhrases not equals for sample 7. Uuid: " + uuid);
        }
        finally {
            deleteRecord(uuid);
        }
    }
    /**
     * Test case when AI service is available and returns both enhanced links and enhanced description
     * This test verifies that both AI link grouping and AI description enhancement are properly applied
     * during the indexing process in the service layer
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyAiServiceEnhancement() throws IOException {
        String uuid = "e18eee85-c6c4-4be2-ac8c-930991cf2534";

        // Mock the enhanced links returned by AI service
        List<LinkModel> enhancedLinks = Arrays.asList(
                LinkModel.builder()
                        .href("https://www.marine.csiro.au/data/trawler/survey_details.cfm?survey=IN2024_V01")
                        .rel("data")
                        .type("")
                        .title("{\"title\":\"MNF Data Trawler\",\"description\":\"Link to processed data and survey information (plans, summaries, etc.) via MNF Data Trawler\"}")
                        .aiGroup("Data Access")
                        .build(),
                LinkModel.builder()
                        .href("https://mnf.csiro.au/")
                        .rel("related")
                        .type("text/html")
                        .title("{\"title\":\"Marine National Facility\",\"description\":\"Link to the Marine National Facility Webpage\"}")
                        .aiGroup("Other")
                        .build(),
                LinkModel.builder()
                        .href("https://doi.org/10.25919/rdrt-bd71")
                        .rel("data")
                        .type("")
                        .title("{\"title\":\"Data Access Portal (DOI)\",\"description\":\"Link to this record at the CSIRO Data Access Portal\"}")
                        .aiGroup("Data Access")
                        .build(),
                LinkModel.builder()
                        .href("http://www.marine.csiro.au/data/underway/?survey=IN2024_V01")
                        .rel("data")
                        .type("")
                        .title("{\"title\":\"Underway Visualisation Tool\",\"description\":\"Link to visualisation tool for Near Real-Time Underway Data (NRUD)\"}")
                        .aiGroup("Data Access")
                        .build(),
                //there are some links that are not enhanced by AI
                LinkModel.builder()
                        .href("https://www.marine.csiro.au/data/trawler/survey_mapfile.cfm?survey=IN2024_V01&data_type=uwy")
                        .rel("preview")
                        .type("image")
                        .build(),
                LinkModel.builder()
                        .href("http://140.79.20.100:8080/geonetwork/srv/eng/catalog.search#/metadata/ff887cf9-18bb-464e-8bad-5dc6e0ad946b")
                        .rel("describedby")
                        .type("text/html")
                        .title("Full metadata link")
                        .build(),
                LinkModel.builder()
                        .href("https://i.creativecommons.org/l/by/4.0/88x31.png")
                        .rel("license")
                        .type("image/png")
                        .build(),
                LinkModel.builder()
                        .href("https://creativecommons.org/licenses/by/4.0/")
                        .rel("license")
                        .type("text/html")
                        .build()
        );

        // Mock the AI enhanced description
        String enhancedDescription = "This record describes the **End of Voyage (EOV)** data archive from the **Marine National Facility (MNF)** RV Investigator voyage **IN2024_V01**, titled \"Multidisciplinary Investigations of the Southern Ocean (MISO): linking physics, biogeochemistry, plankton, aerosols, clouds, and climate.\" The voyage took place between **January 02, 2024** and **March 05, 2024 (AEST)**, departing from **Hobart** and returning to **Fremantle**.\n\nFor further information please refer to the voyage documentation links.\n\nInstruments used and data collected include:\n\n### Regular measurements:\n- Lowered ADCP (LADCP)\n- Acoustic Doppler Current Profiler (ADCP; 75, 150 KHz)\n- Greenhouse Gas Analysers (Picarro)\n- Cloud Condensation Nuclei counter (CCN)\n- Condensation Particle Counters (CPC)\n- Disdrometer\n- Radon sensor\n- Scanning Mobility Particle Sizers (SMPS)\n- CTD\n- Hydrochemistry\n- Triaxus\n- Fisheries Echosounder (EK80)\n- Multibeam Echosounder (EM710, EM122)\n- Sub-bottom Profiler (SBP120)\n- GPS Positioning System\n- Doppler Velocity Log\n- Thermosalinographs (TSG)\n- Fluorometer\n- Oxygen Optode\n- pCO2\n- Multiangle Absorption Photometer (MAAP)\n- Ozone Sensor\n- Nephelometer\n- Atmospheric Temperature, Humidity, Pressure, Wind and Rain sensors\n- Photosynthetically Active Radiation (PAR) sensor\n- Precision Infrared Radiometer (PIR)\n- Precision Spectral Pyranometer (PSP)\n- Starboard and Portside Radiometers\n- Air Sampler\n- Ultra Short BaseLine Underwater Positioning System (USBL)\n- Weather Radar\n- Expendable Bathythermographs (XBTs).\n\n### Voyage-specific measurements:\n\n- **Black Carbon sensor (Aethalometer)**\n- **Mobility particle size spectrometer (MPSS)**\n- **Bongo Net**\n- **Chemical Ionisation Mass Spectrometer (CIMS)**\n- **Cloud Radar (BASTA)**\n- **Fast Repetition Rate Chlorophyll-a Fluorometer (FRRf)**\n- **Mini Micro-Pulse LIDAR (miniMPL)**\n- **Micro Rain Radar (MRR)**\n- **Neutral Cluster Air Ion Spectrometer (NAIS)**\n- **Proton-Transfer-Reaction Mass Spectrometry (PTR-MS)**\n- **Radiosondes**\n- **Cloud and Aerosol Backscatter Lidar (RMAN)**\n- **Stabilised Platform**\n- **Mercury Analyser (Tekran)**\n- **Time of Flight Aerosol Chemical Speciation Monitor (ToF-ACSM)**\n- **Water Vapor Radiometer (WVR)**\n- **Aerosol mass spectrometer (AMS)**\n- **Core Argo floats**\n- **Biogeochemical (BGC) Argo floats**\n- **Near-surface Drifters**\n- **In situ pumps (ISPs)**\n- **Ice Nucleating Particles (INPs)**\n- **Ozone Sensor**\n- **Trace Metal Aerosol Sampling**\n- **Trace Metal CTD Rosette and Bottles**\n- **Organic Sulfur Sequential Chemical Analysis Robot (OSSCAR)**\n- **Omics data and various biological data.**\n\nThe archive for the **IN2024_V01 EOV raw data** is curated by the **CSIRO National Collections and Marine Infrastructure (NCMI) Information and Data Centre (IDC)** in Hobart, with a permanent archive at the **CSIRO Data Access Portal** ([https://data.csiro.au/](https://data.csiro.au/)), providing access to voyage participants and processors of the data collected on the voyage.\n\nAll voyage documentation is available electronically to **MNF support** via the local network. Applications to access voyage documentation by non-CSIRO participants can be made via **data-requests-hf@csiro.au**.\n\nAll processed data from this voyage are made publicly available through the **MNF Data Trawler** (in the related links).";

        // Create mock AI response
        AiEnhancementResponse mockAiResponse = Mockito.mock(AiEnhancementResponse.class);

        // Set up AI service to be available and mock the combined enhancement
        when(dataDiscoveryAiService.isServiceAvailable()).thenReturn(true);
        when(dataDiscoveryAiService.enhanceWithAi(anyString(), anyList(), anyString(), anyString())).thenReturn(mockAiResponse);
        when(dataDiscoveryAiService.getEnhancedLinks(eq(mockAiResponse))).thenReturn(enhancedLinks);
        when(dataDiscoveryAiService.getEnhancedDescription(eq(mockAiResponse))).thenReturn(enhancedDescription);

        try {
            String expectedData = readResourceFile("classpath:canned/aienhancement/sample2_stac_ai_enhanced.json");

            insertMetadataRecords(uuid, "classpath:canned/aienhancement/sample2.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = String.valueOf(Objects.requireNonNull(objectNodeHit.source()));

            String expected = indexerObjectMapper.readTree(expectedData).toPrettyString();
            String actual = indexerObjectMapper.readTree(test).toPrettyString();
            logger.info(actual);
            JSONAssert.assertEquals(expected, actual, JSONCompareMode.STRICT);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            deleteRecord(uuid);
        }
    }
}
