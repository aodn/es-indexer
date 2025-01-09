package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.controller.IndexerController;
import au.org.aodn.esindexer.model.MockServer;
import au.org.aodn.esindexer.model.TemporalExtent;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

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

    @Autowired
    protected MockServer mockServer;

    @Autowired
    protected IndexerController controller;

    protected ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void verifyConversion1() throws IOException {
        try {
            // This set the time range of the mock data range.
            TemporalExtent temporalExtent = TemporalExtent.builder()
                    .startDate("2024-01-01T00:00:00+0000")
                    .endDate("2024-04-01T00:00:00+0000")
                    .build();

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8/temporal_extent"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(objectMapper.writeValueAsString(List.of(temporalExtent)), MediaType.APPLICATION_JSON));

            // Mimic the api call given the range above
            String canned_2024_01= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/2024-01.json");
            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-01-01&end_date=2024-01-31"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(canned_2024_01, MediaType.APPLICATION_JSON));

            String canned_2024_02= readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/2024-02.json");
            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-02-01&end_date=2024-02-29"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(canned_2024_02, MediaType.APPLICATION_JSON));

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-03-01&end_date=2024-03-31"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            mockServer.getServer().expect(once(), requestTo("http://localhost/api/v1/das/data/35234913-aa3c-48ec-b9a4-77f822f66ef8?is_to_index=true&start_date=2024-04-01&end_date=2024-04-30"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            ResponseEntity<?> v = controller.indexDatasetByUUID("35234913-aa3c-48ec-b9a4-77f822f66ef8");
        }
        finally {
            mockServer.getServer().reset();
        }
    }
}
