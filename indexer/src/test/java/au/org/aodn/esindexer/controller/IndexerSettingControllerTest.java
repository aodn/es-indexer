package au.org.aodn.esindexer.controller;

import au.org.aodn.esindexer.controller.IndexerSettingController.UuidResult;
import au.org.aodn.esindexer.controller.IndexerSettingController.UuidsRequest;
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

import java.io.IOException;
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

    private static final String NOT_FOUND_ERROR = "Unable to find metadata record in GeoNetwork";
    private static final String NOT_PUBLISHED_ERROR = "Record is not published in GeoNetwork, it is not indexed";
    private static final String INDEX_ERROR = "index error";

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
    void getReturnsTheFullListWithoutResults() throws Exception {
        when(indexerSettingService.getSkipCoSetting())
                .thenReturn(new SkipCoSetting(List.of("uuid-1", "uuid-2"), UPDATED_AT));

        var response = controller.getSkipCoUuids();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-1", "uuid-2"), response.getBody().uuids());
        assertEquals(UPDATED_AT, response.getBody().updatedAt());

        // The GET response has no results field
        JsonNode json = new ObjectMapper().valueToTree(response.getBody());
        assertEquals(UPDATED_AT, json.get("updated_at").asText());
        assertFalse(json.has("results"));
    }

    @Test
    void addUpdatesTheListFirstThenReindexAndReturnsTheFullList() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("uuid-3")))
                .thenReturn(new SkipCoUpdate(List.of("uuid-1", "uuid-2", "uuid-3"), UPDATED_AT, List.of("uuid-3")));

        var response = controller.addSkipCoUuids(new UuidsRequest(List.of("uuid-3")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-1", "uuid-2", "uuid-3"), response.getBody().uuids());
        assertEquals(List.of(new UuidResult("uuid-3", true, true, null)), response.getBody().results());

        // The reindex reads the list, so the list must be written before it
        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).addSkipCoUuids(List.of("uuid-3"));
        inOrder.verify(indexerMetadata).indexMetadata("xml-of-uuid-3");

        // Everything is reindexed, so there is nothing to undo
        verify(indexerSettingService, never()).removeSkipCoUuids(any());

        // A reindexed uuid has no error field
        JsonNode result = new ObjectMapper().valueToTree(response.getBody()).get("results").get(0);
        assertEquals("uuid-3", result.get("uuid").asText());
        assertTrue(result.get("reindexed").asBoolean());
        assertTrue(result.get("on_list").asBoolean());
        assertFalse(result.has("error"));
    }

    @Test
    void addCleansTheInput() throws Exception {
        when(indexerSettingService.addSkipCoUuids(List.of("uuid-1", "uuid-2")))
                .thenReturn(new SkipCoUpdate(List.of("uuid-1", "uuid-2"), UPDATED_AT, List.of("uuid-1", "uuid-2")));

        var response = controller.addSkipCoUuids(new UuidsRequest(
                Arrays.asList(" uuid-1 ", "uuid-2", "", null, "uuid-1")));

        // Trimmed, blank and duplicate removed, each uuid reindexed once
        verify(indexerSettingService).addSkipCoUuids(List.of("uuid-1", "uuid-2"));
        assertEquals(
                List.of(new UuidResult("uuid-1", true, true, null), new UuidResult("uuid-2", true, true, null)),
                response.getBody().results());
        verify(indexerMetadata, times(2)).indexMetadata(anyString());
    }

    @Test
    void emptyInputIsBadRequestAndNothingChanges() throws Exception {
        for (var request : Arrays.asList(
                null,
                new UuidsRequest(null),
                new UuidsRequest(List.of()),
                new UuidsRequest(List.of(" ")))) {

            var e = assertThrows(ResponseStatusException.class, () -> controller.addSkipCoUuids(request));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());

            var e2 = assertThrows(ResponseStatusException.class, () -> controller.removeSkipCoUuids(request));
            assertEquals(HttpStatus.BAD_REQUEST, e2.getStatusCode());
        }

        verifyNoInteractions(indexerSettingService, indexerMetadata, geonetworkResourceService);
    }

    /**
     * Add: a uuid that failed to reindex, for any reason, is taken off the list again with no other
     * reindex. It does not matter if its record is in the record index or not.
     */
    @Test
    void addIsUndoneForTheUuidsThatFailedToReindex() throws Exception {
        List<String> uuids = List.of("not-in-geonetwork", "not-published", "index-error", "ok");
        when(indexerSettingService.addSkipCoUuids(uuids))
                .thenReturn(new SkipCoUpdate(uuids, UPDATED_AT, uuids));
        when(indexerSettingService.removeSkipCoUuids(any()))
                .thenReturn(new SkipCoUpdate(List.of("ok"), UPDATED_AT, List.of()));
        mockReindexResults();
        when(indexerMetadata.isDocumentIndexed(anyString())).thenReturn(true);

        var response = controller.addSkipCoUuids(new UuidsRequest(uuids));

        // Still 200, a failed uuid does not stop the others, and each failed uuid has its error
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("ok"), response.getBody().uuids());
        assertEquals(
                List.of(
                        new UuidResult("not-in-geonetwork", false, false, NOT_FOUND_ERROR),
                        new UuidResult("not-published", false, false, NOT_PUBLISHED_ERROR),
                        new UuidResult("index-error", false, false, INDEX_ERROR),
                        new UuidResult("ok", true, true, null)),
                response.getBody().results());

        // The undo comes after the reindex, and it only writes the list
        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).addSkipCoUuids(uuids);
        inOrder.verify(indexerMetadata, times(3)).indexMetadata(anyString());
        inOrder.verify(indexerSettingService).removeSkipCoUuids(List.of("not-in-geonetwork", "not-published", "index-error"));
        inOrder.verifyNoMoreInteractions();
    }

    /**
     * A uuid that was on the list before the request matches its record already, a failed reindex
     * must not take it off: the list goes back to what it was before the request.
     */
    @Test
    void addUndoesOnlyWhatTheRequestChanged() throws Exception {
        List<String> uuids = List.of("index-error", "not-in-geonetwork");
        // Both were on the list already, the request added nothing
        when(indexerSettingService.addSkipCoUuids(uuids))
                .thenReturn(new SkipCoUpdate(uuids, UPDATED_AT, List.of()));
        mockReindexResults();

        var response = controller.addSkipCoUuids(new UuidsRequest(uuids));

        assertEquals(uuids, response.getBody().uuids());
        assertEquals(
                List.of(
                        new UuidResult("index-error", false, true, INDEX_ERROR),
                        new UuidResult("not-in-geonetwork", false, true, NOT_FOUND_ERROR)),
                response.getBody().results());
        verify(indexerSettingService, never()).removeSkipCoUuids(any());
    }

    @Test
    void removeUpdatesTheListThenReindex() throws Exception {
        when(indexerSettingService.removeSkipCoUuids(List.of("uuid-1", "uuid-2")))
                .thenReturn(new SkipCoUpdate(List.of("uuid-3"), UPDATED_AT, List.of("uuid-1", "uuid-2")));

        var response = controller.removeSkipCoUuids(new UuidsRequest(List.of("uuid-1", "uuid-2")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("uuid-3"), response.getBody().uuids());
        assertEquals(
                List.of(new UuidResult("uuid-1", true, false, null), new UuidResult("uuid-2", true, false, null)),
                response.getBody().results());

        InOrder inOrder = inOrder(indexerSettingService, indexerMetadata);
        inOrder.verify(indexerSettingService).removeSkipCoUuids(List.of("uuid-1", "uuid-2"));
        inOrder.verify(indexerMetadata, times(2)).indexMetadata(anyString());

        // Everything is reindexed, so there is nothing to undo and nothing to check
        verify(indexerSettingService, never()).addSkipCoUuids(any());
        verify(indexerMetadata, never()).isDocumentIndexed(anyString());
    }

    /**
     * Remove with a failed reindex: the uuid is put back only if its record is in the record index
     * and GeoNetwork still has it. With no record that depends on the list it is still removed.
     */
    @Test
    void removeIsUndoneOnlyForTheFailedUuidsWithARecordInTheIndex() throws Exception {
        List<String> uuids = List.of(
                "not-in-geonetwork", "not-published", "index-error", "index-error-not-indexed", "not-on-list", "ok");
        // "not-on-list" was not on the list, so the request did not remove it
        when(indexerSettingService.removeSkipCoUuids(uuids))
                .thenReturn(new SkipCoUpdate(List.of(), UPDATED_AT,
                        List.of("not-in-geonetwork", "not-published", "index-error", "index-error-not-indexed", "ok")));
        when(indexerSettingService.addSkipCoUuids(any()))
                .thenReturn(new SkipCoUpdate(List.of("not-published", "index-error"), UPDATED_AT, List.of("not-published", "index-error")));
        mockReindexResults();
        for (String failed : List.of("index-error-not-indexed", "not-on-list")) {
            when(indexerMetadata.indexMetadata("xml-of-" + failed))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException(INDEX_ERROR)));
        }
        // Every record is in the record index, but one
        when(indexerMetadata.isDocumentIndexed(anyString())).thenReturn(true);
        when(indexerMetadata.isDocumentIndexed("index-error-not-indexed")).thenReturn(false);

        var response = controller.removeSkipCoUuids(new UuidsRequest(uuids));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("not-published", "index-error"), response.getBody().uuids());
        assertEquals(
                List.of(
                        // An old record in the index does not keep the uuid, GeoNetwork lost the record
                        new UuidResult("not-in-geonetwork", false, false, NOT_FOUND_ERROR),
                        new UuidResult("not-published", false, true, NOT_PUBLISHED_ERROR),
                        new UuidResult("index-error", false, true, INDEX_ERROR),
                        new UuidResult("index-error-not-indexed", false, false, INDEX_ERROR),
                        new UuidResult("not-on-list", false, false, INDEX_ERROR),
                        new UuidResult("ok", true, false, null)),
                response.getBody().results());

        // Put back only what the request removed, and the undo does not reindex again
        verify(indexerSettingService).addSkipCoUuids(List.of("not-published", "index-error"));
        verify(indexerMetadata, times(5)).indexMetadata(anyString());
        verify(indexerMetadata, never()).isDocumentIndexed("not-in-geonetwork");
    }

    /** Not sure if the record is in the index: keep the uuid on the list, the admin repeats the call. */
    @Test
    void removeIsUndoneWhenTheRecordIndexCannotBeChecked() throws Exception {
        when(indexerSettingService.removeSkipCoUuids(List.of("index-error")))
                .thenReturn(new SkipCoUpdate(List.of(), UPDATED_AT, List.of("index-error")));
        when(indexerSettingService.addSkipCoUuids(any()))
                .thenReturn(new SkipCoUpdate(List.of("index-error"), UPDATED_AT, List.of("index-error")));
        mockReindexResults();
        when(indexerMetadata.isDocumentIndexed("index-error")).thenThrow(new IOException("elasticsearch is down"));

        var response = controller.removeSkipCoUuids(new UuidsRequest(List.of("index-error")));

        assertEquals(List.of(new UuidResult("index-error", false, true, INDEX_ERROR)), response.getBody().results());
        verify(indexerSettingService).addSkipCoUuids(List.of("index-error"));
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
                () -> controller.addSkipCoUuids(new UuidsRequest(List.of("index-error"))));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatusCode());
        assertTrue(e.getReason().contains("index-error"), e.getReason());
    }

    @Test
    void failedListUpdateMeansNoReindex() throws Exception {
        when(indexerSettingService.addSkipCoUuids(any()))
                .thenThrow(new IndexerSettingConflictException("changed by someone else"));

        assertThrows(IndexerSettingConflictException.class,
                () -> controller.addSkipCoUuids(new UuidsRequest(List.of("uuid-1"))));

        verifyNoInteractions(indexerMetadata, geonetworkResourceService);
    }

    /** The reindex result depends on the name of the uuid, any other uuid is reindexed. */
    private void mockReindexResults() throws Exception {
        when(geonetworkResourceService.searchRecordBy("not-in-geonetwork"))
                .thenThrow(new MetadataNotFoundException(NOT_FOUND_ERROR));
        // 204 is what indexMetadata() returns for a record that is not published
        when(indexerMetadata.indexMetadata("xml-of-not-published"))
                .thenReturn(CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null)));
        when(indexerMetadata.indexMetadata("xml-of-index-error"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException(INDEX_ERROR)));
    }
}
