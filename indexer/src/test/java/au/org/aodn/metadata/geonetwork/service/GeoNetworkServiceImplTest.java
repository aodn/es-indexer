package au.org.aodn.metadata.geonetwork.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoNetworkServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate indexerRestTemplate;
    private GeoNetworkServiceImpl geoNetworkService;

    @BeforeEach
    void setUp() {
        indexerRestTemplate = mock(RestTemplate.class);
        geoNetworkService = new GeoNetworkServiceImpl(
                "http://localhost",
                "records",
                mock(ElasticsearchClient.class),
                indexerRestTemplate,
                mock(FIFOCache.class)
        );
    }

    @Test
    void findCategoriesByIdReturnsTagNamesWithOriginalCase() throws IOException {
        mockTagsResponse(ResponseEntity.ok(objectMapper.readTree("""
                [
                  { "id": 1, "name": "portal:IMOS", "label": { "eng": "portal:IMOS" } },
                  { "id": 2, "name": "MARVL", "label": { "eng": "MARVL" } }
                ]
                """)));

        assertEquals(List.of("portal:IMOS", "MARVL"), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdSkipsTagWithoutName() throws IOException {
        mockTagsResponse(ResponseEntity.ok(objectMapper.readTree("""
                [
                  { "id": 1, "label": { "eng": "no name here" } },
                  { "id": 2, "name": "portal:IMOS" }
                ]
                """)));

        assertEquals(List.of("portal:IMOS"), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdReturnsEmptyListWhenRecordHasNoTag() throws IOException {
        mockTagsResponse(ResponseEntity.ok(objectMapper.readTree("[]")));

        assertEquals(List.of(), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdReturnsEmptyListWhenBodyIsMissing() {
        mockTagsResponse(ResponseEntity.ok(null));

        assertEquals(List.of(), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdReturnsEmptyListWhenRecordIsMissing() {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.contains("/geonetwork/srv/api/records/") && url.endsWith("/tags")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class),
                anyMap()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertEquals(List.of(), geoNetworkService.findCategoriesById("uuid"));
    }

    private void mockTagsResponse(ResponseEntity<JsonNode> response) {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.contains("/geonetwork/srv/api/records/") && url.endsWith("/tags")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class),
                anyMap()))
                .thenReturn(response);
    }
}
