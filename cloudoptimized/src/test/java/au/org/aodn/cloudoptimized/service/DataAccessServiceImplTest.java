package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.FeatureGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PolygonGeoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
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
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockJsonResponse));

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
    }

}
