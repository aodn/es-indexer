package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.DockerComposeContainer;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GeoNetworkServiceTests extends BaseTestClass {
    // Must use the impl to access protected method for testing
    @Autowired
    protected GeoNetworkServiceImpl geoNetworkService;

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
    /**
     * We need to make sure this works before you can do any meaningful transformation
     *
     * @throws IOException
     */
    @Test
    @Order(1)
    public void verifyInsertMetadataWorks() throws IOException, InterruptedException {

        String content = insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");

        logger.debug("Get count in verifyInsertMetadataWorks");
        assertEquals("Count is 1", 1, geoNetworkService.getMetadataRecordsCount());

        Iterable<String> i = geoNetworkService.getAllMetadataRecords();

        for(String x : i) {
            Diff d  = DiffBuilder
                        .compare(content)
                        .withTest(x)
                        .ignoreWhitespace()
                        .ignoreComments()
                        .build();

            assertFalse("XML equals", d.hasDifferences());
        }

        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
    }

    @Test
    public void verifyFindFormatterId() throws IOException, InterruptedException {

        insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
        assertEquals("Format is correct",
                AppConstants.FORMAT_XML,
                geoNetworkService.findFormatterId("9e5c3031-a026-48b3-a153-a70c2e2b78b9"));

        insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");
        assertEquals("Format is correct",
                AppConstants.FORMAT_ISO19115_3_2018,
                geoNetworkService.findFormatterId("830f9a83-ae6b-4260-a82a-24c4851f7119"));

        Exception exception = assertThrows(MetadataNotFoundException.class, () -> {
            geoNetworkService.findFormatterId("NOT_FOUND");
        });

        assertTrue("Unable to find metadata record with UUID: NOT_FOUND in GeoNetwork".contains(exception.getMessage()));

        deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119");
        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
    }

    @Test
    public void verifySearchRecordBy() throws IOException, InterruptedException {

        String content = insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
        String xml = geoNetworkService.searchRecordBy("9e5c3031-a026-48b3-a153-a70c2e2b78b9");

        Diff d  = DiffBuilder
                .compare(content)
                .withTest(xml)
                .ignoreWhitespace()
                .ignoreComments()
                .ignoreElementContentWhitespace()
                .normalizeWhitespace()
                .build();

        assertFalse("XML equals for 9e5c3031-a026-48b3-a153-a70c2e2b78b9", d.hasDifferences());

        insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");
        xml = geoNetworkService.searchRecordBy("830f9a83-ae6b-4260-a82a-24c4851f7119");

        // The sample2 is of old format, the indexer only works for iso19115, hence the search will convert it
        // so the return result will not be the same as sample2 input.
        File f = ResourceUtils.getFile("classpath:canned/transformed_sample2.xml");
        String transformed = new String(Files.readAllBytes(f.toPath()));

        d  = DiffBuilder
                .compare(transformed)
                .withTest(xml)
                .ignoreWhitespace()
                .ignoreComments()
                .ignoreElementContentWhitespace()
                .normalizeWhitespace()
                .build();

        assertFalse("XML transformed for 830f9a83-ae6b-4260-a82a-24c4851f7119", d.hasDifferences());

        deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119");
        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
    }

    @Test
    public void verifyAllMetadataRecords() throws IOException, InterruptedException  {

        insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
        insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");

        Iterable<String> i = geoNetworkService.getAllMetadataRecords();

        // The content verified above, just make sure it returned the correct number
        int count = 0;
        for(String x : i) {
            if(x != null) {
                count++;
            }
        }

        assertEquals("Count matches", 2, count);

        deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119");
        deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
    }
}
