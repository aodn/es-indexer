package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.enums.MetadataKeyType;
import au.org.aodn.cloudoptimized.model.MetadataEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;


import java.util.Map;
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

    @Test
    void testMetadataEntity_ReadType() throws Exception {
        // Arrange: the metadata endpoint response, one key of each kind
        String json = """
                {
                  "a.parquet": {"uuid": "test-uuid", "dname": "a.parquet", "type": "cloud_optimised"},
                  "original_netcdfs": {"uuid": "test-uuid", "dname": "original_netcdfs", "lat": null, "lng": null, "depth": null, "type": "data_index"},
                  "b.zarr": {"uuid": "test-uuid", "dname": "b.zarr"},
                  "c.parquet": {"uuid": "test-uuid", "dname": "c.parquet", "type": "something_new"}
                }
                """;

        // Act
        Map<String, MetadataEntity> result = new ObjectMapper().readValue(json, new TypeReference<>() {
        });

        // Assert
        assertEquals(MetadataKeyType.CLOUD_OPTIMISED, result.get("a.parquet").getType());
        assertEquals(MetadataKeyType.DATA_INDEX, result.get("original_netcdfs").getType());
        assertEquals("original_netcdfs", result.get("original_netcdfs").getDname());
        // No type means the kind is unknown
        assertNull(result.get("b.zarr").getType());
        // An unknown type does not fail the response
        assertNull(result.get("c.parquet").getType());
    }

}
