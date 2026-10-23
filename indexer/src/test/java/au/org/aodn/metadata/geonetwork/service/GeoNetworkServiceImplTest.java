package au.org.aodn.metadata.geonetwork.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeoNetworkServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate indexerRestTemplate;
    private FIFOCache<String, Map<String, ?>> cache;
    private GeoNetworkServiceImpl geoNetworkService;

    @BeforeEach
    void setUp() {
        indexerRestTemplate = mock(RestTemplate.class);
        cache = mock(FIFOCache.class);
        geoNetworkService = new GeoNetworkServiceImpl(
                "http://localhost",
                "records",
                mock(ElasticsearchClient.class),
                indexerRestTemplate,
                cache
        );
        // No spring proxy here, self is the object itself
        ReflectionTestUtils.setField(geoNetworkService, "self", geoNetworkService);
        // Do not wait between retry in test
        geoNetworkService.upstreamRetryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .noBackoff()
                .retryOn(List.of(HttpServerErrorException.class, ResourceAccessException.class))
                .build();
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
    void getHarvestSourceUriReturnsUriOfGeonetworkHarvester() {
        mockExtraInfo(Map.of(
                "isHarvested", true,
                "harvesterType", "org.fao.geonet.kernel.harvest.harvester.geonet.v21_3.GeonetHarvester",
                "harvesterUri", "https://catalogue-imos.aodn.org.au/geonetwork/"));

        assertEquals(Optional.of("https://catalogue-imos.aodn.org.au/geonetwork"),
                geoNetworkService.getHarvestSourceUri("uuid"));
    }

    @Test
    void getHarvestSourceUriIgnoresNonGeonetworkHarvester() {
        mockExtraInfo(Map.of(
                "isHarvested", true,
                "harvesterType", "org.fao.geonet.kernel.harvest.harvester.csw.CswHarvester",
                "harvesterUri", "https://example.org/csw"));

        assertEquals(Optional.empty(), geoNetworkService.getHarvestSourceUri("uuid"));
    }

    @Test
    void getHarvestSourceUriIgnoresRecordNotHarvested() {
        mockExtraInfo(Map.of("isHarvested", false, "schemaid", "iso19115-3.2018"));

        assertEquals(Optional.empty(), geoNetworkService.getHarvestSourceUri("uuid"));
    }

    @Test
    void getHarvestSourceUriReturnsEmptyWhenInfoMissing() {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.endsWith("/info")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                anyMap()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertEquals(Optional.empty(), geoNetworkService.getHarvestSourceUri("uuid"));
    }

    @Test
    void getUpstreamAssociatedRecordsReturnsRecordEntryAndSkipsCache() {
        Map<String, Object> related = Map.of("children", List.of(Map.of("id", "child")));
        when(indexerRestTemplate.exchange(
                eq("https://catalogue-imos.aodn.org.au/geonetwork/srv/api/related?type=parent&type=brothersAndSisters&type=children&uuid={uuid}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("uuid", related)));

        assertEquals(related,
                geoNetworkService.getUpstreamAssociatedRecords("https://catalogue-imos.aodn.org.au/geonetwork", "uuid"));
        verifyNoInteractions(cache);
    }

    @Test
    void getUpstreamAssociatedRecordsReturnsEmptyWhenUpstreamDown() {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.contains("/srv/api/related")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                anyMap()))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null, null, null));

        assertEquals(Map.of(),
                geoNetworkService.getUpstreamAssociatedRecords("https://catalogue-imos.aodn.org.au/geonetwork", "uuid"));
        verify(indexerRestTemplate, times(3)).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class), anyMap());
    }

    @Test
    void getUpstreamAssociatedRecordsReturnsEmptyWhenRecordNotInUpstream() {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.contains("/srv/api/related")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                anyMap()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertEquals(Map.of(),
                geoNetworkService.getUpstreamAssociatedRecords("https://catalogue-imos.aodn.org.au/geonetwork", "uuid"));
    }

    @SuppressWarnings("unchecked")
    private void mockExtraInfo(Map<String, Object> info) {
        when(indexerRestTemplate.exchange(
                argThat(url -> url.contains("/geonetwork/srv/api/aodn/records/") && url.endsWith("/info")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                anyMap()))
                .thenReturn((ResponseEntity) ResponseEntity.ok(info));
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
