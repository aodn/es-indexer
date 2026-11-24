package au.org.aodn.metadata.geonetwork.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoNetworkServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate indexerRestTemplate;
    private ElasticsearchClient gn4ElasticClient;
    private GeoNetworkServiceImpl geoNetworkService;

    @BeforeEach
    void setUp() {
        indexerRestTemplate = mock(RestTemplate.class);
        gn4ElasticClient = mock(ElasticsearchClient.class);
        geoNetworkService = new GeoNetworkServiceImpl(
                "http://localhost",
                "records",
                gn4ElasticClient,
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

    @Test
    void findGroupByIdReturnsEnglishLabel() throws IOException {
        mockGroupOwner("1460");
        mockGroupResponse(ResponseEntity.ok(objectMapper.readTree("""
                { "id": 1460, "name": "AIMS_IMOS", "label": { "eng": "AIMS, IMOS", "fre": "AIMS_IMOS" } }
                """)));

        assertEquals("AIMS, IMOS", geoNetworkService.findGroupById("uuid"));
    }

    @Test
    void findGroupByIdThrowsWhenLabelIsMissing() throws IOException {
        mockGroupOwner("1460");
        mockGroupResponse(ResponseEntity.ok(objectMapper.readTree("""
                { "id": 1460, "name": "AIMS_IMOS" }
                """)));

        assertNoEnglishLabelError();
    }

    @Test
    void findGroupByIdThrowsWhenEnglishLabelIsMissing() throws IOException {
        mockGroupOwner("1460");
        mockGroupResponse(ResponseEntity.ok(objectMapper.readTree("""
                { "id": 1460, "name": "AIMS_IMOS", "label": { "fre": "AIMS, IMOS" } }
                """)));

        assertNoEnglishLabelError();
    }

    @Test
    void findGroupByIdThrowsWhenLabelIsBlank() throws IOException {
        mockGroupOwner("1460");
        mockGroupResponse(ResponseEntity.ok(objectMapper.readTree("""
                { "id": 1460, "name": "AIMS_IMOS", "label": { "eng": "  " } }
                """)));

        assertNoEnglishLabelError();
    }

    private void assertNoEnglishLabelError() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> geoNetworkService.findGroupById("uuid"));

        assertEquals(
                "Group 1460 (AIMS_IMOS) of record uuid has no English label (label.eng), please add it in the GN4 group config",
                e.getMessage());
    }

    private void mockGroupOwner(String groupId) throws IOException {
        ObjectNode source = objectMapper.createObjectNode().put("groupOwner", groupId);
        SearchResponse<ObjectNode> response = SearchResponse.of(r -> r
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(List.of(Hit.of(hit -> hit.index("records").id("1").source(source))))));

        when(gn4ElasticClient.search(any(SearchRequest.class), eq(ObjectNode.class))).thenReturn(response);
    }

    private void mockGroupResponse(ResponseEntity<JsonNode> response) {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.contains("/geonetwork/srv/api/groups/")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class),
                anyMap()))
                .thenReturn(response);
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
