package au.org.aodn.metadata.geonetwork.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoNetworkServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ElasticsearchClient gn4ElasticClient;
    private GeoNetworkServiceImpl geoNetworkService;

    @BeforeEach
    void setUp() {
        gn4ElasticClient = mock(ElasticsearchClient.class);
        geoNetworkService = new GeoNetworkServiceImpl(
                "http://localhost",
                "records",
                gn4ElasticClient,
                mock(RestTemplate.class),
                mock(FIFOCache.class)
        );
    }

    @Test
    void findCategoriesByIdReturnsArrayValuesWithOriginalCase() throws IOException {
        ObjectNode source = objectMapper.createObjectNode();
        source.putArray("cat").add("portal:IMOS").add("MARVL");
        mockSearchHit(source);

        assertEquals(List.of("portal:IMOS", "MARVL"), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdHandlesBareString() throws IOException {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("cat", "portal:IMOS");
        mockSearchHit(source);

        assertEquals(List.of("portal:IMOS"), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdReturnsEmptyListWhenCategoryIsMissing() throws IOException {
        mockSearchHit(objectMapper.createObjectNode());

        assertEquals(List.of(), geoNetworkService.findCategoriesById("uuid"));
    }

    @Test
    void findCategoriesByIdReturnsEmptyListWhenRecordIsMissing() throws IOException {
        SearchResponse<ObjectNode> response = mock(SearchResponse.class);
        HitsMetadata<ObjectNode> hits = mock(HitsMetadata.class);
        when(response.hits()).thenReturn(hits);
        when(hits.hits()).thenReturn(List.of());
        when(gn4ElasticClient.search(any(SearchRequest.class), eq(ObjectNode.class))).thenReturn(response);

        assertEquals(List.of(), geoNetworkService.findCategoriesById("uuid"));
    }

    private void mockSearchHit(ObjectNode source) throws IOException {
        SearchResponse<ObjectNode> response = mock(SearchResponse.class);
        HitsMetadata<ObjectNode> hits = mock(HitsMetadata.class);
        Hit<ObjectNode> hit = mock(Hit.class);
        when(response.hits()).thenReturn(hits);
        when(hits.hits()).thenReturn(List.of(hit));
        when(hit.source()).thenReturn(source);
        when(gn4ElasticClient.search(any(SearchRequest.class), eq(ObjectNode.class))).thenReturn(response);
    }
}
