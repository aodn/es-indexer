package au.org.aodn.esindexer.service;

import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.esindexer.Application;
import au.org.aodn.esindexer.BaseTestClass;
import au.org.aodn.esindexer.configuration.GeoNetworkSearchTestConfig;
import au.org.aodn.esindexer.controller.IndexerSettingController;
import au.org.aodn.esindexer.controller.IndexerSettingController.UuidResult;
import au.org.aodn.esindexer.exception.IndexerSettingConflictException;
import au.org.aodn.esindexer.model.MockServer;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The skip_co uuid list end-to-end, against the Elasticsearch and GeoNetwork test containers:
 * the settings index, the list updates, the endpoints, and the assets of a record indexed with
 * and without its uuid on the list.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IndexerSettingServiceIT extends BaseTestClass {

    private static final String CO_KEY = "vessel_xbt_realtime_nonqc.parquet";
    private static final String DATA_INDEX_KEY = "original_netcdfs";

    // Same bean overrides, with the same field names, as IndexerServiceIT and AcronymServiceIT so
    // that Spring reuses the cached context (and its docker stack) instead of starting another one.
    @MockitoSpyBean
    protected IndexerMetadataServiceImpl indexerService;

    @MockitoBean
    protected DataDiscoveryAiService dataDiscoveryAiService;

    @Autowired
    protected IndexerSettingService indexerSettingService;

    @Autowired
    protected IndexerSettingController indexerSettingController;

    @Autowired
    protected MockServer mockServer;

    @Value("${elasticsearch.index.name}")
    protected String INDEX_NAME;

    @Value("${elasticsearch.config_index.name}")
    protected String CONFIG_INDEX_NAME;

    @Value("${app.http.auth-token-header-name}")
    protected String apiKeyHeader;

    @Value("${app.http.authToken}")
    protected String apiKey;

    @LocalServerPort
    protected int serverPort;

    @BeforeAll
    public void setup() {
        geoNetworkService.setServer(String.format("http://%s:%s",
                dockerComposeContainer.getServiceHost(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT),
                dockerComposeContainer.getServicePort(GeoNetworkSearchTestConfig.GN_NAME, GeoNetworkSearchTestConfig.GN_PORT))
        );
    }

    /** Each test starts with no skip_co document, which is the state of a new environment. */
    @BeforeEach
    public void deleteSetting() throws IOException {
        try {
            client.delete(d -> d.index(CONFIG_INDEX_NAME).id(IndexerSettingService.SKIP_CO));
        }
        catch (ElasticsearchException e) {
            // It is ok if the index is not there
        }
    }

    @AfterAll
    public void cleanUp() throws IOException {
        deleteSetting();
        mockServer.resetToDefault();
    }

    // ---- settings index ----

    /** The index is created when missing, with a mapping that stores the value without parsing it. */
    @Test
    public void indexIsCreatedWhenMissing() throws IOException {
        client.indices().delete(d -> d.index(CONFIG_INDEX_NAME).ignoreUnavailable(true));

        // A missing index reads as an empty list, same as a missing document
        Assertions.assertEquals(List.of(), indexerSettingService.getSkipCoSetting().uuids());
        Assertions.assertFalse(indexerSettingService.isSkipCo("uuid-1"));

        indexerSettingService.createIndexIfMissing();
        assertSettingsMapping();

        // Calling it again must not delete the index, the settings must survive a restart
        indexerSettingService.addSkipCoUuids(List.of("uuid-1"));
        indexerSettingService.init();
        Assertions.assertEquals(List.of("uuid-1"), indexerSettingService.getSkipCoSetting().uuids());
    }

    /** A write creates the index itself when missing, so it never gets a dynamic mapping. */
    @Test
    public void writeCreatesTheIndexWhenMissing() throws IOException {
        client.indices().delete(d -> d.index(CONFIG_INDEX_NAME).ignoreUnavailable(true));

        indexerSettingService.addSkipCoUuids(List.of("uuid-1"));

        assertSettingsMapping();
        Assertions.assertEquals(List.of("uuid-1"), indexerSettingService.getSkipCoSetting().uuids());
    }

    // ---- list updates ----

    @Test
    public void missingDocumentReadsAsEmptyList() throws IOException {
        var setting = indexerSettingService.getSkipCoSetting();

        Assertions.assertEquals(List.of(), setting.uuids());
        Assertions.assertNull(setting.updatedAt());
        Assertions.assertFalse(indexerSettingService.isSkipCo("uuid-1"));
    }

    /** Add and remove work for one uuid and for several, a read right after a write returns the new list. */
    @Test
    public void addAndRemoveUuids() throws IOException {
        var added = indexerSettingService.addSkipCoUuids(List.of("uuid-1"));
        Assertions.assertEquals(List.of("uuid-1"), added.uuids());
        Assertions.assertDoesNotThrow(() -> Instant.parse(added.updatedAt()),
                "updated_at should be an ISO instant; got " + added.updatedAt());
        // No refresh in between, the get by id is real-time
        Assertions.assertEquals(List.of("uuid-1"), indexerSettingService.getSkipCoSetting().uuids());
        Assertions.assertEquals(added.updatedAt(), indexerSettingService.getSkipCoSetting().updatedAt());
        Assertions.assertTrue(indexerSettingService.isSkipCo("uuid-1"));

        Assertions.assertEquals(List.of("uuid-1", "uuid-2", "uuid-3"),
                indexerSettingService.addSkipCoUuids(List.of("uuid-2", "uuid-3")).uuids());
        Assertions.assertEquals(List.of("uuid-1", "uuid-2", "uuid-3"), indexerSettingService.getSkipCoSetting().uuids());

        Assertions.assertEquals(List.of("uuid-1", "uuid-3"),
                indexerSettingService.removeSkipCoUuids(List.of("uuid-2")).uuids());
        Assertions.assertFalse(indexerSettingService.isSkipCo("uuid-2"));

        Assertions.assertEquals(List.of(),
                indexerSettingService.removeSkipCoUuids(List.of("uuid-1", "uuid-3")).uuids());
        Assertions.assertEquals(List.of(), indexerSettingService.getSkipCoSetting().uuids());
    }

    /** Repeating the call is how an admin fixes a failed reindex, so it must not fail. */
    @Test
    public void repeatingAddOrRemoveDoesNotFail() throws IOException {
        // Remove before the document exists
        Assertions.assertEquals(List.of(), indexerSettingService.removeSkipCoUuids(List.of("uuid-1")).uuids());

        indexerSettingService.addSkipCoUuids(List.of("uuid-1", "uuid-2"));
        Assertions.assertEquals(List.of("uuid-1", "uuid-2"),
                indexerSettingService.addSkipCoUuids(List.of("uuid-1")).uuids());
        Assertions.assertEquals(List.of("uuid-1", "uuid-2"),
                indexerSettingService.addSkipCoUuids(List.of("uuid-2", "uuid-1")).uuids());

        indexerSettingService.removeSkipCoUuids(List.of("uuid-1"));
        Assertions.assertEquals(List.of("uuid-2"),
                indexerSettingService.removeSkipCoUuids(List.of("uuid-1")).uuids());
        Assertions.assertEquals(List.of("uuid-2"), indexerSettingService.getSkipCoSetting().uuids());
    }

    /** A write on top of someone else's change is rejected and changes nothing. */
    @Test
    public void writeWithOldSeqNoGetsConflict() throws IOException {
        indexerSettingService.addSkipCoUuids(List.of("uuid-1"));
        var read = client.get(g -> g.index(CONFIG_INDEX_NAME).id(IndexerSettingService.SKIP_CO), ObjectNode.class);

        // Someone else updates the list after our read
        indexerSettingService.addSkipCoUuids(List.of("uuid-2"));

        Assertions.assertThrows(IndexerSettingConflictException.class,
                () -> indexerSettingService.writeSkipCoUuids(List.of("uuid-3"), read.seqNo(), read.primaryTerm()));
        // Same when our read saw no document but someone else created it in between
        Assertions.assertThrows(IndexerSettingConflictException.class,
                () -> indexerSettingService.writeSkipCoUuids(List.of("uuid-3"), null, null));

        Assertions.assertEquals(List.of("uuid-1", "uuid-2"), indexerSettingService.getSkipCoSetting().uuids());
    }

    // ---- endpoints ----

    @Test
    public void callWithoutApiKeyIsRejected() {
        ResponseEntity<String> noKey = testRestTemplate.exchange(
                url("/skip_co"), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, noKey.getStatusCode());

        ResponseEntity<JsonNode> withKey = testRestTemplate.exchange(
                url("/skip_co"), HttpMethod.GET, new HttpEntity<>(apiKeyHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, withKey.getStatusCode());
        Assertions.assertEquals(0, withKey.getBody().get("uuids").size());
        Assertions.assertFalse(withKey.getBody().has("results"), "GET has no reindex result");
    }

    /**
     * Over HTTP: the add and the delete take the same JSON body. A uuid
     * that is not in GeoNetwork is not added, and it can be removed. The status is 200 and the error
     * is in the result of the uuid.
     */
    @Test
    public void addAndDeleteOverHttp() throws IOException {
        // "on-list" is a uuid with no record that got on the list before, the add must not lose it
        indexerSettingService.addSkipCoUuids(List.of("on-list"));

        ResponseEntity<JsonNode> added = testRestTemplate.exchange(
                url("/skip_co/uuids"), HttpMethod.POST,
                new HttpEntity<>("{ \"uuids\": [\"not-exist-1\", \" on-list \", \"not-exist-1\"] }", apiKeyHeaders()),
                JsonNode.class);

        Assertions.assertEquals(HttpStatus.OK, added.getStatusCode());
        Assertions.assertEquals(List.of("on-list"), texts(added.getBody().get("uuids")), "The add is undone");
        Assertions.assertDoesNotThrow(() -> Instant.parse(added.getBody().get("updated_at").asText()));
        Assertions.assertEquals(List.of("on-list"), indexerSettingService.getSkipCoSetting().uuids());

        // One result per uuid, in the order of the request, with the reason of the failure
        JsonNode addResults = added.getBody().get("results");
        Assertions.assertEquals(2, addResults.size());
        assertResult(addResults.get(0), "not-exist-1", false, false);
        // It was on the list before the request, so it stays as it was
        assertResult(addResults.get(1), "on-list", false, true);
        Assertions.assertTrue(addResults.get(0).get("error").asText().contains("not-exist-1"),
                "The error says which record is not found; got " + addResults.get(0).get("error"));

        // No record depends on these uuids, so they are removed even if the reindex failed
        ResponseEntity<JsonNode> deleted = testRestTemplate.exchange(
                url("/skip_co/uuids"), HttpMethod.DELETE,
                new HttpEntity<>("{ \"uuids\": [\"on-list\", \" not-exist-1 \"] }", apiKeyHeaders()),
                JsonNode.class);

        Assertions.assertEquals(HttpStatus.OK, deleted.getStatusCode());
        Assertions.assertEquals(List.of(), texts(deleted.getBody().get("uuids")));
        assertResult(deleted.getBody().get("results").get(0), "on-list", false, false);
        assertResult(deleted.getBody().get("results").get(1), "not-exist-1", false, false);
        Assertions.assertEquals(List.of(), indexerSettingService.getSkipCoSetting().uuids());

        ResponseEntity<String> empty = testRestTemplate.exchange(
                url("/skip_co/uuids"), HttpMethod.POST,
                new HttpEntity<>("{ \"uuids\": [] }", apiKeyHeaders()),
                String.class);
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, empty.getStatusCode());
    }

    // ---- indexing ----

    /**
     * The data access service returns the cloud-optimised key and the data-index key of the record.
     * Not on the list: the cloud-optimised asset only. On the list: the data-index asset only, for
     * the single record reindex of the endpoint and for the full reindex. After a remove the
     * cloud-optimised asset is back. An add or a remove with a failed reindex changes nothing, the
     * list and the record still agree.
     */
    @Test
    public void recordOnTheListIsIndexedWithDataIndexKeyOnly() throws Exception {
        String uuid = "9e5c3031-a026-48b3-a153-a70c2e2b78b9";
        try {
            insertMetadataRecords(uuid, "classpath:canned/sample1.xml");
            mockDataAccessServiceKeys(uuid);

            // Remove with a failed reindex and the record not in the index yet: no record depends
            // on the list, so the uuid is still removed
            indexerSettingService.addSkipCoUuids(List.of(uuid));
            var removedWithNoRecord = withFailedReindex(() -> indexerSettingController.removeSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of(uuid)))).getBody();
            Assertions.assertEquals(List.of(), removedWithNoRecord.uuids());
            Assertions.assertEquals(List.of(new UuidResult(uuid, false, false, "index error")), removedWithNoRecord.results());

            // Full reindex, uuid not on the list
            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Assertions.assertEquals(List.of(CO_KEY), assetKeys(uuid));

            // Add: the list is updated, then the record is reindexed with the new list
            var added = indexerSettingController
                    .addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of(uuid)))
                    .getBody();
            Assertions.assertEquals(List.of(uuid), added.uuids());
            Assertions.assertEquals(List.of(new UuidResult(uuid, true, true, null)), added.results());
            Assertions.assertEquals(List.of(DATA_INDEX_KEY), assetKeys(uuid));

            // Full reindex, uuid on the list
            indexerService.indexAllMetadataRecordsFromGeoNetwork(null, true, null);
            Assertions.assertEquals(List.of(DATA_INDEX_KEY), assetKeys(uuid));
            Assertions.assertEquals(List.of(uuid), indexerSettingService.getSkipCoSetting().uuids(),
                    "A full reindex does not touch the settings");

            // Remove with a failed reindex and the record in the index: the uuid is put back, the
            // list and the record still agree
            var notRemoved = withFailedReindex(() -> indexerSettingController.removeSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of(uuid)))).getBody();
            Assertions.assertEquals(List.of(uuid), notRemoved.uuids());
            Assertions.assertEquals(List.of(new UuidResult(uuid, false, true, "index error")), notRemoved.results());
            Assertions.assertEquals(List.of(uuid), indexerSettingService.getSkipCoSetting().uuids());
            Assertions.assertEquals(List.of(DATA_INDEX_KEY), assetKeys(uuid));

            // Remove: the cloud-optimised asset is back
            var removed = indexerSettingController.removeSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of(uuid))).getBody();
            Assertions.assertEquals(List.of(), removed.uuids());
            Assertions.assertEquals(List.of(new UuidResult(uuid, true, false, null)), removed.results());
            Assertions.assertEquals(List.of(CO_KEY), assetKeys(uuid));

            // Add with a failed reindex: the uuid is not added
            var notAdded = withFailedReindex(() -> indexerSettingController
                    .addSkipCoUuids(new IndexerSettingController.UuidsRequest(List.of(uuid))))
                    .getBody();
            Assertions.assertEquals(List.of(), notAdded.uuids());
            Assertions.assertEquals(List.of(new UuidResult(uuid, false, false, "index error")), notAdded.results());
            Assertions.assertEquals(List.of(), indexerSettingService.getSkipCoSetting().uuids());
            Assertions.assertEquals(List.of(CO_KEY), assetKeys(uuid));
        }
        finally {
            mockServer.resetToDefault();
            // The record was just reindexed with no refresh, a delete by query on a stale search
            // gets a version conflict
            client.indices().refresh();
            clearElasticIndex(INDEX_NAME);
            deleteRecord(uuid);
        }
    }

    // ---- helpers ----

    private String url(String path) {
        return "http://localhost:" + serverPort + "/api/v1/indexer/config" + path;
    }

    private HttpHeaders apiKeyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(apiKeyHeader, apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static void assertResult(JsonNode result, String uuid, boolean reindexed, boolean onList) {
        Assertions.assertEquals(uuid, result.get("uuid").asText());
        Assertions.assertEquals(reindexed, result.get("reindexed").asBoolean(), "reindexed of " + uuid);
        Assertions.assertEquals(onList, result.get("on_list").asBoolean(), "on_list of " + uuid);
        Assertions.assertEquals(!reindexed, result.has("error"), "error of " + uuid);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText()));
        return values;
    }

    /** The mapping from the schema file: nothing dynamic, and the value is stored but not parsed. */
    private void assertSettingsMapping() throws IOException {
        TypeMapping mapping = client.indices()
                .getMapping(g -> g.index(CONFIG_INDEX_NAME))
                .get(CONFIG_INDEX_NAME)
                .mappings();

        Assertions.assertEquals(DynamicMapping.False, mapping.dynamic());
        Assertions.assertEquals(Boolean.FALSE, mapping.properties().get("value").object().enabled());
        Assertions.assertTrue(mapping.properties().get("updated_at").isDate());
    }

    /**
     * Run the call while the indexing of the controller fails for every record. The controller gets
     * a failing indexer for the call only, the real one is put back after it.
     */
    private <T> T withFailedReindex(Callable<T> call) throws Exception {
        IndexerMetadataService real = (IndexerMetadataService) ReflectionTestUtils.getField(indexerSettingController, "indexerMetadata");
        IndexerMetadataService failing = Mockito.mock(IndexerMetadataService.class);
        Mockito.when(failing.indexMetadata(Mockito.anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("index error")));
        // Only the indexing fails, the check of the record index is the real one
        Mockito.when(failing.isDocumentIndexed(Mockito.anyString()))
                .thenAnswer(ans -> real.isDocumentIndexed(ans.getArgument(0)));

        ReflectionTestUtils.setField(indexerSettingController, "indexerMetadata", failing);
        try {
            return call.call();
        }
        finally {
            ReflectionTestUtils.setField(indexerSettingController, "indexerMetadata", real);
        }
    }

    /** DAS returns both kinds of key for the uuid, every other DAS call keeps the default 404. */
    private void mockDataAccessServiceKeys(String uuid) {
        String json = """
                {
                  "%1$s": {"uuid": "%3$s", "dname": "%1$s", "type": "cloud_optimised"},
                  "%2$s": {"uuid": "%3$s", "dname": "%2$s", "depth": null, "type": "data_index"}
                }
                """.formatted(CO_KEY, DATA_INDEX_KEY, uuid);

        mockServer.getServer().reset();
        // Registered before the catch-all 404, the first expectation that matches is used
        mockServer.getServer()
                .expect(manyTimes(), requestTo(endsWith("/metadata/" + uuid)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        mockServer.expectDefaultNotFound();
    }

    /** The asset keys of the record in the portal index, read by id so no refresh is needed. */
    private List<String> assetKeys(String uuid) throws IOException {
        var response = client.get(g -> g.index(INDEX_NAME).id(uuid), ObjectNode.class);
        Assertions.assertTrue(response.found(), "record " + uuid + " should be in the portal index");

        List<String> keys = new ArrayList<>();
        JsonNode assets = response.source().get("assets");
        if (assets != null) {
            assets.fieldNames().forEachRemaining(keys::add);
        }
        return keys;
    }
}
