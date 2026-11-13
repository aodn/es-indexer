package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.IndexerSettingConflictException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the indexer settings. The settings live in one index (elasticsearch.config_index.name) which
 * works as a key-value store, each setting is one document:
 *   _id        -> the setting name, e.g. "skip_co"
 *   value      -> the JSON of the setting, stored as is (not parsed, not indexed), so each setting
 *                 can have its own shape
 *   updated_at -> the time of the last write
 *
 * Rules:
 *   - Read with a get by id, it is real-time so a write is visible to the next read. Do not read a
 *     setting with a search, a search has a refresh delay.
 *   - Write with the seq_no and primary_term of the last read, so a write on top of someone else's
 *     change is rejected (409) and not lost.
 *   - Do not cache a setting in memory, another instance of the indexer would not see the update.
 *
 * The only setting for now is "skip_co": the uuids to index with the data-index key and not with
 * the cloud-optimised keys, see StacCollectionMapperService.mapAssetsData()
 */
@Slf4j
@Service
public class IndexerSettingService {

    public static final String SKIP_CO = "skip_co";

    protected static final String FIELD_UPDATED_AT = "updated_at";
    protected static final String FIELD_VALUE = "value";
    protected static final String FIELD_UUIDS = "uuids";

    /**
     * The skip_co setting
     * @param uuids - The full list of uuid, empty if the setting is not created yet
     * @param updatedAt - The time of the last write, null if the setting is not created yet
     */
    public record SkipCoSetting(List<String> uuids, String updatedAt) {}

    /**
     * One setting document. seqNo and primaryTerm come from the read and are null when the document
     * does not exist, the next write needs them to detect a change in between.
     */
    protected record StoredSetting(JsonNode value, String updatedAt, Long seqNo, Long primaryTerm) {
        static StoredSetting missing() {
            return new StoredSetting(null, null, null, null);
        }
    }

    protected final String configIndexName;
    protected final ElasticsearchClient portalElasticsearchClient;
    protected final ElasticSearchIndexService elasticSearchIndexService;

    @Autowired
    public IndexerSettingService(
            @Value("${elasticsearch.config_index.name}") String configIndexName,
            @Qualifier("portalElasticsearchClient") ElasticsearchClient portalElasticsearchClient,
            ElasticSearchIndexService elasticSearchIndexService) {
        this.configIndexName = configIndexName;
        this.portalElasticsearchClient = portalElasticsearchClient;
        this.elasticSearchIndexService = elasticSearchIndexService;
    }

    @PostConstruct
    public void init() {
        try {
            createIndexIfMissing();
        }
        catch (Exception e) {
            // Do not block the startup. A missing index reads as empty settings and the next write
            // tries to create it again.
            log.error("Failed to create index {} at startup, will retry on next write: {}", configIndexName, e.getMessage());
        }
    }
    /**
     * Create the settings index if it does not exist. It never deletes an existing index, the
     * settings must survive a restart.
     */
    public void createIndexIfMissing() {
        elasticSearchIndexService.createIndexFromMappingJSONFileIfMissing(
                AppConstants.INDEXER_CONFIG_MAPPING_JSON_FILE,
                configIndexName,
                null
        );
    }
    /**
     * @return - The skip_co setting, with an empty list if the setting is not created yet
     * @throws IOException - If Elasticsearch cannot be reached
     */
    public SkipCoSetting getSkipCoSetting() throws IOException {
        StoredSetting stored = readSetting(SKIP_CO);
        return new SkipCoSetting(List.copyOf(uuidsOf(stored)), stored.updatedAt());
    }
    /**
     * Used by the indexing, it reads the list on every call so that an update is seen at once.
     *
     * @param uuid - The uuid of the record
     * @return - True if the uuid is on the skip_co list
     */
    public boolean isSkipCo(String uuid) {
        try {
            return uuid != null && getSkipCoSetting().uuids().contains(uuid);
        }
        catch (IOException e) {
            // Do not fall back to "not on the list", the record would be indexed with the wrong keys
            throw new UncheckedIOException("Failed to read setting " + SKIP_CO + " for uuid " + uuid, e);
        }
    }
    /**
     * Add uuids to the skip_co list. It is safe to repeat: a uuid on the list already is kept as is.
     *
     * @param uuids - The uuids to add
     * @return - The setting after the update
     * @throws IOException - If Elasticsearch cannot be reached
     * @throws IndexerSettingConflictException - If someone else changed the list during the update
     */
    public SkipCoSetting addSkipCoUuids(Collection<String> uuids) throws IOException {
        return updateSkipCoUuids(uuids, true);
    }
    /**
     * Remove uuids from the skip_co list. It is safe to repeat: a uuid not on the list is ignored.
     *
     * @param uuids - The uuids to remove
     * @return - The setting after the update
     * @throws IOException - If Elasticsearch cannot be reached
     * @throws IndexerSettingConflictException - If someone else changed the list during the update
     */
    public SkipCoSetting removeSkipCoUuids(Collection<String> uuids) throws IOException {
        return updateSkipCoUuids(uuids, false);
    }

