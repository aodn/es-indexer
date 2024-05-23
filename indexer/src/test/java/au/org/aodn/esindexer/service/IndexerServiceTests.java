package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.A;

import java.io.IOException;

import static org.junit.Assert.*;

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

            assertTrue(uuid1 + " published", indexerService.isMetadataPublished(uuid1));
            assertTrue(uuid2 + " published", indexerService.isMetadataPublished(uuid2));
            assertFalse("Not exist and not published", indexerService.isMetadataPublished("not-exist"));
        }
        finally {
            deleteRecord(uuid1);
            deleteRecord(uuid2);
        }
    }
    /**
     * Read the function implementation on why need to insert 1 docs
     * @throws IOException
     */
    @Test
    public void verifyGeoNetworkInstanceReinstalled() throws IOException {
        String uuid = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        try {
            insertMetadataRecords(uuid, "classpath:canned/sample1.xml");
            assertTrue("New installed", indexerService.isGeoNetworkInstanceReinstalled(1));
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

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true);

            // The sample1 geometry have error [1:9695] failed to parse field [summaries.proj:geometry] of type [geo_shape]
            // ErrorCause: {"type":"illegal_argument_exception","reason":"Polygon self-intersection at lat=57.0 lon=-66.0"}
            //
            // So it will not insert correctly and result in 1 doc only
            assertEquals("Doc count correct", 1L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME));
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

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
            assertEquals("Doc count correct", 2L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME));

            // Only 2 doc in elastic, if we delete it then should be zero
            indexerService.deleteDocumentByUUID(uuid1);
            assertEquals("Doc count correct", 1L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME));

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

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = objectNodeHit.source().toPrettyString();

            assertEquals("Stac equals " + uuid, indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test));
        }
        finally {
            deleteRecord(uuid);
        }
    }
    /**
     * Some dataset can provide links to logos, this test is use to verify the logo links added correctly to the
     * @throws IOException - If file not found
     */
    @Test
    public void verifyLogoLinkAddedOnIndex() throws IOException {
        String uuid = "2852a776-cbfc-4bc8-a126-f3c036814892";
        try {
            String expected = readResourceFile("classpath:canned/sample5_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample5.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = objectNodeHit.source().toPrettyString();
            assertEquals("Stac equals " + uuid, indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test));
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

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = objectNodeHit.source().toPrettyString();
            assertEquals("Stac equals " + uuid, indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test));
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
    @Disabled("Bug in code where bounding box not right")
    @Test
    public void verifyThumbnailLinkNullAddedOnIndex() throws IOException {
        String uuid = "5905b3eb-aad0-4f9c-a03e-a02fb3488082";
        try {
            String expected = readResourceFile("classpath:canned/sample7_stac.json");

            insertMetadataRecords(uuid, "classpath:canned/sample7.xml");

            indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
            Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID(uuid);

            String test = objectNodeHit.source().toPrettyString();
            logger.info(test);
            assertEquals("Stac equals " + uuid, indexerObjectMapper.readTree(expected), indexerObjectMapper.readTree(test));
        }
        finally {
            deleteRecord(uuid);
        }
    }
}
