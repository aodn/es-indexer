package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import ch.qos.logback.core.joran.spi.XMLUtil;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GeoNetworkServiceTests extends BaseTestClass {

    @Autowired
    protected GeoNetworkService geoNetworkService;

    @Autowired
    protected DockerComposeContainer dockerComposeContainer;

    @BeforeAll
    public void setup() {
        // Update the server for geonetwork RESTful URL
        geoNetworkService.setServer(String.format("http://%s:%s",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT))
        );
    }

    /**
     * We need to make sure this works before you can do any meaningful transformation
     *
     * @throws IOException
     */
    @Test
    @Order(1)
    public void verifyInsertMetadataWorks() throws IOException, InterruptedException {
        File f = ResourceUtils.getFile("classpath:canned/sample1.xml");
        String content = new String(Files.readAllBytes(f.toPath()));

        insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", content);

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
    }
}
