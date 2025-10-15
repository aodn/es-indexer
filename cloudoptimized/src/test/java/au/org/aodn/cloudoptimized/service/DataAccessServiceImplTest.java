package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.model.MetadataFields;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.FeatureGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PointGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PolygonGeoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataAccessServiceImplTest {

    private RestTemplate mockRestTemplate;
    private DataAccessServiceImpl dataAccessService;

    @BeforeEach
    public void setUp() {
        mockRestTemplate = mock(RestTemplate.class);


    }

    @Test
    void testGetZarrIndexingDataByMonth() {
        // Arrange
        String uuid = "test-uuid";
        String key = "test-key";
        YearMonth yearMonth = YearMonth.of(2025, 10);
        String expectedUrl = "http://example.com/data/test-uuid/test-key/zarr_rect?start_date=2025-10-01T00:00:00.000000000Z&end_date=2025-10-31T23:59:59.999999999Z";

        FeatureCollectionGeoJson mockResponse = new FeatureCollectionGeoJson(
                List.of(new FeatureGeoJson(
                        new PointGeoJson(BigDecimal.ONE, BigDecimal.ONE)
                )),
                null
        );
        ResponseEntity<FeatureCollectionGeoJson> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        dataAccessService = new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper()
        );

        // Correct the mock setup to ensure the responseEntity is not null
        when(mockRestTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);


        // Act
        FeatureCollectionGeoJson result = dataAccessService.getZarrIndexingDataByMonth(uuid, key, yearMonth);

        // Assert
        assertNotNull(result);
        assertEquals(mockResponse, result);
    }

    @Test
    void testGetTemporalExtentOf_retriesOnFailure() {
        String uuid = "testUuid";
        String key = "testKey";
        var expected = List.of(TemporalExtent.builder()
                .startDate("2020-01-01T00:00:00Z")
                .endDate("2020-12-31T23:59:59Z")
                .build());
        dataAccessService = new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper()
        );
        // First call throws, second call returns success
        when(mockRestTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class),
                anyMap()
        ))
        .thenThrow(new RuntimeException("Temporary error"))
        .thenReturn(new ResponseEntity<>(expected, HttpStatus.OK));

        // Call the method under test
        List<TemporalExtent> result = dataAccessService.getTemporalExtentOf(uuid, key);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2020-01-01T00:00:00Z", result.get(0).getStartDate());
        assertEquals("2020-12-31T23:59:59Z", result.get(0).getEndDate());
        verify(mockRestTemplate, times(2)).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class),
                anyMap()
        );
    }

    @Test
    void testGetIndexingDatasetByMonth()   {
        // Mock WebClient components
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Prepare 3 valid mock SSE event JSON strings with different dates (interval 5 days)
        String sseEventJson1 = """
                {"status":"completed","message":"chunk 1","data":[{"latitude":-35.0,"longitude":150.0,"time":"2025-10-01"}]}
                """;
        String sseEventJson2 = """
                {"status":"completed","message":"chunk 2","data":[{"latitude":-35.0,"longitude":150.0,"time":"2025-10-06"}]}
                """;
        String sseEventJson3 = """
                {"status":"completed","message":"chunk 3/end","data":[{"latitude":-35.0,"longitude":150.0,"time":"2025-10-11"}]}
                """;

        // Mock the WebClient behavior to return 3 SSE events
        WebClient mockWebClient = mock(WebClient.class);
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyMap())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class))
                .thenReturn(reactor.core.publisher.Flux.just(sseEventJson1, sseEventJson2, sseEventJson3));

        // Inject the mocked WebClient into the service
        dataAccessService = new DataAccessServiceImpl(
                "server-url",
                "base-url",
                mockRestTemplate,
                mockWebClient,
                new ObjectMapper()
        );

        // Prepare test parameters
        String uuid = "test-uuid";
        String key = "test-key";
        YearMonth yearMonth = YearMonth.of(2025, 10);
        List<MetadataFields> fields = List.of();



        String newSseEventJson1 = """
                {"status":"completed","message":"chunk 1/end","data":[{"latitude":-35.0,"longitude":150.0,"time":"2025-10-01"}]}
                """;
        String newSseEventJson2 = """
                {"status":"completed","message":"chunk 1/end","data":[{"latitude":-35.0,"longitude":150.0,"time":"2025-10-06"}]}
                """;
        String newSseEventJson3 = """
                {"status":"completed","message":"chunk 1/end","data":[{"latitude":-35.0,"longitude":150.0,"time":"2025-10-11"}]}
                """;
        String newSseEventJson4 = """
                {"status":"completed","message":"chunk 0/end","data":[]}
                """;

        when(responseSpec.bodyToFlux(String.class))
                .thenReturn(reactor.core.publisher.Flux.just(newSseEventJson1))
                .thenReturn(reactor.core.publisher.Flux.just(newSseEventJson2))
                .thenReturn(reactor.core.publisher.Flux.just(newSseEventJson3))
                .thenReturn(reactor.core.publisher.Flux.just(newSseEventJson4))
        ;

        var result = dataAccessService.getIndexingDatasetByMonth(uuid, key, yearMonth, fields);


        var feature2 = result.getFeatures().get(0);
        assertEquals(-35.0, ((PointGeoJson) feature2.getGeometry()).getCoordinates().get(1).doubleValue(), 1e-8);
        assertEquals(150.0, ((PointGeoJson) feature2.getGeometry()).getCoordinates().get(0).doubleValue(), 1e-8);
        assertEquals("2025-10", feature2.getProperties().get("date"));
        assertEquals(3L, feature2.getProperties().get("count"));

    }

}
