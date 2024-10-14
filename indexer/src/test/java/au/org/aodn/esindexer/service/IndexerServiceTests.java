package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
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
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IndexerServiceTests extends BaseTestClass {

    @Autowired
    protected GeoNetworkServiceImpl geoNetworkService;

    @Qualifier("gn4ElasticsearchClient")
    ElasticsearchClient gn4ElasticsearchClient;

    @Autowired
    protected IndexerService indexerService;

    @Autowired
    protected ObjectMapper indexerObjectMapper;

    @Autowired
    protected ElasticSearchIndexService elasticSearchIndexService;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @BeforeAll
    public void setup() {
        // Update the server for geonetwork RESTful URL
        geoNetworkService.setServer(String.format("http://%s:%s",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT))
        );
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

            // The sample1 geometry have error [1:9695] failed to parse field [summaries.proj:geometry] of type [geo_shape]
            // ErrorCause: {"type":"illegal_argument_exception","reason":"Polygon self-intersection at lat=57.0 lon=-66.0"}
            //
            // So it will not insert correctly and result in 1 doc only
            Assertions.assertEquals(1L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");
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
     * @throws IOException
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

            List<String> expectedPlatformVocabs = List.of("small boat");
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
            List<String> expected = Arrays.asList("community compostion", "any coral reefs environment", "diver rig", "shooting", "resolution", "ldquo lawn", "building", "gordon et", "were distributed", "community composition", "high visibility", "dome ports", "prime", "information from close range", "lenses", "planar", "community composition information", "al", "community", "calssifying", "ldquo lawn mowing", "information from close", "size", "publication titled", "applyed", "each gpc", "were selected", "metrics", "two nikon", "second intervals full camera", "manual", "composition", "segmentation", "m using", "were captured using two", "spatially adjacent photos", "reefs surveyed compared", "close", "compared", "z axes details", "replicate", "between adjacent images", "workflow described", "inch dome ports", "cm", "mounted", "spatially adjacent", "consisting", "were distributed across", "ldquo", "benthic images", "plots segmenting", "information", "data extraction due", "inch", "standardized diver", "reefs environment we eveluated", "communities from photogrammetric reconstruction", "captured using two nikon", "automatically extract community", "sites", "photogrammetry mointoring", "cameras were housed", "prior", "level metrics", "gpc", "we eveluated", "publication titled ldquo", "rig photogrammetry workflow", "mowing rdquo swim pattern", "rdquo swim", "scale resultant models", "area", "visibility", "underwater housings", "extracting community compostion", "prior imaging", "scuba over", "et", "reconstruction rdquo", "extraction due", "longitudinal", "planar area", "sustainably scale", "perpendicular", "additional", "provided", "back", "titled ldquo", "photogrammetric", "lens", "data can", "passes", "captured", "six gpcs", "standardized", "high resolution", "over", "colony planar", "lagoon all", "six", "nikkor prime lens", "recorded", "colony frequency", "significance", "environment", "approximately", "using two nikon", "single diver", "rig photogrammetry", "et al", "mowing", "reef front flank", "images figure", "from high", "all sites were", "colony level", "from photogrammetric reconstruction", "composition information from close", "accessed", "rig", "imagery", "axes details provided", "model", "second intervals", "period", "large", "all sites were imaged", "titled ldquo rapidbenthos", "distributed", "underlying", "z axes", "coral reefs environment we", "classification", "rdquo", "colony planar area", "pixels were captured", "any coral", "incorporate bathymetric information", "adjacent images", "titled ldquo rapidbenthos ndash", "flank back", "respectively", "distributed across", "diver", "captured using two", "housings", "standardized diver rig photogrammetry", "front flank back", "cameras", "spatially adjacent photos respectively", "time faster than manual", "coral reef communities", "reef communities from photogrammetric", "dslr cameras", "intervals full camera settings", "camera", "than manual", "used ensured", "nauticam", "were mounted", "speed used ensured", "swim pattern consisting", "eveluated", "pattern consisting", "coral reefs", "reefal", "aluminium", "coral reef", "communities", "nadiral", "environments", "automatically extract", "resolution benthic images", "publication titled ldquo rapidbenthos", "standardized diver rig", "photos", "spanned depths from", "second", "from close range photogrammetry", "photogrammetric reconstruction", "high", "turbid inshore reefs spanned", "extract community composition", "faster", "coral", "publication", "between temporally", "than", "benthic constituents", "different", "selected", "research lies", "all", "mm", "settings", "time faster", "workflow", "ranged from high visibility", "level", "scuba", "lawn mowing rdquo", "from high visibility", "each plots", "intervals", "manual data extraction due", "between lenses", "were", "details provided", "depths from", "overlap between adjacent images", "intervals full", "camera settings", "rapidbenthos ndash", "our method", "inshore reefs spanned depths", "each site", "speed used", "meters", "nikon", "sites were selected", "turbid", "calssifying benthic", "environmental conditions", "included", "visibility offshore", "all sites", "range photogrammetry", "adjacent images figure", "adjacent", "model building", "any", "surveyed", "plots", "spatially", "due", "were imaged using", "reconstruction", "were captured", "ldquo lawn mowing rdquo", "using two", "intra reefal", "colony level metrics", "different environmental conditions", "lagoon all sites", "photogrammetric reconstruction rdquo", "offshore reefs", "six gpcs were", "sites were imaged", "mm nikkor prime", "spanned", "full camera", "models", "reef", "reefs spanned depths from", "using", "environmental", "reefs spanned", "multi view", "photogrammetry workflow described", "cameras were", "each", "close range", "manual segmentation", "ranged from high", "underwater", "manual data", "conditions", "inch dome", "oblique imagery", "gpcs were distributed", "reefs environment", "close range photogrammetry", "automatically", "faster than", "ports", "two", "reef habitats", "were imaged", "mowing rdquo", "extracting community", "site prior", "photos respectively", "ldquo rapidbenthos", "front flank", "photogrammetric models", "pixels were", "gradient", "classification allowing", "information from", "gordon", "faster than manual segmentation", "lagoon", "single", "automated", "were captured using", "ndash automated segmentation", "calssifying benthic constituents", "depths", "faster than manual", "dslr", "axes details", "bathymetric information", "resulted", "depth gradient", "cm between lenses", "incorporate", "extract community", "ldquo rapidbenthos ndash automated", "research", "pixels were captured using", "housed", "frequency", "view", "reef communities", "aluminium rig", "cm between", "flank", "z axes details provided", "details", "extract community composition information", "swim pattern", "from close", "view classification", "reef front", "reefs", "high resolution benthic images", "imaging", "surveyed compared", "offshore", "resolution benthic", "overlap", "extract", "scale resultant", "composition information from", "incorporate bathymetric", "between adjacent", "swim", "lagoon all sites were", "full", "inshore", "photos respectively figure", "distance", "data files", "speed", "we", "nikkor prime", "community composition information from", "nikkor", "x y", "nikkor prime lens shooting", "ranged", "high visibility offshore reefs", "lies", "different environmental", "reefs environment we", "from photogrammetric", "figure", "nauticam underwater", "were housed", "inshore reefs spanned", "devlopping", "communities from photogrammetric", "composition information", "spanned depths", "ensured", "time", "titled", "also recorded", "automatically extract community composition", "oblique", "method resulted", "rapidbenthos ndash automated", "axes", "longitudinal passes", "extracting", "six gpcs were distributed", "multi", "capture approximately", "mowing rdquo swim", "camera settings described", "captured using", "full camera settings described", "adjacent photos", "settings described", "between", "overlap between adjacent", "resultant", "images", "method", "any coral reefs", "turbid inshore", "reef communities from", "gpcs", "extraction", "sites were", "imaged", "mointoring both", "ndash", "lawn", "between adjacent images figure", "rig photogrammetry workflow described", "habitats", "high resolution benthic", "reef front flank back", "prime lens shooting", "pattern", "range", "rapidbenthos", "dome", "lawn mowing rdquo swim", "our", "mointoring", "across", "resultant models", "reefs surveyed", "coral reef communities from", "benthic", "inshore reefs", "respectively figure", "communities from", "replication", "bathymetric", "gpcs were", "compostion", "full camera settings", "perpendicular passes", "photogrammetry mointoring both", "site", "depth", "large data", "assess", "multi view classification", "environment we eveluated", "ranged from", "intervals full camera", "from photogrammetric reconstruction rdquo", "minimum", "data", "mm nikkor", "overlap between", "manual data extraction", "photogrammetry workflow", "used", "minimum overlap", "temporally", "presented", "reefs spanned depths", "adjacent photos respectively figure", "from", "described", "diver rig photogrammetry", "turbid inshore reefs", "intra reefal environments", "rdquo swim pattern consisting", "intra", "each plots segmenting", "ndash automated", "minutes", "large data files", "capture", "site prior imaging", "sustainably", "imaged using", "both", "underlying images", "pixels", "prime lens", "high visibility offshore", "second intervals full", "than manual segmentation", "environment we", "files", "lawn mowing", "rdquo swim pattern", "from close range", "ldquo rapidbenthos ndash", "altitude", "request", "colony", "photogrammetry", "link", "diver rig photogrammetry workflow", "scale", "photos nadiral", "rapidbenthos ndash automated segmentation", "can", "data extraction", "gordon et al", "mm nikkor prime lens", "nauticam underwater housings", "lens shooting", "adjacent photos respectively", "from high visibility offshore", "rapidbenthos workflow", "method presented", "also", "coral reefs environment", "reefal environments", "allowing", "sites were imaged using", "performance", "segmenting", "orthomosaics", "time faster than", "front", "visibility offshore reefs", "constituents", "automated segmentation", "gpcs were distributed across");

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
}
