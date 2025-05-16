package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.esindexer.controller.IndexerController;
import au.org.aodn.esindexer.model.MockServer;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DataAccessServiceIT {

    @Value("${elasticsearch.cloud_optimized_index.name}")
    protected String INDEX_NAME;

    @Autowired
    protected MockServer mockServer;

    @Autowired
    protected IndexerController controller;

    @Autowired
    protected IndexerMetadataService indexerService;

    @Autowired
    protected ElasticSearchIndexService elasticSearchIndexService;

    protected ObjectMapper objectMapper = new ObjectMapper();

    @AfterAll
    public void resetMock() {
        mockServer.getServer().reset();
    }

    @Disabled("This test is disabled and will fix it later")
    @Test
    public void verifyConversion1() throws IOException, JSONException, InterruptedException {
        try {
            // This set the time range of the mock data range.
            MetadataEntity metadataEntity = new MetadataEntity();
            metadataEntity.setUuid("35234913-aa3c-48ec-b9a4-77f822f66ef8");

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/metadata/35234913-aa3c-48ec-b9a4-77f822f66ef8"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(objectMapper.writeValueAsString(metadataEntity), MediaType.APPLICATION_JSON));

            TemporalExtent temporalExtent = TemporalExtent.builder()
                    .startDate("2024-01-01T00:00:00+0000")
                    .endDate("2024-04-01T00:00:00+0000")
                    .build();

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8/temporal_extent"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(objectMapper.writeValueAsString(List.of(temporalExtent)), MediaType.APPLICATION_JSON));

            // Mimic the api call given the range above
            String canned_2024_01= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/2024-01.json");
            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-01-01&end_date=2024-01-31&columns=TIME&columns=LONGITUDE&columns=LATITUDE"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(canned_2024_01, MediaType.APPLICATION_JSON));

            String canned_2024_02= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/2024-02.json");
            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-02-01&end_date=2024-02-29&columns=TIME&columns=LONGITUDE&columns=LATITUDE"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(canned_2024_02, MediaType.APPLICATION_JSON));

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-03-01&end_date=2024-03-31&columns=TIME&columns=LONGITUDE&columns=LATITUDE"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-04-01&end_date=2024-04-30&columns=TIME&columns=LONGITUDE&columns=LATITUDE"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            controller.indexCODataByUUID("35234913-aa3c-48ec-b9a4-77f822f66ef8", null, null);

            CountDownLatch latch = new CountDownLatch(1);
            latch.await(5, TimeUnit.SECONDS);

            // Insert value correctly
            Assertions.assertEquals(747L, elasticSearchIndexService.getDocumentsCount(INDEX_NAME), "Doc count correct");

            Hit<ObjectNode> hit = indexerService.getDocumentByUUID("35234913-aa3c-48ec-b9a4-77f822f66ef8|2024-02|170.33|-33.87|530.00", INDEX_NAME);

            String stac1= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/StacItem1.json");
            Assertions.assertNotNull(hit.source(), "Source not null");
            JSONAssert.assertEquals(stac1, hit.source().toString(), JSONCompareMode.STRICT);
        }
        finally {
            mockServer.getServer().reset();
        }
    }
}
