package au.org.aodn.esindexer.service;

import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import au.org.aodn.esindexer.model.MockServer;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
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

    @MockitoSpyBean
    protected IndexerMetadataServiceImpl indexerService;

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
    public void verifyAlias() throws IOException {
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
            clearElasticIndex(INDEX_NAME);
            deleteRecord(uuid);
        }
    }

    /**
     * Test that running index is preserved when indexing fails midway,
     * allowing resume with beginWithUuid parameter.
     */
    @Test
    public void verifyRunningIndexAndResumeFeature() throws IOException {
        // UUIDs from sample1.xml to sample11.xml
        var uuid1 = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";   // sample1
        var uuid2 = "830f9a83-ae6b-4260-a82a-24c4851f7119";   // sample2
        var uuid3 = "06b09398-d3d0-47dc-a54a-a745319fbece";   // sample3
        var uuid4 = "7709f541-fc0c-4318-b5b9-9053aa474e0e";   // sample4
        var uuid5 = "2852a776-cbfc-4bc8-a126-f3c036814892";   // sample5
        var uuid6 = "e18eee85-c6c4-4be2-ac8c-930991cf2534";   // sample6
        var uuid7 = "5905b3eb-aad0-4f9c-a03e-a02fb3488082";   // sample7
        var uuid8 = "1880cd63-d0f9-42e0-b073-7082527945f2";   // sample8
        var uuid9 = "1e13ab6e-e546-44f2-a007-061c2815268a";   // sample9
        var uuid10 = "ae86e2f5-eaaf-459e-a405-e654d85adb9c";  // sample10
        var uuid11 = "fa93c66e-0e56-7e1d-e043-08114f8c1b76";  // sample11

        var allUuids = List.of(uuid1, uuid2, uuid3, uuid4, uuid5, uuid6, uuid7, uuid8, uuid9, uuid10, uuid11);
        var runningAliasName = INDEX_NAME + "-running";

        try {
            insertMetadataRecords(uuid1, "classpath:canned/sample1.xml");
            insertMetadataRecords(uuid2, "classpath:canned/sample2.xml");
            insertMetadataRecords(uuid3, "classpath:canned/sample3.xml");
            insertMetadataRecords(uuid4, "classpath:canned/sample4.xml");
            insertMetadataRecords(uuid5, "classpath:canned/sample5.xml");
            insertMetadataRecords(uuid6, "classpath:canned/sample6.xml");
            insertMetadataRecords(uuid7, "classpath:canned/sample7.xml");
            insertMetadataRecords(uuid8, "classpath:canned/sample8.xml");
            insertMetadataRecords(uuid9, "classpath:canned/sample9.xml");
            insertMetadataRecords(uuid10, "classpath:canned/sample10.xml");
            insertMetadataRecords(uuid11, "classpath:canned/sample11.xml");

            // Create a callback that throws an exception on the first onProgress call
            var failingCallback = createFailingCallback();

            // First indexing attempt - should fail after some documents are indexed
            RuntimeException thrownException = null;
            try {
                indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, failingCallback);
            } catch (RuntimeException e) {
                logger.info("Expected exception caught: {}", e.getMessage());
                thrownException = e;
            }

            // Verify that an exception was thrown
            Assertions.assertNotNull(thrownException, "An exception should have been thrown");
            logger.info("Exception message: {}", thrownException.getMessage());

            // Verify running index exists with the -running alias
            var runningIndexName = elasticSearchIndexService.getIndexNameFromAlias(runningAliasName);
            logger.info("Running index name after failure: {}", runningIndexName);
            Assertions.assertNotNull(runningIndexName, "Running index should exist after failed indexing");

            // Now resume indexing
            // Because our "beginWithUuid" feature starts after the specified UUID, we can use this fake UUID to ensure all records are indexed
            var fakeUuid = "00000000-0000-0000-0000-000000000000";
            indexerService.indexAllMetadataRecordsFromGeoNetwork(fakeUuid, true, null);

            // Verify all documents are indexed and the main index can be queried
            Assertions.assertEquals(allUuids.size(), elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "All documents should be indexed after resume");

            // Verify the running index has been cleaned up
            var leftoverRunningIndex = elasticSearchIndexService.getIndexNameFromAlias(runningAliasName);
            Assertions.assertNull(leftoverRunningIndex, "Running index should be cleaned up after successful indexing");

            // index again, and fail it
            try {
                indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, failingCallback);
            } catch (RuntimeException e) {
                logger.info("Expected exception caught on second indexing: {}", e.getMessage());
            }

            // the in-use index should not be affected by the failed indexing, and should still be able to query all documents
            Assertions.assertEquals(allUuids.size(), elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "All documents should still be indexed after second failed indexing");


        } finally {
            // Clean up both the main index and any running index
            clearElasticIndex(INDEX_NAME);

            // Also try to clean up any leftover running index
            try {
                var leftoverRunningIndex = elasticSearchIndexService.getIndexNameFromAlias(runningAliasName);
                if (leftoverRunningIndex != null) {
                    elasticSearchIndexService.deleteIndexStore(leftoverRunningIndex);
                }
            } catch (Exception e) {
                logger.debug("No leftover running index to clean up: {}", e.getMessage());
            }

            // Delete all records from GeoNetwork
            deleteRecord(allUuids.toArray(new String[0]));
        }
    }

    // please keep getting the failing callback via function (not a shared instance) as we don't want any unexpected behavior caused by shared instance.
    private IndexService.@NotNull Callback createFailingCallback() {
        return new IndexService.Callback() {
            @Override
            public void onProgress(Object update) {
                String message = update != null ? update.toString() : "";
                logger.info("Callback onProgress: {}", message);
                logger.info(">>> Throwing exception on first onProgress call <<<");
                throw new RuntimeException("Simulated failure on first indexing");
            }

            @Override
            public void onComplete(Object result) {
                logger.info("Callback onComplete: {}", result);
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error("Callback onError: {}", throwable.getMessage());
            }
        };
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
     * Too big token generated will cause circuit break and crash Elastic search, we have set a limit in the
     * schema to only consider the first n token in the description, then we apply shingle without created output_unigrams
     * that is unigrams is excluded. This will make the output much smaller. The hard code schema value is a number with
     * trial and error that fix most existing test case. It is a tradeoff that we cannot handle too big desc given the memory
     * constraint.
     * Text: quick brown fox jumps
     * Becomes:
     * unigrams: quick, brown, fox, jumps
     * bigrams: quick brown, brown fox, fox jumps
     * trigrams: quick brown fox, brown fox jumps
     * four-grams: quick brown fox jumps
     * @throws IOException - If file not found
     */
    @Test
    public void verifyTokenGeneratedMaxLimit() throws IOException {
        String uuid = "fa93c66e-0e56-7e1d-e043-08114f8c1b76";
        try {
            // The doc is not important, it is just use to create the index given the schema
            insertMetadataRecords(uuid, "classpath:canned/sample11.xml");
            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);

            // Now we verify the limit
            String bigDesc = """
                    William Shakespeare was the son of John Shakespeare, an alderman and a successful glover (glove-maker)
                    originally from Snitterfield in Warwickshire, and Mary Arden, the daughter of an affluent landowning family
                    that was influential in the Recusant Catholic community.[3][4][5] He was born in Stratford-upon-Avon,
                    where he was baptised on 26 April 1564. His date of birth is unknown but is traditionally observed
                    on 23 April, Saint George's Day.[1] This date, which can be traced to William Oldys and George Steevens,
                    has proved appealing to biographers because Shakespeare died on the same date in 1616.[6][7]
                    He was the third of eight children, and the eldest surviving son.[8]

                    Although no attendance records for the period survive, most biographers agree that Shakespeare was
                    probably educated at the King's New School in Stratford,[9][10][11] a free school chartered in 1553,
                    [12] about a quarter-mile (400 m) from his home. Grammar schools varied in quality during the
                    Elizabethan era, but grammar school curricula were largely similar: the basic Latin text was
                    standardised by royal decree,[13][14] and the school would have provided an intensive education
                    in grammar based upon Latin classical authors.[15]

                    At the age of 18, Shakespeare married 26-year-old Anne Hathaway. The consistory court of the Diocese
                    of Worcester issued a marriage licence on 27 November 1582. The next day, two of Hathaway's neighbours
                    posted bonds guaranteeing that no lawful claims impeded the marriage.[16] The ceremony may
                    have been arranged in some haste; the Worcester chancellor allowed the marriage banns to be read once
                    instead of the usual three times.[17][18] Six months after the marriage, Anne gave birth to a daughter,
                    Susanna, baptised 26 May 1583.[19] Twins, son Hamnet and daughter Judith, followed almost two years
                    later and were baptised 2 February 1585.[20] Hamnet died of unknown causes at the age of 11 and was
                    buried 11 August 1596.[21]
                    Shakespeare's coat of arms, from the 1602 book The book of coates and creasts. Promptuarium armorum.
                    It features spears as a pun on the family name.[d]

                    After the birth of the twins, Shakespeare left few historical traces until he is mentioned as part
                    of the London theatre scene in 1592. The exception is the appearance of his name in the "complaints
                    bill" of a law case before the Queen's Bench court at Westminster dated Michaelmas Term 1588 and
                    9 October 1589.[22] Scholars refer to the years between 1585 and 1592 as Shakespeare's "lost years".
                    [23] Biographers attempting to account for this period have reported many apocryphal stories.
                    Nicholas Rowe, Shakespeare's first biographer, recounted a Stratford legend that Shakespeare fled
                    the town for London to escape prosecution for deer poaching in the estate of local squire Thomas Lucy.
                    Shakespeare is also supposed to have taken his revenge on Lucy by writing a scurrilous ballad about him.
                    [24][25] Another 18th-century story has Shakespeare starting his theatrical career minding the horses
                    of theatre patrons in London.[26] John Aubrey reported that Shakespeare had been a country schoolmaster.
                    [27] Some 20th-century scholars suggested that Shakespeare may have been employed as a schoolmaster by
                    Alexander Hoghton of Lancashire, a Catholic landowner who named a certain "William Shakeshafte" in
                    his will.[28][29] Little evidence substantiates such stories other than hearsay collected after
                    his death, and Shakeshafte was a common name in the Lancashire area.[30][31]
                    London and theatrical career

                    It is not known definitively when Shakespeare began writing, but contemporary allusions and records
                    of performances show that several of his plays were on the London stage by 1592.[32] By then, he was
                    sufficiently known in London to be attacked in print by the playwright Robert Greene in his Groats-Worth of Wit from that year:

                        ... there is an upstart Crow, beautified with our feathers, that with his Tiger's heart wrapped
                        in a Player's hide, supposes he is as well able to bombast out a blank verse as the best of you:
                        and being an absolute Johannes factotum, is in his own conceit the only Shake-scene in a country.[33]

                    Scholars differ on the exact meaning of Greene's words,[33][34] but most agree that Greene was accusing
                    Shakespeare of reaching above his rank in trying to match such university-educated writers as Christopher
                    Marlowe, Thomas Nashe and Greene himself (the so-called "University Wits").[35] The italicised phrase
                    parodying the line "Oh, tiger's heart wrapped in a woman's hide" from Shakespeare's Henry VI, Part 3,
                    along with the pun "Shake-scene", clearly identify Shakespeare as Greene's target. As used here, Johannes
                    Factotum ("Jack of all trades") refers to a second-rate tinkerer with the work of others, rather than
                    the more common "universal genius".[33][36]

                    Greene's attack is the earliest surviving mention of Shakespeare's work in the theatre. Biographers suggest
                    that his career may have begun any time from the mid-1580s to just before Greene's remarks.[37][38][39]
                    After 1594 Shakespeare's plays were performed at The Theatre, in Shoreditch, only by the Lord Chamberlain's Men,
                    a company owned by a group of players, including Shakespeare, that soon became the leading playing company in
                    London.[40] After the death of Queen Elizabeth in 1603, the company was awarded a royal patent by the
                    new King James I, and changed its name to the King's Men.[41]

                        All the world's a stage,
                        and all the men and women merely players:
                        they have their exits and their entrances;
                        and one man in his time plays many parts ...

                    —As You Like It, Act II, Scene 7, 139–142[42]

                    In 1599 a partnership of members of the company built their own theatre on the south bank of the
                    River Thames, which they named the Globe. In 1608 the partnership also took over the Blackfriars
                    indoor theatre. Extant records of Shakespeare's property purchases and investments indicate that
                    his association with the company made him a wealthy man,[43] and in 1597 he bought the second-largest
                    house in Stratford, New Place, and in 1605 invested in a share of the parish tithes in Stratford.[44]

                    Some of Shakespeare's plays were published in quarto editions, beginning in 1594, and by 1598 his
                    name had become a selling point and began to appear on the title pages.[45][46][47] Shakespeare
                    continued to act in his own and other plays after his success as a playwright. The 1616 edition of
                    Ben Jonson's Works names him on the cast lists for Every Man in His Humour (1598) and Sejanus His
                    Fall (1603).[48] The absence of his name from the 1605 cast list for Jonson's Volpone is taken by
                    some scholars as a sign that his acting career was nearing its end.[37] The First Folio of 1623,
                    however, lists Shakespeare as one of "the Principal Actors in all these Plays", some of which were
                    first staged after Volpone, although one cannot know for certain which roles he played.[49] In 1610,
                    John Davies of Hereford wrote that "good Will" played "kingly" roles.[50] In 1709 Rowe passed down a
                    tradition that Shakespeare played the ghost of Hamlet's father.[51] Later traditions maintain that he
                    also played Adam in As You Like It, and the Chorus in Henry V,[52][53] though scholars doubt the
                    sources of that information.[54]

                    Later years and death
                    Shakespeare's funerary monument in Stratford-upon-Avon

                    Nicholas Rowe was the first biographer to record the tradition, repeated by Samuel Johnson, that Shakespeare
                    retired to Stratford "some years before his death".[60][61] He was still working as an actor in London in 1608;
                    in an answer to the sharers' petition in 1635, Cuthbert Burbage stated that after purchasing the lease of the
                    Blackfriars Theatre in 1608 from Henry Evans, the King's Men "placed men players" there, "which were Heminges,
                    Condell, Shakespeare, etc.".[62] However, it is perhaps relevant that the bubonic plague raged in London throughout
                    1609.[63][64] The London public playhouses were repeatedly closed during extended outbreaks of the plague
                    (a total of over 60 months closure between May 1603 and February 1610),[65] which meant there was often no acting work.
                    Retirement from all work was uncommon at that time.[66] Shakespeare continued to visit London during the years 1611–1614.[60]
                    In 1612 he was called as a witness in Bellott v Mountjoy, a court case concerning the marriage settlement of Mountjoy's daughter,
                    Mary.[67][68] In March 1613 he bought a gatehouse in the former Blackfriars priory;[69] and from November 1614 he was
                    in London for several weeks with his son-in-law, John Hall.[70] After 1610 Shakespeare wrote fewer plays, and none
                    are attributed to him after 1613.[71] His last three plays were collaborations, probably with John Fletcher,[72]
                    who succeeded him as the house playwright of the King's Men. He retired in 1613, before the Globe
                    Theatre burned down during the performance of Henry VIII on 29 June.[71]

                    Shakespeare died on 23 April 1616, at the age of 52.[e] He died within a month of signing his will, a document which
                    he begins by describing himself as being in "perfect health". No extant contemporary source explains how or why he died.
                    Half a century later, John Ward, the vicar of Stratford, wrote in his notebook: "Shakespeare, Drayton, and Ben Jonson
                    had a merry meeting and, it seems, drank too hard, for Shakespeare died of a fever there contracted",[74][75] not an
                    impossible scenario since Shakespeare knew Jonson and Michael Drayton. Of the tributes from fellow authors, one refers
                    to his relatively sudden death: "We wondered, Shakespeare, that thou went'st so soon / From the world's stage to the
                    grave's tiring room."[76][f]
                    """;

            Set<String> token = ((IndexerMetadataServiceImpl)indexerService).extractTokensFromDescription(bigDesc, INDEX_NAME);
            Assertions.assertTrue(token.size() <= 1500, "Should not generate big token given larger desc");
        }
        finally {
            deleteRecord(uuid);
        }
    }
}
