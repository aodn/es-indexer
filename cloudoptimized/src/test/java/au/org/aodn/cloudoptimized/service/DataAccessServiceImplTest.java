package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.model.MetadataFields;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.cloudoptimized.model.geojson.FeatureGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PointGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PolygonGeoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
        // Mock WebClient components
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Define the mock response
        String mockJsonResponse = """
                    {
                                     "type": "FeatureCollection",
                                     "features": [
                                         {
                                             "type": "Feature",
                                             "geometry": {
                                                 "type": "Polygon",
                                                 "coordinates": [
                                                     [
                                                         [
                                                             132.953957,
                                                             -37.454966
                                                         ],
                                                         [
                                                             132.953957,
                                                             -34.823425
                                                         ],
                                                         [
                                                             137.443624,
                                                             -34.823425
                                                         ],
                                                         [
                                                             137.443624,
                                                             -37.454966
                                                         ],
                                                         [
                                                             132.953957,
                                                             -37.454966
                                                         ]
                                                     ]
                                                 ]
                                             },
                                             "properties": {
                                                 "date": "2011-05",
                                                 "count": 3849480
                                             }
                                         }
                                     ],
                                     "properties": {
                                         "date": "2011-05",
                                         "collection": "db049981-3d4e-4cb2-9c4b-e697650845b9",
                                         "key": "radar_SouthAustraliaGulfs_wind_delayed_qc.zarr"
                                     }
                                 }
                """;

        // Mock the WebClient behavior
        WebClient mockWebClient = mock(WebClient.class);
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenThrow(new RuntimeException("502 Bad Gateway")) // First request
                .thenReturn(Mono.just(mockJsonResponse)); // second request (successful);

        // Inject the mocked WebClient into the service
        dataAccessService = new DataAccessServiceImpl(
                "server-url",
                "base-url",
                mockRestTemplate,
                mockWebClient,
                new ObjectMapper()
        );

        // Call the method under test
        var result = dataAccessService.getZarrIndexingDataByMonth("test-uuid", "test-key", YearMonth.of(2024, 6));

        // Assert the result
        assertNotNull(result);
        assertEquals(1, result.getFeatures().size());
        FeatureGeoJson feature = result.getFeatures().get(0);
        assertEquals("Feature", feature.getType());
        assertTrue(feature.getGeometry() instanceof PolygonGeoJson);
        PolygonGeoJson polygon = (PolygonGeoJson) feature.getGeometry();
        assertEquals(5, polygon.getCoordinates().get(0).size()); // 5 coordinates in the polygon
        assertEquals("2011-05", feature.getProperties().get("date"));
        assertEquals(3849480, feature.getProperties().get("count"));
        assertEquals("2011-05", result.getProperties().get("date"));
        assertEquals("db049981-3d4e-4cb2-9c4b-e697650845b9", result.getProperties().get("collection"));
        assertEquals("radar_SouthAustraliaGulfs_wind_delayed_qc.zarr", result.getProperties().get("key"));

        verify(responseSpec, times(2)).bodyToMono(String.class);
    }

    @Test
    void testGetTemporalExtentOf_withRetry() {
        // Mock WebClient components
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Prepare mock response (as JSON array)
        String mockJsonResponse = """
                [
                    {"start":"2020-01-01T00:00:00Z","end":"2020-12-31T23:59:59Z"}
                ]
                """;

        // Mock the WebClient behavior
        WebClient mockWebClient = mock(WebClient.class);
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(any(Class.class)))
                .thenThrow(new RuntimeException("502 Bad Gateway")) // First call fails
                .thenReturn(reactor.core.publisher.Flux.just(
//                        new TemporalExtent("2020-01-01T00:00:00Z", "2020-12-31T23:59:59Z")
                        TemporalExtent.builder()
                                .startDate("2020-01-01T00:00:00Z")
                                .endDate("2020-12-31T23:59:59Z")
                                .build()
                )); // Second call succeeds

        // Inject the mocked WebClient into the service
        dataAccessService = new DataAccessServiceImpl(
                "server-url",
                "base-url",
                mockRestTemplate,
                mockWebClient,
                new ObjectMapper()
        );

        // Call the method under test
        var result = dataAccessService.getTemporalExtentOf("test-uuid", "test-key");

        // Assert the result
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2020-01-01T00:00:00Z", result.get(0).getStartDate());
        assertEquals("2020-12-31T23:59:59Z", result.get(0).getEndDate());

        // Verify retry (should call twice)
        verify(responseSpec, times(2)).bodyToFlux(any(Class.class));
    }

    @Test
    void testGetIndexingDatasetByMonth() throws Exception {
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
