package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.model.MetadataFields;
import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.FeatureGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PointGeoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataAccessServiceImplTest {

    private RestTemplate mockRestTemplate;

    @BeforeEach
    public void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
    }

    @Test
    void testGetNotebookLink_Success() {
        // Arrange
        String uuid = "test-uuid";
        // The API returns a JSON string with quotes, e.g., "https://github.com/..."
        String jsonResponseWithQuotes = "\"https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/test_notebook.ipynb\"";
        String expectedUrl = "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/test_notebook.ipynb";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(jsonResponseWithQuotes, HttpStatus.OK);

        DataAccessServiceImpl dataAccessService = new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper()
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedUrl, result.get());
        // Verify the quotes are properly stripped
        assertFalse(result.get().startsWith("\""));
        assertFalse(result.get().endsWith("\""));
    }

    @Test
    void testGetNotebookLink_EmptyResponse() {
        // Arrange
        String uuid = "test-uuid";
        ResponseEntity<String> responseEntity = new ResponseEntity<>("", HttpStatus.OK);

        DataAccessServiceImpl dataAccessService = new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper()
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetNotebookLink_NotFoundReturnsEmpty() {
        // Arrange
        String uuid = "test-uuid";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        DataAccessServiceImpl dataAccessService = new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper()
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetNotebookLink_ExceptionReturnsEmpty() {
        // Arrange
        String uuid = "test-uuid";

        DataAccessServiceImpl dataAccessService = new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper()
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenThrow(new RuntimeException("Connection refused"));

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid);

        // Assert
        assertTrue(result.isEmpty());
    }

}
