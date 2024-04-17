package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
    protected ObjectMapper objectMapper;

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
        clearElasticIndex();
    }

    @Test
    public void verifyIsMetadataPublished() throws IOException {
        insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
        insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");

        assertTrue("9e5c3031-a026-48b3-a153-a70c2e2b78b9 published", indexerService.isMetadataPublished("9e5c3031-a026-48b3-a153-a70c2e2b78b9"));
        assertTrue("830f9a83-ae6b-4260-a82a-24c4851f7119 published", indexerService.isMetadataPublished("830f9a83-ae6b-4260-a82a-24c4851f7119"));
        assertFalse("Not exist and not published", indexerService.isMetadataPublished("not-exist"));

        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119");
    }
    /**
     * Read the function implementation on why need to insert 1 docs
     * @throws IOException
     */
    @Test
    public void verifyGeoNetworkInstanceReinstalled() throws IOException {
        insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
        assertTrue("New installed", indexerService.isGeoNetworkInstanceReinstalled(1));

        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
    }

    @Test
    public void verifyGetDocumentCount() throws IOException {
        insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");
        insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");

        indexerService.indexAllMetadataRecordsFromGeoNetwork(true);

        // The sample1 geometry have error [1:9695] failed to parse field [summaries.proj:geometry] of type [geo_shape]
        // ErrorCause: {"type":"illegal_argument_exception","reason":"Polygon self-intersection at lat=57.0 lon=-66.0"}
        //
        // So it will not insert correctly and result in 1 doc only
        assertEquals("Doc count correct", 1L, indexerService.getDocumentsCount());

        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119");
    }

    @Test
    public void verifyDeleteDocumentByUUID() throws IOException {
        insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");
        insertMetadataRecords("06b09398-d3d0-47dc-a54a-a745319fbece", "classpath:canned/sample3.xml");

        indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
        assertEquals("Doc count correct", 2L, indexerService.getDocumentsCount());

        // Only 2 doc in elastic, if we delete it then should be zero
        indexerService.deleteDocumentByUUID("830f9a83-ae6b-4260-a82a-24c4851f7119");
        assertEquals("Doc count correct", 1L, indexerService.getDocumentsCount());

        deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119");
        deleteRecord("06b09398-d3d0-47dc-a54a-a745319fbece");
    }

    @Test
    public void verifyGetDocumentByUUID() throws IOException {
        String expected = readResourceFile("classpath:canned/sample4_stac.json");

        insertMetadataRecords("7709f541-fc0c-4318-b5b9-9053aa474e0e", "classpath:canned/sample4.xml");

        indexerService.indexAllMetadataRecordsFromGeoNetwork(true);
        Hit<ObjectNode> objectNodeHit = indexerService.getDocumentByUUID("7709f541-fc0c-4318-b5b9-9053aa474e0e");

        String test = objectNodeHit.source().toPrettyString();

        assertEquals("Stac equals", objectMapper.readTree(expected), objectMapper.readTree(test));

        deleteRecord("7709f541-fc0c-4318-b5b9-9053aa474e0e");
    }
}
