package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.model.AssociateRecordFactory;
import au.org.aodn.esindexer.model.RelationType;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GeoNetworkServiceTests extends BaseTestClass {
    // Must use the impl to access protected method for testing
    @Autowired
    protected GeoNetworkServiceImpl geoNetworkService;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @Value("${elasticsearch.query.pageSize}")
    protected int pageSize;

    @Autowired
    JaxbUtils<MDMetadataType> jaxbUtils;

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
    /**
     * We need to make sure this works before you can do any meaningful transformation
     *
     * @throws IOException
     */
    @Test
    @Order(1)
    public void verifyInsertMetadataWorks() throws IOException {
        try {
            String content = insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");

            logger.debug("Get count in verifyInsertMetadataWorks");

            Assertions.assertFalse(geoNetworkService.isMetadataRecordsCountLessThan(1), "Compare false");
            Assertions.assertTrue(geoNetworkService.isMetadataRecordsCountLessThan(2), "Compare true");

            Iterable<String> i = geoNetworkService.getAllMetadataRecords();

            for (String x : i) {
                if (x != null) {
                    Diff d = DiffBuilder
                            .compare(content)
                            .withTest(x)
                            .ignoreWhitespace()
                            .ignoreComments()
                            .build();

                    Assertions.assertFalse(d.hasDifferences(), "XML equals");
                }
            }
        }
        finally {
            deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        }
    }

    @Test
    public void verifyFindFormatterId() throws IOException {

        try {
            insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
            Assertions.assertEquals(AppConstants.FORMAT_XML,
                    geoNetworkService.findFormatterId("9e5c3031-a026-48b3-a153-a70c2e2b78b9"),
                    "Format is correct");

            insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");
            Assertions.assertEquals(AppConstants.FORMAT_ISO19115_3_2018,
                    geoNetworkService.findFormatterId("830f9a83-ae6b-4260-a82a-24c4851f7119"),
                    "Format is correct");

            Exception exception = assertThrows(MetadataNotFoundException.class, () -> {
                geoNetworkService.findFormatterId("NOT_FOUND");
            });

            Assertions.assertTrue("Unable to find metadata record with UUID: NOT_FOUND in GeoNetwork".contains(exception.getMessage()));

        }
        finally {
            deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119", "9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        }
    }

    @Test
    public void verifyFindGroupById() throws IOException {
        try {
            // By default, record will assign to group with group id = 2
            insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
            String group = geoNetworkService.findGroupById("9e5c3031-a026-48b3-a153-a70c2e2b78b9");

            Assertions.assertEquals("sample", group, "Default group equals");

        }
        finally {
            deleteRecord("9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        }
    }

    @Test
    public void verifySearchRecordBy() throws IOException {
        try {
            String content = insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
            String xml = geoNetworkService.searchRecordBy("9e5c3031-a026-48b3-a153-a70c2e2b78b9");

            Diff d = DiffBuilder
                    .compare(content)
                    .withTest(xml)
                    .ignoreWhitespace()
                    .ignoreComments()
                    .ignoreElementContentWhitespace()
                    .normalizeWhitespace()
                    .build();

            Assertions.assertFalse(d.hasDifferences(), "XML equals for 9e5c3031-a026-48b3-a153-a70c2e2b78b9");

            insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");
            xml = geoNetworkService.searchRecordBy("830f9a83-ae6b-4260-a82a-24c4851f7119");

            // The sample2 is of old format, the indexer only works for iso19115, hence the search will convert it
            // so the return result will not be the same as sample2 input.
            File f = ResourceUtils.getFile("classpath:canned/transformed_sample2.xml");
            String transformed = new String(Files.readAllBytes(f.toPath()));

            d = DiffBuilder
                    .compare(transformed)
                    .withTest(xml)
                    .ignoreWhitespace()
                    .ignoreComments()
                    .ignoreElementContentWhitespace()
                    .normalizeWhitespace()
                    .build();

            Assertions.assertFalse(d.hasDifferences(), "XML transformed for 830f9a83-ae6b-4260-a82a-24c4851f7119");

        }
        finally {
            deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119", "9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        }
    }

    @Test
    public void verifyAllMetadataRecords() throws IOException  {
        try {
            insertMetadataRecords("9e5c3031-a026-48b3-a153-a70c2e2b78b9", "classpath:canned/sample1.xml");
            insertMetadataRecords("830f9a83-ae6b-4260-a82a-24c4851f7119", "classpath:canned/sample2.xml");

            Iterable<String> i = geoNetworkService.getAllMetadataRecords();

            // The content verified above, just make sure it returned the correct number
            int count = 0;
            for (String x : i) {
                if (x != null) {
                    count++;
                }
            }

            Assertions.assertEquals(2, count, "Count matches");
        }
        finally {
            deleteRecord("830f9a83-ae6b-4260-a82a-24c4851f7119", "9e5c3031-a026-48b3-a153-a70c2e2b78b9");
        }
    }

    /**
     * We set a very small page size in test, please refer to
     * @throws IOException
     */
    @Test
    public void verifyAllMetadataRecordWithPage() throws IOException, JAXBException {
        final String UUID1 = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        final String UUID2 = "830f9a83-ae6b-4260-a82a-24c4851f7119";
        final String UUID3 = "06b09398-d3d0-47dc-a54a-a745319fbece";
        final String UUID4 = "7709f541-fc0c-4318-b5b9-9053aa474e0e";
        final String UUID5 = "2852a776-cbfc-4bc8-a126-f3c036814892";
        final String UUID6 = "e18eee85-c6c4-4be2-ac8c-930991cf2534";
        final String UUID7 = "5905b3eb-aad0-4f9c-a03e-a02fb3488082";

        try {

            Assertions.assertEquals(4, pageSize, "Page size need to be small to work for this test");

            insertMetadataRecords(UUID1, "classpath:canned/sample1.xml");
            insertMetadataRecords(UUID2, "classpath:canned/sample2.xml");
            insertMetadataRecords(UUID3, "classpath:canned/sample3.xml");
            insertMetadataRecords(UUID4, "classpath:canned/sample4.xml");
            insertMetadataRecords(UUID5, "classpath:canned/sample5.xml");
            insertMetadataRecords(UUID6, "classpath:canned/sample6.xml");
            insertMetadataRecords(UUID7, "classpath:canned/sample7.xml");

            Iterable<String> i = geoNetworkService.getAllMetadataRecords();

            final List<MDMetadataType> xml = new ArrayList<>();
            for(String x : i) {

                // because of the concurrency issue, sometimes it might return null. just ignore it
                if (x == null) {
                    continue;
                }
                xml.add(jaxbUtils.unmarshal(x));
            }

            // A list of ordered UUID
            Assertions.assertEquals(UUID3, xml.get(0).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID3);
            Assertions.assertEquals(UUID5, xml.get(1).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID5);
            Assertions.assertEquals(UUID7, xml.get(2).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID7);
            Assertions.assertEquals(UUID4, xml.get(3).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID4);
            Assertions.assertEquals(UUID2, xml.get(4).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID2);
            Assertions.assertEquals(UUID1, xml.get(5).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID1);
            Assertions.assertEquals(UUID6, xml.get(6).getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue(), UUID6);
        }
        finally {
            deleteRecord(UUID1, UUID2, UUID3, UUID4, UUID5, UUID6, UUID7);
        }
    }


    @Test
    public void verifyAssociatedRecords() {

        var targetRecordId = "4637bd9b-8fba-4a10-bf23-26a511e17042";
        var parentId = "a35d02d7-3bd2-40f8-b982-a0e30b64dc40";
        var siblingId = "0ede6b3d-8635-472f-b91c-56a758b4e091";
        var childId = "06b09398-d3d0-47dc-a54a-a745319fbece";

        try {
            insertMetadataRecords(targetRecordId, "classpath:canned/associated/targetRecord.xml");
            insertMetadataRecords(parentId, "classpath:canned/associated/parent.xml");
            insertMetadataRecords(siblingId, "classpath:canned/associated/sibling.xml");
            insertMetadataRecords(childId, "classpath:canned/associated/child.xml");

            var associatedRecords = geoNetworkService.getAssociatedRecords(targetRecordId);
            var records = AssociateRecordFactory.buildAssociatedRecords(associatedRecords);
            var builtParentId = records
                    .stream()
                    .filter(x -> x.getRel().equals(RelationType.PARENT.getValue()))
                    .map(x -> x.getHref().replace("uuid:", ""))
                    .findFirst()
                    .orElse(null);

            var builtSiblingId = records
                    .stream()
                    .filter(x -> x.getRel().equals(RelationType.SIBLING.getValue()))
                    .map(x -> x.getHref().replace("uuid:", ""))
                    .findFirst()
                    .orElse(null);

            var builtChildId = records
                    .stream()
                    .filter(x -> x.getRel().equals(RelationType.CHILD.getValue()))
                    .map(x -> x.getHref().replace("uuid:", ""))
                    .findFirst()
                    .orElse(null);

            Assertions.assertEquals(parentId, builtParentId, "Wrong parent id");
            Assertions.assertEquals(siblingId, builtSiblingId, "Wrong sibling1 id");
            Assertions.assertEquals(childId, builtChildId, "Wrong child1 id");

        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        } finally {
            deleteRecord(targetRecordId, parentId, siblingId, childId);
        }
    }
}