    protected SkipCoSetting updateSkipCoUuids(Collection<String> uuids, boolean add) throws IOException {
        StoredSetting stored = readSetting(SKIP_CO);

        // Keep the order of the list, new uuids go to the end
        Set<String> current = new LinkedHashSet<>(uuidsOf(stored));
        boolean changed = add ? current.addAll(uuids) : current.removeAll(uuids);

        if (!changed) {
            log.info("Setting {} not changed by {} of {}", SKIP_CO, add ? "add" : "remove", uuids);
            return new SkipCoSetting(List.copyOf(current), stored.updatedAt());
        }

        SkipCoSetting updated = writeSkipCoUuids(new ArrayList<>(current), stored.seqNo(), stored.primaryTerm());
        log.info("Setting {} updated by {} of {}, it has {} uuids now", SKIP_CO, add ? "add" : "remove", uuids, updated.uuids().size());
        return updated;
    }
    /**
     * Write the whole list once. Package-private so the test can write with an old seq_no.
     */
    SkipCoSetting writeSkipCoUuids(List<String> uuids, Long seqNo, Long primaryTerm) throws IOException {
        String updatedAt = writeSetting(SKIP_CO, Map.of(FIELD_UUIDS, uuids), seqNo, primaryTerm);
        return new SkipCoSetting(List.copyOf(uuids), updatedAt);
    }

    protected static List<String> uuidsOf(StoredSetting stored) {
        List<String> uuids = new ArrayList<>();
        JsonNode node = stored.value() != null ? stored.value().get(FIELD_UUIDS) : null;
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                if (n.isTextual()) {
                    uuids.add(n.asText());
                }
            });
        }
        return uuids;
    }
    /**
     * Read one setting with a get by id (real-time).
     *
     * @param name - The setting name, it is the document id
     * @return - The setting, or StoredSetting.missing() if the document or the index does not exist
     * @throws IOException - If Elasticsearch cannot be reached
     */
    protected StoredSetting readSetting(String name) throws IOException {
        try {
            GetResponse<ObjectNode> response = portalElasticsearchClient.get(g -> g
                            .index(configIndexName)
                            .id(name),
                    ObjectNode.class
            );
            ObjectNode source = response.source();
            if (!response.found() || source == null) {
                return StoredSetting.missing();
            }
            return new StoredSetting(
                    source.get(FIELD_VALUE),
                    source.path(FIELD_UPDATED_AT).asText(null),
                    response.seqNo(),
                    response.primaryTerm()
            );
        }
        catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                // The index is not created yet, same as an empty setting
                return StoredSetting.missing();
            }
            throw e;
        }
    }
    /**
     * Write one setting. The write is rejected if the document changed since the read that gave
     * seqNo and primaryTerm, or if the document was created in between when they are null.
     *
     * @param name - The setting name, it is the document id
     * @param value - The JSON value of the setting
     * @param seqNo - The seq_no of the last read, null if the document did not exist
     * @param primaryTerm - The primary_term of the last read, null if the document did not exist
     * @return - The updated_at written
     * @throws IOException - If Elasticsearch cannot be reached
     * @throws IndexerSettingConflictException - If someone else changed the setting in between
     */
    protected String writeSetting(String name, Object value, Long seqNo, Long primaryTerm) throws IOException {
        // Without this the write auto-creates the index with a dynamic mapping, which parses the value
        createIndexIfMissing();

        String updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Map<String, Object> document = Map.of(
                FIELD_UPDATED_AT, updatedAt,
                FIELD_VALUE, value
        );

        try {
            portalElasticsearchClient.index(i -> {
                i.index(configIndexName).id(name).document(document);
                if (seqNo != null && primaryTerm != null) {
                    i.ifSeqNo(seqNo).ifPrimaryTerm(primaryTerm);
                }
                else {
                    // No document on read, so it must still not exist on write
                    i.opType(OpType.Create);
                }
                return i;
            });
        }
        catch (ResponseException e) {
            // The elastic client does not turn a 409 into an ElasticsearchException, the low level
            // exception comes out as is
            if (e.getResponse().getStatusLine().getStatusCode() == HttpStatus.CONFLICT.value()) {
                throw conflict(name);
            }
            throw e;
        }
        catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.CONFLICT.value()) {
                throw conflict(name);
            }
            throw e;
        }
        return updatedAt;
    }

    protected static IndexerSettingConflictException conflict(String name) {
        return new IndexerSettingConflictException(
                "Setting " + name + " was changed by someone else during the update, nothing is changed, please retry");
    }
}
