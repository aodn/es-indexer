package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.exception.IndexerSettingConflictException;
import au.org.aodn.esindexer.service.IndexerMetadataService;
import au.org.aodn.esindexer.service.IndexerSettingService;
import au.org.aodn.esindexer.service.IndexerSettingService.SkipCoSetting;
import au.org.aodn.metadata.geonetwork.exception.MetadataNotFoundException;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test of the skip_co endpoints, everything behind the controller is mocked.
 */
class IndexerSettingControllerTest {

    private static final String UPDATED_AT = "2026-11-12T00:00:00Z";

    @Mock
    private IndexerSettingService indexerSettingService;

    @Mock
    private IndexerMetadataService indexerMetadata;

    @Mock
    private GeoNetworkService geonetworkResourceService;

    @InjectMocks
    private IndexerSettingController controller;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // By default every uuid is found in GeoNetwork and indexed
        when(geonetworkResourceService.searchRecordBy(anyString()))
                .thenAnswer(ans -> "xml-of-" + ans.getArgument(0));
        when(indexerMetadata.indexMetadata(anyString()))
                .thenReturn(CompletableFuture.completedFuture(ResponseEntity.ok("")));
    }

    @Test
    void getReturnsTheFullListWithoutReindexResult() throws Exception {
        when(indexerSettingService.getSkipCoSetting())
                .thenReturn(new SkipCoSetting(List.of("uuid-1", "uuid-2"), UPDATED_AT));

        var response = controller.getSkipCoUuids();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-1", "uuid-2"), response.getBody().uuids());
        assertEquals(UPDATED_AT, response.getBody().updatedAt());

        // The GET response has no reindexed and no reindex_failed field
        JsonNode json = new ObjectMapper().valueToTree(response.getBody());
        assertEquals(UPDATED_AT, json.get("updated_at").asText());
        assertFalse(json.has("reindexed"));
        assertFalse(json.has("reindex_failed"));
    }

    @Test
    void addUpdatesTheListFirstThenReindexAndReturnsTheFullList() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("uuid-3")))
                .thenReturn(new SkipCoSetting(List.of("uuid-1", "uuid-2", "uuid-3"), UPDATED_AT));

        var response = controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of("uuid-3")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-1", "uuid-2", "uuid-3"), response.getBody().uuids());
        assertEquals(List.of("uuid-3"), response.getBody().reindexed());
        assertEquals(List.of(), response.getBody().reindexFailed());

        // The reindex reads the list, so the list must be written before it
        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).addSkipCoUuids(List.of("uuid-3"));
        inOrder.verify(indexerMetadata).indexMetadata("xml-of-uuid-3");

        JsonNode json = new ObjectMapper().valueToTree(response.getBody());
        assertTrue(json.has("reindexed"));
        assertTrue(json.has("reindex_failed"));
    }

    @Test
    void addCleansTheInput() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("uuid-1", "uuid-2")))
                .thenReturn(new SkipCoSetting(List.of("uuid-1", "uuid-2"), UPDATED_AT));

        var response = controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(
                Arrays.asList(" uuid-1 ", "uuid-2", "", null, "uuid-1")));

        // Trimmed, blank and duplicate removed, each uuid reindexed once
        verify(indexerSettingService).addSkipCoUuids(List.of("uuid-1", "uuid-2"));
        assertEquals(List.of("uuid-1", "uuid-2"), response.getBody().reindexed());
        verify(indexerMetadata, times(2)).indexMetadata(anyString());
    }

    @Test
    void emptyInputIsBadRequestAndNothingChanges() throws Exception {
        for (var request : Arrays.asList(
                null,
                new IndexerSettingController.UuidsRequest(null),
                new IndexerSettingController.UuidsRequest(List.of()),
                new IndexerSettingController.UuidsRequest(List.of(" ")))) {

            var e = assertThrows(ResponseStatusException.class, () -> controller.addSkipCoUuids(request));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
        var e = assertThrows(ResponseStatusException.class, () -> controller.removeSkipCoUuids(List.of(" ")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());

        verifyNoInteractions(indexerSettingService, indexerMetadata, geonetworkResourceService);
    }

    @Test
    void failedReindexShowsInReindexFailedAndDoesNotStopTheOthers() throws Exception {
        List<String> uuids = List.of("not-in-geonetwork", "not-published", "index-error", "ok");
        when(indexerSettingService.addSkipCoUuids(uuids))
                .thenReturn(new SkipCoSetting(uuids, UPDATED_AT));

        when(geonetworkResourceService.searchRecordBy("not-in-geonetwork"))
                .thenThrow(new MetadataNotFoundException("not found"));
        // 204 is what indexMetadata() returns for a record that is not published
        when(indexerMetadata.indexMetadata("xml-of-not-published"))
                .thenReturn(CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null)));
        when(indexerMetadata.indexMetadata("xml-of-index-error"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("index error")));

        var response = controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(uuids));

        // Still 200, and the uuids stay on the list
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(uuids, response.getBody().uuids());
        assertEquals(List.of("ok"), response.getBody().reindexed());
        assertEquals(List.of("not-in-geonetwork", "not-published", "index-error"), response.getBody().reindexFailed());
    }

    @Test
    void removeUpdatesTheListThenReindex() throws Exception {
        when(indexerSettingService.removeSkipCoUuids(List.of("uuid-1", "uuid-2")))
                .thenReturn(new SkipCoSetting(List.of("uuid-3"), UPDATED_AT));

        // Spring splits ?uuids=uuid-1,uuid-2 into a list
        var response = controller.removeSkipCoUuids(List.of("uuid-1", "uuid-2"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-3"), response.getBody().uuids());
        assertEquals(List.of("uuid-1", "uuid-2"), response.getBody().reindexed());
        assertEquals(List.of(), response.getBody().reindexFailed());

        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).removeSkipCoUuids(List.of("uuid-1", "uuid-2"));
        inOrder.verify(indexerMetadata, times(2)).indexMetadata(anyString());
    }

    @Test
    void failedListUpdateMeansNoReindex() throws Exception {
        when(indexerSettingService.addSkipCoUuids(any()))
                .thenThrow(new IndexerSettingConflictException("changed by someone else"));

        assertThrows(IndexerSettingConflictException.class,
                () -> controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of("uuid-1"))));

        verifyNoInteractions(indexerMetadata, geonetworkResourceService);
    }
}
