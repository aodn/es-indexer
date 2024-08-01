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
            Assertions.assertEquals(indexerObjectMapper.readTree(expectedJson), indexerObjectMapper.readTree(resultJson));
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
            List<String> expected = Arrays.asList("community compostion", "shooting", "resolution", "ldquo lawn", "building", "were distributed", "community composition", "rapidbenthos different", "high visibility", "level metrics i.e", "i.e", "information from close range", "lenses", "diver scuba over", "community", "photogrammetry any coral", "calssifying", "information from close", "4", "size", "5", "each gpc", "8", "metrics", "also recorded each", "manual", "assess performance", "m using", "were captured using two", "reefs surveyed compared", "using two nikon d850", "devlopping workflow", "close", "z axes details", "spanned depths from 5", "workflow automatically", "m", "classification allowing sustainably", "spatially adjacent", "consisting", "ldquo", "benthic images", "plots segmenting", "x", "y", "z", "information", "data extraction due", "communities from photogrammetric reconstruction", "captured using two nikon", "automatically extract community", "housings 8", "approximately 3,000", "level metrics", "gpc", "rig photogrammetry workflow", "mowing rdquo swim pattern", "scale resultant models", "area", "visibility", "underwater housings", "0.5 second intervals full", "extracting community compostion", "prior imaging", "information 3d model", "segmentation classification allowing", "gpc incorporate bathymetric", "reconstruction rdquo", "site imaged", "cm between lenses 60", "reefs turbid", "metrics i.e colony planar", "imaged using standardized", "longitudinal", "15 meters", "y z axes", "provided", "4 8 perpendicular passes", "photogrammetric", "lens", "3217", "60 between temporally", "reefs turbid inshore", "six gpcs", "standardized", "high resolution", "colony planar", "lagoon all", "reconstruction rdquo sites", "8 inch dome ports", "nikkor prime lens", "recorded", "manual segmentation classification", "significance", "environment", "lenses 60 overlap", "single diver", "allowing sustainably scale", "rig photogrammetry", "et al", "reef front flank", "images figure", "details provided gordon", "from high", "colony level", "from photogrammetric reconstruction", "composition information from close", "accessed", "habitats ranged", "rig", "axes details provided", "rdquo sites", "second intervals", "period", "x 3217 pixels", "2023 high resolution benthic", "distributed", "underlying", "z axes", "coral reefs environment we", "reefal environments i.e reef", "colony planar area", "m using ldquo", "both replication", "pixels were captured", "incorporate bathymetric information", "swim pattern speed", "5686", "allowing sustainably", "respectively", "captured using two", "housings", "standardized diver rig photogrammetry", "front flank back", "cameras", "spatially adjacent photos respectively", "coral reef communities", "recorded each gpc", "imaged single", "range photogrammetry any", "area colony", "3d model building", "than manual", "approximately 1.5", "used ensured", "nauticam", "building rapidbenthos", "pattern consisting", "15 meters included", "temporally spatially adjacent", "coral reefs", "reefal", "over period", "communities", "compared manual data", "environments", "automatically extract", "resolution benthic images", "replicate method presented", "standardized diver rig", "spanned depths from", "images 5686", "rapidbenthos workflow applyed", "depths from 5", "photogrammetric reconstruction", "coral", "between temporally", "20 mm nikkor prime", "benthic constituents", "from 5 15", "1d six gpcs were", "models underlying images", "all", "settings", "20 mm nikkor", "lawn mowing rdquo", "imaging scale", "57 cm", "from high visibility", "intervals", "manual data extraction due", "between lenses", "planar area colony", "were", "capture approximately 3,000 photos", "overlap between adjacent images", "intervals full", "our method", "intra reefal environments i.e", "195 time", "figure 1c each site", "meters", "turbid", "calssifying benthic", "colony level metrics i.e", "adjacent images figure", "adjacent", "any", "plots", "0.5", "across depth gradient", "195 time faster", "ldquo lawn mowing rdquo", "using two", "i.e reef front", "using ldquo", "colony level metrics", "underwater housings 8", "rdquo sites were", "different environmental conditions", "additional 4 8", "offshore reefs", "six gpcs were", "3217 pixels were", "full camera", "housed nauticam", "models", "reef", "using", "environmental", "reefs spanned", "12 3d photogrammetric models", "scuba over period", "metrics i.e", "information 3d", "range intra reefal", "distance 57 cm", "each", "3217 pixels", "close range", "meters included", "2023 depth also", "12 3d", "manual data", "1.5 m using", "temporally spatially", "gpcs were distributed", "reefs environment", "close range photogrammetry", "size reefs", "automatically", "0.5 second intervals", "two", "distributed across depth", "were imaged", "mowing rdquo", "extracting community", "colony frequency significance", "ldquo rapidbenthos", "front flank", "altitude approximately 1.5", "altitude approximately", "d850 dslr", "gradient", "between temporally spatially", "underlying images replicate", "information from", "faster than manual segmentation", "automated", "were captured using", "ndash automated segmentation", "calssifying benthic constituents", "orthomosaics method resulted", "dslr", "shooting 0.5 second", "axes details", "consisting 5", "resulted", "size reefs surveyed", "depth gradient", "cm between lenses", "incorporate", "model building rapidbenthos", "adjacent images figure 1c", "research", "housed", "view", "y z", "aluminium rig", "cm between", "provided gordon", "using ldquo lawn", "1d six gpcs", "flank", "z axes details provided", "images 5686 x", "shooting 0.5", "swim pattern", "from close", "sustainably scale 3d photogrammetry", "high resolution benthic images", "imaging", "surveyed compared", "overlap", "3d photogrammetric", "incorporate bathymetric", "ports were mounted", "between adjacent", "swim", "full", "scale 3d photogrammetry", "inshore", "distance", "resultant models x", "data files", "nikkor prime", "community composition information from", "nikkor", "5 longitudinal passes", "lies devlopping", "reefs environment we", "from photogrammetric", "recorded each", "8 perpendicular", "overlap 80", "depth also", "files data", "195 time faster than", "devlopping", "communities from photogrammetric", "composition information", "spanned depths", "ensured", "area colony frequency", "time", "titled", "also recorded", "automatically extract community composition", "4 8 perpendicular", "longitudinal passes", "extracting", "six gpcs were distributed", "multi", "capture approximately", "conditions reef habitats", "camera settings described", "full camera settings described", "resolution benthic images 5686", "adjacent photos", "resultant", "images", "al 2023 cameras", "gpcs", "our method 195", "extraction", "sites were", "imaged", "2023 depth", "mointoring both", "eveluated our", "lawn", "between adjacent images figure", "rig photogrammetry workflow described", "high resolution benthic", "prime lens shooting", "pattern", "dome", "mointoring", "dome ports were", "coral reef communities from", "photos respectively figure 1d", "communities from", "perpendicular passes swim", "bathymetric", "segmentation multi", "reefal environments i.e", "gpcs were", "compostion", "full camera settings", "perpendicular passes", "passes swim pattern", "environments i.e reef", "photogrammetry mointoring both", "settings described gordon", "environment we eveluated", "et al 2023 cameras", "eveluated our method", "mm nikkor", "overlap between", "used", "60 overlap", "minimum overlap", "frequency significance", "presented", "each gpc incorporate", "reefs spanned depths", "level metrics i.e colony", "2023 high resolution", "described", "turbid inshore reefs", "range intra", "intra reefal environments", "intra", "ndash automated", "minutes", "large data files", "site prior imaging", "capture approximately 3,000", "60 overlap between adjacent", "both", "pixels", "prime lens", "high visibility offshore", "second intervals full", "than manual segmentation", "environment we", "2023 cameras", "ldquo rapidbenthos ndash", "altitude", "request", "12 3d photogrammetric", "colony", "gpc incorporate", "two nikon d850 dslr", "diver rig photogrammetry workflow", "photos nadiral", "environments i.e reef front", "et al 2023 high", "compared manual", "rapidbenthos ndash automated segmentation", "consisting 5 longitudinal", "1c each site", "3,000", "constituents orthomosaics", "mm nikkor prime lens", "lens shooting", "from high visibility offshore", "rapidbenthos workflow", "also", "coral reefs environment", "reefal environments", "environmental conditions reef", "scale 3d", "data can accessed", "diver scuba", "front", "visibility offshore reefs", "automated segmentation", "gpcs were distributed across", "community compostion colony", "scale 3d photogrammetry mointoring", "any coral reefs environment", "diver rig", "resulted extracting", "gordon et", "nikon d850 dslr cameras", "20 mm", "photos nadiral oblique", "dome ports", "prime", "planar", "community composition information", "al", "ldquo lawn mowing", "publication titled", "applyed", "were selected", "images figure 1c", "figure 1c each", "x y z", "3d model", "3,000 photos nadiral", "orthomosaics method", "automated segmentation multi", "two nikon", "second intervals full camera", "figure 1d six gpcs", "60 overlap between", "oblique imagery captured", "figure 1d", "figure 1c", "composition", "segmentation", "passes swim", "spatially adjacent photos", "compared", "3d photogrammetric models", "replicate", "between adjacent images", "5686 x 3217 pixels", "workflow described", "inch dome ports", "cm", "approximately 3,000 photos", "mounted", "benthic images 5686 x", "were distributed across", "5686 x", "inch", "standardized diver", "reefs environment we eveluated", "al 2023 cameras were", "nadiral oblique", "sites", "lenses 60", "photogrammetry mointoring", "cameras were housed", "d850 dslr cameras", "prior", "we eveluated", "publication titled ldquo", "rdquo swim", "segmentation classification", "photogrammetric models underlying", "workflow automatically extract", "segmentation multi view", "scuba over", "et", "extraction due", "i.e colony planar area", "1d six", "planar area", "building rapidbenthos workflow", "sustainably scale", "perpendicular", "additional", "back", "titled ldquo", "images replicate", "0.5 second", "research lies devlopping", "data can", "pattern consisting 5", "passes", "passes additional", "captured", "80 60 between", "3d photogrammetry mointoring", "et al 2023", "over", "across depth", "six", "2023", "captured altitude", "colony frequency", "approximately", "using two nikon", "mowing", "method 195 time", "housed nauticam underwater", "replication size", "lenses 60 overlap between", "all sites were", "al 2023 depth", "10 15", "5 longitudinal", "imagery", "period 10", "model", "approximately 1.5 m using", "i.e reef", "large", "all sites were imaged", "x 3217 pixels were", "titled ldquo rapidbenthos", "classification", "rdquo", "depth also recorded", "dslr cameras 20", "models underlying", "15 minutes", "lens shooting 0.5", "any coral", "adjacent images", "titled ldquo rapidbenthos ndash", "compostion colony", "flank back", "distributed across", "diver", "included range", "were housed nauticam", "time faster than manual", "reef communities from photogrammetric", "dslr cameras", "intervals full camera settings", "camera", "surveyed compared manual", "benthic images 5686", "imagery captured", "flank back lagoon", "were mounted", "speed used ensured", "swim pattern consisting", "eveluated", "workflow applyed", "method resulted extracting", "were selected assess", "aluminium", "each site imaged", "coral reef", "nadiral", "approximately 1.5 m", "publication titled ldquo rapidbenthos", "applyed each", "performance rapidbenthos", "photos", "second", "from close range photogrammetry", "high", "turbid inshore reefs spanned", "extract community composition", "faster", "using standardized diver", "publication", "than", "different", "3d photogrammetry mointoring both", "selected", "research lies", "mm", "time faster", "i.e reef front flank", "workflow", "ranged from high visibility", "level", "scuba", "2023 high", "cameras 20", "prior imaging scale", "each plots", "sustainably scale 3d", "from 5", "1.5", "details provided", "depths from", "d850", "camera settings", "rapidbenthos ndash", "inshore reefs spanned depths", "x 3217", "each site", "speed used", "nikon", "3,000 photos", "sites were selected", "environmental conditions", "conditions reef", "included", "visibility offshore", "all sites", "range photogrammetry", "replicate method", "view classification coral", "8 inch dome", "model building", "imaging scale resultant", "surveyed", "mounted aluminium", "spatially", "due", "were imaged using", "et al 2023 depth", "5 15", "reconstruction", "were captured", "1c each", "photogrammetry any", "intra reefal", "approximately 3,000 photos nadiral", "lagoon all sites", "period 10 15", "photogrammetric reconstruction rdquo", "i.e colony planar", "sites were imaged", "housings 8 inch", "mm nikkor prime", "spanned", "environments i.e", "10", "12", "reefs spanned depths from", "using standardized", "link 12 3d", "15", "multi view", "photogrammetry workflow described", "cameras were", "bathymetric information 3d", "57 cm between lenses", "manual segmentation", "ranged from high", "al 2023 high resolution", "underwater", "due large", "conditions", "inch dome", "20", "oblique imagery", "faster than", "minutes capture", "ports", "ports were", "reef habitats", "1c", "1d", "pattern speed", "site prior", "photos respectively", "images 5686 x 3217", "photogrammetric models", "distance 57", "pixels were", "classification allowing", "gordon", "lagoon", "3217 pixels were captured", "single", "models x y", "depths", "faster than manual", "bathymetric information", "provided gordon et", "link 12", "extract community", "nadiral oblique imagery", "ldquo rapidbenthos ndash automated", "pixels were captured using", "frequency", "5 15 meters", "3d", "reef communities", "back lagoon", "details", "8 perpendicular passes", "57", "extract community composition information", "view classification", "reef front", "reefs", "offshore", "resolution benthic", "applyed each plots", "8 inch", "i.e colony", "extract", "scale resultant", "al 2023", "single diver scuba", "composition information from", "60", "files data can", "lagoon all sites were", "2023 cameras were", "additional 4 8 perpendicular", "photos respectively figure", "used ensured minimum", "1.5 m", "speed", "we", "x y", "nikkor prime lens shooting", "respectively figure 1d six", "ranged", "high visibility offshore reefs", "lies", "ensured minimum", "presented publication", "different environmental", "figure", "nauticam underwater", "were housed", "inshore reefs spanned", "cameras 20 mm", "additional 4", "80", "oblique", "method resulted", "offshore reefs turbid", "rapidbenthos ndash automated", "axes", "nikon d850 dslr", "195", "mowing rdquo swim", "captured using", "settings described", "between", "overlap between adjacent", "method", "any coral reefs", "turbid inshore", "reef communities from", "60 between", "described gordon", "workflow described gordon", "rig distance", "pattern speed used", "57 cm between", "accessed request", "ndash", "mointoring both replication", "between lenses 60 overlap", "habitats", "classification coral", "gordon et al 2023", "reef front flank back", "range", "metrics i.e colony", "rapidbenthos", "lawn mowing rdquo swim", "4 8", "our", "across", "resultant models", "10 15 minutes", "minimum overlap 80", "reefs surveyed", "models x", "benthic", "inshore reefs", "respectively figure", "replication", "compostion colony level", "segmenting calssifying", "segmenting calssifying benthic", "we eveluated our", "site", "respectively figure 1d", "significance research", "depth", "large data", "assess", "multi view classification", "selected assess", "ranged from", "intervals full camera", "minutes capture approximately", "from photogrammetric reconstruction rdquo", "rapidbenthos different environmental", "minimum", "back lagoon all", "method 195", "2023 cameras were housed", "resulted extracting community", "data", "manual data extraction", "photogrammetry workflow", "ensured minimum overlap", "temporally", "3d photogrammetry", "adjacent photos respectively figure", "from", "diver rig photogrammetry", "rdquo swim pattern consisting", "plots segmenting calssifying", "each plots segmenting", "described gordon et", "capture", "sustainably", "15 minutes capture", "imaged using", "underlying images", "data files data", "nikon d850", "files", "lawn mowing", "rdquo swim pattern", "images figure 1c each", "figure 1d six", "from close range", "photogrammetry", "link", "80 60", "scale", "can", "data extraction", "can accessed", "gordon et al", "nauticam underwater housings", "adjacent photos respectively", "gradient site", "between lenses 60", "method presented", "two nikon d850", "allowing", "classification coral reef", "al 2023 high", "sites were imaged using", "performance", "segmenting", "5686 x 3217", "orthomosaics", "time faster than", "constituents");

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
