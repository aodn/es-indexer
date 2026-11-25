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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataAccessServiceImplTest {

    private static final String NOTEBOOK_URL = "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/test_notebook.ipynb";

    private RestTemplate mockRestTemplate;
    private NotebookUrlValidator mockValidator;

    @BeforeEach
    public void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        // Valid by default, so the existing tests do not care about the validator
        mockValidator = mock(NotebookUrlValidator.class);
        when(mockValidator.check(anyString())).thenReturn(NotebookUrlCheck.ok());
    }

    private DataAccessServiceImpl createService() {
        return new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                mockRestTemplate,
                null,
                new ObjectMapper(),
                mockValidator
        );
    }

    /**
     * Make the data access service answer the notebook_url call
     * @param status - The http status of the answer
     * @param json - The raw body, the real service returns a JSON string e.g. "https://..." (with the quotes)
     */
    @SuppressWarnings("unchecked")
    private void dasReturns(HttpStatus status, String json) {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(new ResponseEntity<>(json, status));
    }

    private static String quoted(String url) {
        return "\"" + url + "\"";
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
                new ObjectMapper(),
                mockValidator
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid, Set.of());

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
                new ObjectMapper(),
                mockValidator
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid, Set.of());

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
                new ObjectMapper(),
                mockValidator
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenReturn(responseEntity);

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid, Set.of());

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
                new ObjectMapper(),
                mockValidator
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                anyMap()
        )).thenThrow(new RuntimeException("Connection refused"));

        // Act
        Optional<String> result = dataAccessService.getNotebookLink(uuid, Set.of());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetNotebookLink_InMetadataSkipped() {
        // The metadata has the same link, the metadata is the point of truth, and no request is spent on the url
        dasReturns(HttpStatus.OK, quoted(NOTEBOOK_URL));

        Optional<String> result = createService().getNotebookLink("test-uuid", Set.of(NOTEBOOK_URL, "https://other.example.org/a"));

        assertTrue(result.isEmpty());
        verifyNoInteractions(mockValidator);
    }

    @Test
    void testGetNotebookLink_DifferentHrefInMetadataKept() {
        // Same notebook but a broken href in the metadata (missing the b), so it is not the same link
        dasReturns(HttpStatus.OK, quoted(NOTEBOOK_URL));

        Optional<String> result = createService().getNotebookLink("test-uuid", Set.of(NOTEBOOK_URL.substring(0, NOTEBOOK_URL.length() - 1)));

        assertEquals(Optional.of(NOTEBOOK_URL), result);
        verify(mockValidator).check(NOTEBOOK_URL);
    }

    @Test
    void testGetNotebookLink_NullOrEmptyHrefsKept() {
        dasReturns(HttpStatus.OK, quoted(NOTEBOOK_URL));
        DataAccessServiceImpl service = createService();

        assertEquals(Optional.of(NOTEBOOK_URL), service.getNotebookLink("test-uuid", null));
        assertEquals(Optional.of(NOTEBOOK_URL), service.getNotebookLink("test-uuid", Set.of()));
    }

    @Test
    void testGetNotebookLink_ValidatorInvalidDropsLink() {
        // e.g. the notebook is not there (404), or GitHub throttle us (429)
        dasReturns(HttpStatus.OK, quoted(NOTEBOOK_URL));
        when(mockValidator.check(NOTEBOOK_URL)).thenReturn(NotebookUrlCheck.invalid("HTTP 404"));

        Optional<String> result = createService().getNotebookLink("test-uuid", Set.of());

        assertTrue(result.isEmpty());
        verify(mockValidator).check(NOTEBOOK_URL);
    }

    @Test
    void testGetNotebookLink_PlaceholderDropsLinkWithoutRequest() {
        // The real validator, with a rest template that must not be used, as the placeholder is not a url
        RestTemplate notebookHostRestTemplate = mock(RestTemplate.class);
        mockValidator = new HttpNotebookUrlValidator(notebookHostRestTemplate, true);
        dasReturns(HttpStatus.OK, quoted("FILL UP MANUALLY - CHECK DOCUMENTATION"));

        Optional<String> result = createService().getNotebookLink("test-uuid", Set.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(notebookHostRestTemplate);
    }

    @Test
    void testGetNotebookLink_DasNotFoundNeverCallValidator() {
        dasReturns(HttpStatus.NOT_FOUND, null);

        Optional<String> result = createService().getNotebookLink("test-uuid", Set.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(mockValidator);
    }

    @Test
    void testGetNotebookLink_ValidUrlAsksDasOnceAndValidatorOnce() {
        // The url is checked once by the validator, which is not the rest template of the data access service
        dasReturns(HttpStatus.OK, quoted(NOTEBOOK_URL));

        Optional<String> result = createService().getNotebookLink("test-uuid", Set.of());

        assertEquals(Optional.of(NOTEBOOK_URL), result);
        verify(mockValidator, times(1)).check(NOTEBOOK_URL);
        verify(mockRestTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class), anyMap());
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
