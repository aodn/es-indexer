package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    protected IndexerServiceImpl indexerService;

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
            deleteRecord(uuid1);
            deleteRecord(uuid2);
        }
    }
    /**
     * Read the function implementation on why need to insert 1 docs
     * @throws IOException Not expected to throws
     */
    @Test
    public void verifyGeoNetworkInstanceReinstalled() throws IOException {
        String uuid = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        try {
            insertMetadataRecords(uuid, "classpath:canned/sample1.xml");
            Assertions.assertTrue(indexerService.isGeoNetworkInstanceReinstalled(1), "New installed");
        }
        finally {
            deleteRecord(uuid);
        }
    }

    @Test
    public void verifyGetDocumentCount() throws IOException {
        String uuid1 = "830f9a83-ae6b-4260-a82a-24c4851f7119";
        String uuid2 = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        try {
            insertMetadataRecords(uuid1, "classpath:canned/sample2.xml");
            insertMetadataRecords(uuid2, "classpath:canned/sample1.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);

            // The sample1 geometry have error [1:9695] failed to parse field [summaries.proj:geometry] of type [geo_shape]
            // ErrorCause: {"type":"illegal_argument_exception","reason":"Polygon self-intersection at lat=57.0 lon=-66.0"}
            //
            // So it will not insert correctly and result in 1 doc only
            Assertions.assertEquals(1L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");
        }
        finally {
            deleteRecord(uuid2);
            deleteRecord(uuid1);
        }
    }

    @Test
    public void verifyDeleteDocumentByUUID() throws IOException {
        String uuid1 = "830f9a83-ae6b-4260-a82a-24c4851f7119";
        String uuid2 = "06b09398-d3d0-47dc-a54a-a745319fbece";
        try {
            insertMetadataRecords(uuid1, "classpath:canned/sample2.xml");
            insertMetadataRecords(uuid2, "classpath:canned/sample3.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            Assertions.assertEquals(2L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");

            // Only 2 doc in elastic, if we delete it then should be zero
            indexerService.deleteDocumentByUUID(uuid1);
            Assertions.assertEquals(1L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");

        }
        finally {
            deleteRecord(uuid1);
            deleteRecord(uuid2);
        }
    }

    @Test
    public void verifyGetDocumentByUUID() throws IOException {
        String uuid = "7709f541-fc0c-4318-b5b9-9053aa474e0e";
        try {
            String expected = readResourceFile("classpath:canned/sample4_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample4.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = Objects.requireNonNull(objectNodeHit.source()).toPrettyString();
            Assertions.assertEquals(indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test), "Stac not equals for sample 4. Uuid:" + uuid);
        }
        finally {
            deleteRecord(uuid);
        }
    }

    @Test
    public void verifyAssociatedRecordIndexer() throws IOException{

        var targetRecordId = "4637bd9b-8fba-4a10-bf23-26a511e17042";
        var parentId = "a35d02d7-3bd2-40f8-b982-a0e30b64dc40";
        var siblingId = "0ede6b3d-8635-472f-b91c-56a758b4e091";
        var childId = "06b09398-d3d0-47dc-a54a-a745319fbece";

        String expectedJson = readResourceFile("classpath:canned/associated/self.json");

        try {
            insertMetadataRecords(targetRecordId, "classpath:canned/associated/self.xml");
            insertMetadataRecords(parentId, "classpath:canned/associated/parent.xml");
            insertMetadataRecords(siblingId, "classpath:canned/associated/sibling.xml");
            insertMetadataRecords(childId, "classpath:canned/associated/child.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            var targetResult = indexerService.getDocumentByUUID(targetRecordId);
            String resultJson = Objects.requireNonNull(targetResult.source()).toPrettyString();
            Assertions.assertEquals(indexerObjectMapper.readTree(expectedJson), indexerObjectMapper.readTree(resultJson), "stac not equals for associated/self.json");
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
            String expected = readResourceFile("classpath:canned/sample5_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample5.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = Objects.requireNonNull(objectNodeHit.source()).toPrettyString();
            Assertions.assertEquals(indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test), "Stac not equals for sample 5. Uuid: " + uuid);
        }
        finally {
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
            String expected = readResourceFile("classpath:canned/sample6_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample6.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = Objects.requireNonNull(objectNodeHit.source()).toPrettyString();
            Assertions.assertEquals(indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test), "Stac not equals for sample 6. Uuid: " + uuid);
        }
        finally {
            deleteRecord(uuid);
        }
    }
    /**
     * This test is use to make sure if the geonetwork return an empty thumbnails [] array, we are still ok,
     * in the related/5905b3eb-aad0-4f9c-a03e-a02fb3488082.json, the thumbnails: [] is empty
     * @throws IOException
     */
    @Test
    public void verifyThumbnailLinkNullAddedOnIndex() throws IOException {
        String uuid = "5905b3eb-aad0-4f9c-a03e-a02fb3488082";
        try {
            String expected = readResourceFile("classpath:canned/sample7_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample7.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = Objects.requireNonNull(objectNodeHit.source()).toPrettyString();
            logger.info(test);
            Assertions.assertEquals(indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test), "Stac not equals for sample 7. Uuid: " + uuid);
        }
        finally {
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
            List<String> expected = Arrays.asList("community compostion", "shooting", "resolution", "ldquo lawn", "building", "were distributed", "community composition", "rapidbenthos different", "high visibility", "information from close range", "lenses", "diver scuba over", "community", "photogrammetry any coral", "calssifying", "information from close", "size", "each gpc", "metrics", "also recorded each", "manual", "assess performance", "m using", "were captured using two", "reefs surveyed compared", "devlopping workflow", "close", "z axes details", "workflow automatically", "m", "classification allowing sustainably", "spatially adjacent", "consisting", "ldquo", "benthic images", "plots segmenting", "x", "y", "z", "information", "data extraction due", "communities from photogrammetric reconstruction", "captured using two nikon", "automatically extract community", "level metrics", "gpc", "rig photogrammetry workflow", "mowing rdquo swim pattern", "scale resultant models", "area", "visibility", "underwater housings", "extracting community compostion", "prior imaging", "segmentation classification allowing", "gpc incorporate bathymetric", "reconstruction rdquo", "site imaged", "reefs turbid", "imaged using standardized", "longitudinal", "y z axes", "provided", "photogrammetric", "lens", "reefs turbid inshore", "six gpcs", "standardized", "high resolution", "colony planar", "lagoon all", "reconstruction rdquo sites", "nikkor prime lens", "recorded", "manual segmentation classification", "significance", "environment", "single diver", "allowing sustainably scale", "rig photogrammetry", "et al", "reef front flank", "images figure", "details provided gordon", "from high", "colony level", "from photogrammetric reconstruction", "composition information from close", "accessed", "habitats ranged", "rig", "axes details provided", "rdquo sites", "second intervals", "period", "distributed", "underlying", "z axes", "coral reefs environment we", "colony planar area", "m using ldquo", "both replication", "pixels were captured", "incorporate bathymetric information", "swim pattern speed", "allowing sustainably", "respectively", "captured using two", "housings", "standardized diver rig photogrammetry", "front flank back", "cameras", "spatially adjacent photos respectively", "coral reef communities", "recorded each gpc", "imaged single", "range photogrammetry any", "area colony", "than manual", "used ensured", "nauticam", "building rapidbenthos", "pattern consisting", "temporally spatially adjacent", "coral reefs", "reefal", "over period", "communities", "compared manual data", "environments", "automatically extract", "resolution benthic images", "replicate method presented", "standardized diver rig", "spanned depths from", "rapidbenthos workflow applyed", "photogrammetric reconstruction", "coral", "between temporally", "benthic constituents", "models underlying images", "all", "settings", "lawn mowing rdquo", "imaging scale", "from high visibility", "intervals", "manual data extraction due", "between lenses", "planar area colony", "were", "overlap between adjacent images", "intervals full", "our method", "meters", "turbid", "calssifying benthic", "adjacent images figure", "adjacent", "any", "plots", "across depth gradient", "ldquo lawn mowing rdquo", "using two", "using ldquo", "colony level metrics", "rdquo sites were", "different environmental conditions", "offshore reefs", "six gpcs were", "full camera", "housed nauticam", "models", "reef", "using", "environmental", "reefs spanned", "scuba over period", "range intra reefal", "each", "close range", "meters included", "manual data", "temporally spatially", "gpcs were distributed", "reefs environment", "close range photogrammetry", "size reefs", "automatically", "two", "distributed across depth", "were imaged", "mowing rdquo", "extracting community", "colony frequency significance", "ldquo rapidbenthos", "front flank", "altitude approximately", "gradient", "between temporally spatially", "underlying images replicate", "information from", "faster than manual segmentation", "automated", "were captured using", "ndash automated segmentation", "calssifying benthic constituents", "orthomosaics method resulted", "dslr", "axes details", "resulted", "size reefs surveyed", "depth gradient", "cm between lenses", "incorporate", "model building rapidbenthos", "research", "housed", "view", "y z", "aluminium rig", "cm between", "provided gordon", "using ldquo lawn", "flank", "z axes details provided", "swim pattern", "from close", "high resolution benthic images", "imaging", "surveyed compared", "overlap", "incorporate bathymetric", "ports were mounted", "between adjacent", "swim", "full", "inshore", "distance", "resultant models x", "data files", "nikkor prime", "community composition information from", "nikkor", "lies devlopping", "reefs environment we", "from photogrammetric", "recorded each", "depth also", "files data", "devlopping", "communities from photogrammetric", "composition information", "spanned depths", "ensured", "area colony frequency", "time", "titled", "also recorded", "automatically extract community composition", "longitudinal passes", "extracting", "six gpcs were distributed", "multi", "capture approximately", "conditions reef habitats", "camera settings described", "full camera settings described", "adjacent photos", "resultant", "images", "gpcs", "extraction", "sites were", "imaged", "mointoring both", "eveluated our", "lawn", "between adjacent images figure", "rig photogrammetry workflow described", "high resolution benthic", "prime lens shooting", "pattern", "dome", "mointoring", "dome ports were", "coral reef communities from", "communities from", "perpendicular passes swim", "bathymetric", "segmentation multi", "gpcs were", "compostion", "full camera settings", "perpendicular passes", "passes swim pattern", "photogrammetry mointoring both", "settings described gordon", "environment we eveluated", "eveluated our method", "mm nikkor", "overlap between", "used", "minimum overlap", "frequency significance", "presented", "each gpc incorporate", "reefs spanned depths", "described", "turbid inshore reefs", "range intra", "intra reefal environments", "intra", "ndash automated", "minutes", "large data files", "site prior imaging", "both", "pixels", "prime lens", "high visibility offshore", "second intervals full", "than manual segmentation", "environment we", "ldquo rapidbenthos ndash", "altitude", "request", "colony", "gpc incorporate", "diver rig photogrammetry workflow", "photos nadiral", "compared manual", "rapidbenthos ndash automated segmentation", "constituents orthomosaics", "mm nikkor prime lens", "lens shooting", "from high visibility offshore", "rapidbenthos workflow", "also", "coral reefs environment", "reefal environments", "environmental conditions reef", "data can accessed", "diver scuba", "front", "visibility offshore reefs", "automated segmentation", "gpcs were distributed across", "community compostion colony", "any coral reefs environment", "diver rig", "resulted extracting", "gordon et", "photos nadiral oblique", "dome ports", "prime", "planar", "community composition information", "al", "ldquo lawn mowing", "publication titled", "applyed", "were selected", "x y z", "orthomosaics method", "automated segmentation multi", "two nikon", "second intervals full camera", "oblique imagery captured", "composition", "segmentation", "passes swim", "spatially adjacent photos", "compared", "replicate", "between adjacent images", "workflow described", "inch dome ports", "cm", "mounted", "were distributed across", "inch", "standardized diver", "reefs environment we eveluated", "nadiral oblique", "sites", "photogrammetry mointoring", "cameras were housed", "prior", "we eveluated", "publication titled ldquo", "rdquo swim", "segmentation classification", "photogrammetric models underlying", "workflow automatically extract", "segmentation multi view", "scuba over", "et", "extraction due", "planar area", "building rapidbenthos workflow", "sustainably scale", "perpendicular", "additional", "back", "titled ldquo", "images replicate", "research lies devlopping", "data can", "passes", "passes additional", "captured", "over", "across depth", "six", "captured altitude", "colony frequency", "approximately", "using two nikon", "mowing", "housed nauticam underwater", "replication size", "all sites were", "imagery", "model", "large", "all sites were imaged", "titled ldquo rapidbenthos", "classification", "rdquo", "depth also recorded", "models underlying", "any coral", "adjacent images", "titled ldquo rapidbenthos ndash", "compostion colony", "flank back", "distributed across", "diver", "included range", "were housed nauticam", "time faster than manual", "reef communities from photogrammetric", "dslr cameras", "intervals full camera settings", "camera", "surveyed compared manual", "imagery captured", "flank back lagoon", "were mounted", "speed used ensured", "swim pattern consisting", "eveluated", "workflow applyed", "method resulted extracting", "were selected assess", "aluminium", "each site imaged", "coral reef", "nadiral", "publication titled ldquo rapidbenthos", "applyed each", "performance rapidbenthos", "photos", "second", "from close range photogrammetry", "high", "turbid inshore reefs spanned", "extract community composition", "faster", "using standardized diver", "publication", "than", "different", "selected", "research lies", "mm", "time faster", "workflow", "ranged from high visibility", "level", "scuba", "prior imaging scale", "each plots", "details provided", "depths from", "camera settings", "rapidbenthos ndash", "inshore reefs spanned depths", "each site", "speed used", "nikon", "sites were selected", "environmental conditions", "conditions reef", "included", "visibility offshore", "all sites", "range photogrammetry", "replicate method", "view classification coral", "model building", "imaging scale resultant", "surveyed", "mounted aluminium", "spatially", "due", "were imaged using", "reconstruction", "were captured", "photogrammetry any", "intra reefal", "lagoon all sites", "photogrammetric reconstruction rdquo", "sites were imaged", "mm nikkor prime", "spanned", "reefs spanned depths from", "using standardized", "multi view", "photogrammetry workflow described", "cameras were", "manual segmentation", "ranged from high", "underwater", "due large", "conditions", "inch dome", "oblique imagery", "faster than", "minutes capture", "ports", "ports were", "reef habitats", "pattern speed", "site prior", "photos respectively", "photogrammetric models", "pixels were", "classification allowing", "gordon", "lagoon", "single", "models x y", "depths", "faster than manual", "bathymetric information", "provided gordon et", "extract community", "nadiral oblique imagery", "ldquo rapidbenthos ndash automated", "pixels were captured using", "frequency", "reef communities", "back lagoon", "details", "extract community composition information", "view classification", "reef front", "reefs", "offshore", "resolution benthic", "applyed each plots", "extract", "scale resultant", "single diver scuba", "composition information from", "files data can", "lagoon all sites were", "photos respectively figure", "used ensured minimum", "speed", "we", "x y", "nikkor prime lens shooting", "ranged", "high visibility offshore reefs", "lies", "ensured minimum", "presented publication", "different environmental", "figure", "nauticam underwater", "were housed", "inshore reefs spanned", "oblique", "method resulted", "offshore reefs turbid", "rapidbenthos ndash automated", "axes", "mowing rdquo swim", "captured using", "settings described", "between", "overlap between adjacent", "method", "any coral reefs", "turbid inshore", "reef communities from", "described gordon", "workflow described gordon", "rig distance", "pattern speed used", "accessed request", "ndash", "mointoring both replication", "habitats", "classification coral", "reef front flank back", "range", "rapidbenthos", "lawn mowing rdquo swim", "our", "across", "resultant models", "reefs surveyed", "models x", "benthic", "inshore reefs", "respectively figure", "replication", "compostion colony level", "segmenting calssifying", "segmenting calssifying benthic", "we eveluated our", "site", "significance research", "depth", "large data", "assess", "multi view classification", "selected assess", "ranged from", "intervals full camera", "minutes capture approximately", "from photogrammetric reconstruction rdquo", "rapidbenthos different environmental", "minimum", "back lagoon all", "resulted extracting community", "data", "manual data extraction", "photogrammetry workflow", "ensured minimum overlap", "temporally", "adjacent photos respectively figure", "from", "diver rig photogrammetry", "rdquo swim pattern consisting", "plots segmenting calssifying", "each plots segmenting", "described gordon et", "capture", "sustainably", "imaged using", "underlying images", "data files data", "files", "lawn mowing", "rdquo swim pattern", "from close range", "photogrammetry", "link", "scale", "can", "data extraction", "can accessed", "gordon et al", "nauticam underwater housings", "adjacent photos respectively", "gradient site", "method presented", "allowing", "classification coral reef", "sites were imaged using", "performance", "segmenting", "orthomosaics", "time faster than", "constituents");

            insertMetadataRecords(uuid, "classpath:canned/sample7.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true, null);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = Objects.requireNonNull(objectNodeHit.source()).toPrettyString();

            // Parse the JSON string into a JsonNode
            JsonNode rootNode = indexerObjectMapper.readTree(test);
            JsonNode abstractPhrasesNode = rootNode.path("record_suggest").path("abstract_phrases");
            List<String> actual = indexerObjectMapper.convertValue(abstractPhrasesNode, indexerObjectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            logger.info(test);
            Assertions.assertEquals(expected.size(), actual.size(), "abstractPhrases not equals for sample 7. Uuid: " + uuid);
        }
        finally {
            deleteRecord(uuid);
        }
    }
}
