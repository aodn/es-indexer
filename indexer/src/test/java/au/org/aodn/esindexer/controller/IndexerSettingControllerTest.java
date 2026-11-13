package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.exception.IndexerSettingConflictException;
import au.org.aodn.esindexer.service.IndexerMetadataService;
import au.org.aodn.esindexer.service.IndexerSettingService;
import au.org.aodn.esindexer.service.IndexerSettingService.SkipCoSetting;
import au.org.aodn.esindexer.service.IndexerSettingService.SkipCoUpdate;
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
        assertFalse(json.has("not_found"));
    }

    @Test
    void addUpdatesTheListFirstThenReindexAndReturnsTheFullList() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("uuid-3")))
                .thenReturn(new SkipCoUpdate(List.of("uuid-1", "uuid-2", "uuid-3"), UPDATED_AT, List.of("uuid-3")));

        var response = controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of("uuid-3")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-1", "uuid-2", "uuid-3"), response.getBody().uuids());
        assertEquals(List.of("uuid-3"), response.getBody().reindexed());
        assertEquals(List.of(), response.getBody().reindexFailed());
        assertEquals(List.of(), response.getBody().notFound());

        // Everything is reindexed, so there is nothing to undo
        verify(indexerSettingService, never()).removeSkipCoUuids(any());

        // The reindex reads the list, so the list must be written before it
        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).addSkipCoUuids(List.of("uuid-3"));
        inOrder.verify(indexerMetadata).indexMetadata("xml-of-uuid-3");

        JsonNode json = new ObjectMapper().valueToTree(response.getBody());
        assertTrue(json.has("reindexed"));
        assertTrue(json.has("reindex_failed"));
        assertTrue(json.has("not_found"));
    }

    @Test
    void addCleansTheInput() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("uuid-1", "uuid-2")))
                .thenReturn(new SkipCoUpdate(List.of("uuid-1", "uuid-2"), UPDATED_AT, List.of("uuid-1", "uuid-2")));

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

    /** A uuid that is not reindexed is taken off the list again, with no other reindex. */
    @Test
    void addIsUndoneForTheUuidsThatAreNotReindexed() throws Exception {
        List<String> uuids = List.of("not-in-geonetwork", "not-published", "index-error", "ok");
        when(indexerSettingService.addSkipCoUuids(uuids))
                .thenReturn(new SkipCoUpdate(uuids, UPDATED_AT, uuids));
        when(indexerSettingService.removeSkipCoUuids(any()))
                .thenReturn(new SkipCoUpdate(List.of("ok"), UPDATED_AT, List.of()));
        mockReindexResults();

        var response = controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(uuids));

        // Still 200, a failed uuid does not stop the others
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("ok"), response.getBody().uuids());
        assertEquals(List.of("ok"), response.getBody().reindexed());
        assertEquals(List.of("not-published", "index-error"), response.getBody().reindexFailed());
        assertEquals(List.of("not-in-geonetwork"), response.getBody().notFound());

        // The undo comes after the reindex, and it only writes the list
        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).addSkipCoUuids(uuids);
        inOrder.verify(indexerMetadata, times(3)).indexMetadata(anyString());
        inOrder.verify(indexerSettingService).removeSkipCoUuids(List.of("not-published", "index-error", "not-in-geonetwork"));
        inOrder.verifyNoMoreInteractions();
    }

    /**
     * A uuid that was on the list before the request matches its indexed record already, a failed
     * reindex must not take it off. A uuid with no record in GeoNetwork is always taken off.
     */
    @Test
    void addUndoesOnlyWhatTheRequestChanged() throws Exception {
        List<String> uuids = List.of("index-error", "not-in-geonetwork");
        // Both were on the list already, the request added nothing
        when(indexerSettingService.addSkipCoUuids(uuids))
                .thenReturn(new SkipCoUpdate(uuids, UPDATED_AT, List.of()));
        when(indexerSettingService.removeSkipCoUuids(any()))
                .thenReturn(new SkipCoUpdate(List.of("index-error"), UPDATED_AT, List.of("not-in-geonetwork")));
        mockReindexResults();

        var response = controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(uuids));

        assertEquals(List.of("index-error"), response.getBody().uuids());
        assertEquals(List.of("index-error"), response.getBody().reindexFailed());
        assertEquals(List.of("not-in-geonetwork"), response.getBody().notFound());
        verify(indexerSettingService).removeSkipCoUuids(List.of("not-in-geonetwork"));
    }

    @Test
    void removeUpdatesTheListThenReindex() throws Exception {
        when(indexerSettingService.removeSkipCoUuids(List.of("uuid-1", "uuid-2")))
                .thenReturn(new SkipCoUpdate(List.of("uuid-3"), UPDATED_AT, List.of("uuid-1", "uuid-2")));

        // Spring splits ?uuids=uuid-1,uuid-2 into a list
        var response = controller.removeSkipCoUuids(List.of("uuid-1", "uuid-2"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-3"), response.getBody().uuids());
        assertEquals(List.of("uuid-1", "uuid-2"), response.getBody().reindexed());
        assertEquals(List.of(), response.getBody().reindexFailed());
        assertEquals(List.of(), response.getBody().notFound());

        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).removeSkipCoUuids(List.of("uuid-1", "uuid-2"));
        inOrder.verify(indexerMetadata, times(2)).indexMetadata(anyString());

        // Everything is reindexed, so there is nothing to undo
        verify(indexerSettingService, never()).addSkipCoUuids(any());
    }

    /**
     * A uuid that failed to reindex is put back on the list. A uuid with no record in GeoNetwork
     * stays removed, there is nothing to reindex, otherwise it could never be removed.
     */
    @Test
    void removeIsUndoneForTheUuidsThatFailedToReindex() throws Exception {
        List<String> uuids = List.of("not-in-geonetwork", "not-published", "index-error", "not-on-list", "ok");
        // "not-on-list" was not on the list, so the request did not remove it
        when(indexerSettingService.removeSkipCoUuids(uuids))
                .thenReturn(new SkipCoUpdate(List.of(), UPDATED_AT, List.of("not-in-geonetwork", "not-published", "index-error", "ok")));
        when(indexerSettingService.addSkipCoUuids(any()))
                .thenReturn(new SkipCoUpdate(List.of("not-published", "index-error"), UPDATED_AT, List.of("not-published", "index-error")));
        mockReindexResults();
        when(indexerMetadata.indexMetadata("xml-of-not-on-list"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("index error")));

        var response = controller.removeSkipCoUuids(uuids);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("not-published", "index-error"), response.getBody().uuids());
        assertEquals(List.of("ok"), response.getBody().reindexed());
        assertEquals(List.of("not-published", "index-error", "not-on-list"), response.getBody().reindexFailed());
        assertEquals(List.of("not-in-geonetwork"), response.getBody().notFound());

        // Put back only what the request removed, and the undo does not reindex again
        verify(indexerSettingService).addSkipCoUuids(List.of("not-published", "index-error"));
        verify(indexerMetadata, times(4)).indexMetadata(anyString());
    }

    /** If the undo fails the list still has the change, the admin must know: 500 and not 200. */
    @Test
    void failedUndoIsAnError() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("index-error")))
                .thenReturn(new SkipCoUpdate(List.of("index-error"), UPDATED_AT, List.of("index-error")));
        when(indexerSettingService.removeSkipCoUuids(any()))
                .thenThrow(new IndexerSettingConflictException("changed by someone else"));
        mockReindexResults();

        var e = assertThrows(ResponseStatusException.class,
                () -> controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of("index-error"))));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatusCode());
        assertTrue(e.getReason().contains("index-error"), e.getReason());
    }

    @Test
    void failedListUpdateMeansNoReindex() throws Exception {
        when(indexerSettingService.addSkipCoUuids(any()))
                .thenThrow(new IndexerSettingConflictException("changed by someone else"));

        assertThrows(IndexerSettingConflictException.class,
                () -> controller.addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of("uuid-1"))));

        verifyNoInteractions(indexerMetadata, geonetworkResourceService);
    }

    /** The reindex result depends on the name of the uuid, any other uuid is reindexed. */
    private void mockReindexResults() throws Exception {
        when(geonetworkResourceService.searchRecordBy("not-in-geonetwork"))
                .thenThrow(new MetadataNotFoundException("not found"));
        // 204 is what indexMetadata() returns for a record that is not published
        when(indexerMetadata.indexMetadata("xml-of-not-published"))
                .thenReturn(CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null)));
        when(indexerMetadata.indexMetadata("xml-of-index-error"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("index error")));
    }
}
