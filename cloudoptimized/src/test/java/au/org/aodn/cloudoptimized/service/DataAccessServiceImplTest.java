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

        dataAccessService = new DataAccessServiceImpl(
                "server-url",
                "base-url",
                mockRestTemplate,
                mock(WebClient.class),
                new ObjectMapper()
        );
    }

    @Test
    void getZarrIndexingDataByMonthTest() {
        var polygon = new PolygonGeoJson(new ArrayList< List<List<Double>>>());
        var feature = new FeatureGeoJson(polygon);
        var features = List.of(feature);
        var mockFeatureCollection = new FeatureCollectionGeoJson();
        mockFeatureCollection.setFeatures(features);
        var mockResponse = new ResponseEntity<>(mockFeatureCollection, HttpStatus.OK);


        when(mockRestTemplate.exchange(
                anyString(),
                any(),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(mockResponse);

        DataAccessServiceImpl service = Mockito.mock(DataAccessServiceImpl.class);
        String testMonth = "2024-06";
        List<String> expected = Collections.singletonList("test-data");

        var result = dataAccessService.getZarrIndexingDataByMonth("test-uuid", "test-key", YearMonth.of(2024, 6));


        // Assert
        assertNotNull(result);
        assertEquals(mockFeatureCollection, result);

    }
}