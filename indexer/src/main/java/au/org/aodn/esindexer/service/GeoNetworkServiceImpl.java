package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.utils.StringUtil;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.utils.UrlUtils;
import au.org.aodn.stac.model.LinkModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class GeoNetworkServiceImpl implements GeoNetworkService {

    public static final String SUGGEST_LOGOS = "suggest_logos";
    public static final String THUMB_NAILS = "thumbnails";
    public static final String URL = "url";

    @Autowired
    protected UrlUtils urlUtils;

    @Lazy
    @Autowired
    protected GeoNetworkServiceImpl self;

    @Value("${elasticsearch.query.pageSize:1500}")
    protected int ES_PAGE_SIZE;

    protected static final Logger logger = LogManager.getLogger(GeoNetworkServiceImpl.class);

    protected RestTemplate indexerRestTemplate;
    protected ElasticsearchClient gn4ElasticClient;
    protected String indexName;
    protected String server;
    protected HttpEntity<String> defaultRequestEntity = getRequestEntity(MediaType.APPLICATION_JSON, null);

    protected HttpEntity<String> getRequestEntity(String body) {
        return getRequestEntity(null, body);
    }

    protected HttpEntity<String> getRequestEntity(MediaType accept, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(accept == null ? MediaType.APPLICATION_XML : accept));
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    public final static String UUID = "uuid";
    protected final static String GEONETWORK_GROUP = "groupOwner";

    public GeoNetworkServiceImpl(
            String server,
            String indexName,
            ElasticsearchClient gn4ElasticClient,
            RestTemplate indexerRestTemplate) {

        this.gn4ElasticClient = gn4ElasticClient;
        this.indexerRestTemplate = indexerRestTemplate;

        setIndexName(indexName);
        setServer(server);
    }

    public String findGroupById(String uuid) throws IOException {
        SearchRequest request = new SearchRequest.Builder()
                .index(indexName)
                .query(q -> q.bool(b -> b.filter(f -> f.matchPhrase(p -> p.field(UUID).query(uuid)))))
                .source(s -> s
                        .filter(f -> f.includes(GEONETWORK_GROUP)))
                .size(1)
                .build();

        final SearchResponse<ObjectNode> response = gn4ElasticClient.search(request, ObjectNode.class);

        if(response.hits() != null && response.hits().hits() != null && !response.hits().hits().isEmpty()) {
            // UUID should result in only 1 record, hence get(0) is ok.
            String group = response.hits().hits().get(0).source().get(GEONETWORK_GROUP).asText();

            Map<String, Object> params = new HashMap<>();
            params.put("id", group);

            ResponseEntity<JsonNode> responseEntity = indexerRestTemplate.exchange(
                    getGeoNetworkGroupsEndpoint(),
                    HttpMethod.GET,
                    defaultRequestEntity,
                    JsonNode.class, params);

            if(responseEntity.getStatusCode().is2xxSuccessful()) {
                return Objects.requireNonNull(responseEntity.getBody()).get("name").asText();
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }

    }
    /**
     * Please check comment section of getRecordRelated to understand how the structure looks like for
     * thumbnail section
     * @param uuid - UUID of record
     * @return - The LinkModel ref the thumbnail.
     */
    @Override
    public Optional<LinkModel> getThumbnail(String uuid) {
        Optional<Map<String, ?>> optRelated = self.getRecordRelated(uuid);
        if(optRelated.isPresent()) {
            Map<String, ?> node = optRelated.get();
            if(node.containsKey(THUMB_NAILS) && node.get(THUMB_NAILS) instanceof List<?> thumbnails) {
                // Always use the first item, not sure if it is good?
                if(!thumbnails.isEmpty() && thumbnails.get(0) instanceof Map<?, ?> thumbnail) {
                    if(thumbnail.containsKey(URL) && thumbnail.get(URL) instanceof Map<?,?> link) {
                        return link.entrySet()
                                .stream()
                                .findFirst()
                                .map(i -> {
                                    LinkModel linkModel = LinkModel.builder().build();
                                    linkModel.setType("image");
                                    linkModel.setRel("thumbnail");
                                    linkModel.setHref(i.getValue().toString());
                                    return linkModel;
                                });
                    }
                }
            }
        }
        return Optional.empty();
    }
    /**
     * Return a link to the logo if exist.
     *
     * @param uuid - UUID of record
     * @return - Link if logo exist
     */
    @Override
    public Optional<LinkModel> getLogo(String uuid) {
        Optional<Map<String, Object>> optAdditionalInfo = getRecordExtraInfo(uuid);
        if(optAdditionalInfo.isPresent()) {
            // We iterate logos link and add it to STAC
            Map<String, Object> additionalInfo = optAdditionalInfo.get();
            if(additionalInfo.containsKey(SUGGEST_LOGOS)) {
                if(additionalInfo.get(SUGGEST_LOGOS) instanceof List) {
                    return ((List<?>) additionalInfo.get(SUGGEST_LOGOS))
                            .stream()
                            .map(p -> (p instanceof String) ? (String) p : null)
                            .filter(Objects::nonNull)
                            .filter(i -> urlUtils.checkUrlExists(i))
                            .findFirst()        // We only pick the first reachable one
                            .map(i -> {
                                LinkModel linkModel = LinkModel.builder().build();
                                linkModel.setHref(i);
                                // Geonetwork always return png logo
                                linkModel.setType("image/png");
                                linkModel.setRel("icon");
                                linkModel.setTitle("Suggest icon for dataset");
                                return linkModel;
                            });
                }
            }
        }
        return Optional.empty();
    }

    protected Optional<Map<String, Object>> getRecordExtraInfo(String uuid) {
        Map<String, Object> params = new HashMap<>();
        params.put(UUID, uuid);

        try {
            ResponseEntity<Map<String, Object>> responseEntity = indexerRestTemplate.exchange(
                    getAodnExtRecordsEndpoint(),
                    HttpMethod.GET,
                    defaultRequestEntity,
                    new ParameterizedTypeReference<>() {},
                    params);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                return Optional.of(responseEntity.getBody());
            }
        }
        catch(HttpClientErrorException clientErrorException) {
            logger.warn("Fail to call API on additional info, please check api exist?");
            return Optional.empty();
        }
        return Optional.empty();
    }
    /**
     * Call the geonetwork API to get the related info given a uuid, in the related record, you will find the link
     * to the thumbnail
     * @param uuid - UUID of record
     * @return - A Json structure like this
     * {
     *     "children": null,
     *     "parent": null,
     *     "siblings": null,
     *     "associated": null,
     *     "services": null,
     *     "datasets": null,
     *     "fcats": null,
     *     "hasfeaturecats": null,
     *     "sources": null,
     *     "hassources": null,
     *     "related": null,
     *     "onlines": [
     *         {
     *             "id": "https://www.marine.csiro.au/data/trawler/survey_details.cfm?survey=IN2024_V01",
     *             "url": {
     *                 "eng": "https://www.marine.csiro.au/data/trawler/survey_details.cfm?survey=IN2024_V01"
     *             },
     *             "type": "onlinesrc",
     *             "title": {
     *                 "eng": "MNF Data Trawler"
     *             },
     *             "protocol": "WWW:DOWNLOAD-1.0-http--csiro-oa-app",
     *             "description": {
     *                 "eng": "Link to processed data and survey information (plans, summaries, etc.) via MNF Data Trawler"
     *             },
     *             "function": "",
     *             "mimeType": "",
     *             "applicationProfile": ""
     *         },
     *         {
     *             "id": "https://mnf.csiro.au/",
     *             "url": {
     *                 "eng": "https://mnf.csiro.au/"
     *             },
     *             "type": "onlinesrc",
     *             "title": {
     *                 "eng": "Marine National Facility"
     *             },
     *             "protocol": "WWW:LINK-1.0-http--link",
     *             "description": {
     *                 "eng": "Link to the Marine National Facility Webpage"
     *             },
     *             "function": "",
     *             "mimeType": "",
     *             "applicationProfile": ""
     *         },
     *         {
     *             "id": "https://doi.org/10.25919/rdrt-bd71",
     *             "url": {
     *                 "eng": "https://doi.org/10.25919/rdrt-bd71"
     *             },
     *             "type": "onlinesrc",
     *             "title": {
     *                 "eng": "Data Access Portal (DOI)"
     *             },
     *             "protocol": "WWW:DOWNLOAD-1.0-http--csiro-dap",
     *             "description": {
     *                 "eng": "Link to this record at the CSIRO Data Access Portal"
     *             },
     *             "function": "",
     *             "mimeType": "",
     *             "applicationProfile": ""
     *         },
     *         {
     *             "id": "http://www.marine.csiro.au/data/underway/?survey=IN2024_V01",
     *             "url": {
     *                 "eng": "http://www.marine.csiro.au/data/underway/?survey=IN2024_V01"
     *             },
     *             "type": "onlinesrc",
     *             "title": {
     *                 "eng": "Underway Visualisation Tool"
     *             },
     *             "protocol": "WWW:DOWNLOAD-1.0-http--csiro-oa-app",
     *             "description": {
     *                 "eng": "Link to visualisation tool for Near Real-Time Underway Data (NRUD)"
     *             },
     *             "function": "",
     *             "mimeType": "",
     *             "applicationProfile": ""
     *         }
     *     ],
     *     "thumbnails": [
     *         {
     *             "id": "https://www.marine.csiro.au/data/trawler/survey_mapfile.cfm?survey=IN2024_V01&data_type=uwy",
     *             "url": {
     *                 "eng": "https://www.marine.csiro.au/data/trawler/survey_mapfile.cfm?survey=IN2024_V01&data_type=uwy"
     *             },
     *             "type": "thumbnail",
     *             "title": {
     *                 "eng": "Voyage track"
     *             }
     *         }
     *     ]
     * }
     *
     * The need of retryable is because the geonetwork elastic instance is too small, often it memory usage is
     * 75%, it will throw BadRequest exception if we push it too hard, so we need to retry on bad request
     */
    @Retryable(
            retryFor = HttpClientErrorException.BadRequest.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 1500L)
    )
    protected Optional<Map<String, ?>> getRecordRelated(String uuid) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(UUID, uuid);

            logger.debug("Get related record for {}", uuid);
            ResponseEntity<Map<String, ?>> responseEntity = indexerRestTemplate.exchange(
                    getGeoNetworkRelatedEndpoint(),
                    HttpMethod.GET,
                    defaultRequestEntity,
                    new ParameterizedTypeReference<>() {},
                    params
            );

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                return Optional.ofNullable(responseEntity.getBody());
            }
            else {
                return Optional.empty();
            }
        }
        catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }
    /**
     * If geonetwork for some reason reboot, it is cloud env anyway, we keep retry evey 10 seconds
     * @param uuid
     * @return
     */
    @Retryable(
            retryFor = HttpServerErrorException.ServiceUnavailable.class,
            maxAttempts = 50,
            backoff = @Backoff(delay = 10000L)
    )
    protected String findFormatterId(String uuid) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("indexName", getIndexName());
            params.put(UUID, uuid);

            ResponseEntity<JsonNode> responseEntity = indexerRestTemplate.exchange(
                    getGeoNetworkRecordsEndpoint(),
                    HttpMethod.GET,
                    defaultRequestEntity,
                    JsonNode.class,
                    params
            );

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                if (Objects.requireNonNull(responseEntity.getBody()).get("@xsi:schemaLocation").asText().contains("www.isotc211.org/2005/gmd")) {
                    return AppConstants.FORMAT_ISO19115_3_2018;
                }
                else {
                    return AppConstants.FORMAT_XML;
                }
            }
            else {
                throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
            }
        }
        catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }

    public String searchRecordBy(String uuid) {
        try {
            HttpEntity<String> requestEntity = getRequestEntity(null);

            Map<String, Object> params = new HashMap<>();
            params.put("indexName", getIndexName());
            params.put(UUID, uuid);
            params.put("formatterId", this.findFormatterId(uuid));

            ResponseEntity<String> responseEntity = indexerRestTemplate.exchange(
                    getGeoNetworkRecordsEndpoint() + "/formatters/{formatterId}",
                    HttpMethod.GET,
                    requestEntity,
                    String.class,
                    params);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                return StringUtil.encodeUTF8(Objects.requireNonNull(responseEntity.getBody()));
            }
            else {
                throw new RuntimeException("Failed to fetch data from the API");
            }
        }
        catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }

    @Override
    public boolean isMetadataRecordsCountLessThan(int c) {

        if(c < 1) {
            throw new IllegalArgumentException("Compare value less then 1 do not make sense");
        }

        int count = 0;
        Iterable<String> i = this.getAllMetadataRecords();

        for(String s : i) {
            if(s != null) {
                // Null if elastic outsync with geonetwork, that is value deleted in
                // geonetwork but elastic have not re-index.
                count++;
            }

            if(count >= c) {
                return false;
            }
        }

        return true;
    }
    /**
     * The need of retryable is because the geonetwork elastic instance is too small, often it memory usage is
     * 75%, it will throw BadRequest exception if we push it too hard, so we need to retry on bad request
     */
    @Retryable(
            retryFor = HttpClientErrorException.BadRequest.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 1500L)
    )
    @Override
    public Iterable<String> getAllMetadataRecords() {

        try {
            final AtomicReference<String> lastUUID = new AtomicReference<>(null);
            final AtomicReference<SearchResponse<ObjectNode>> response =
                    new AtomicReference<>(gn4ElasticClient.search(createSearchAllUUID(null), ObjectNode.class));

            if(response.get().hits() != null
                    && response.get().hits().hits() != null
                    && !response.get().hits().hits().isEmpty()) {

                // Use iterator so that we can get record by record, otherwise we need to store all record
                // in memory which use up lots of memory
                return () -> new Iterator<>() {
                    // int is enough because we paged query
                    private int index = 0;

                    @Override
                    public boolean hasNext() {
                        // If we hit the end, that means we have iterated to end of page.
                        if (index < response.get().hits().hits().size()) {
                            return true;
                        }
                        else {
                            // Check if we have next page
                            try {
                                response.set(gn4ElasticClient.search(createSearchAllUUID(lastUUID.get()), ObjectNode.class));
                                // Reset counter from start
                                index = 0;
                                return index < response.get().hits().hits().size();
                            }
                            catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    /**
                     * There is a problem with query the elastic directly because the index maybe outdated (not reindexed)
                     * hence there is a chance that the hit size > number of docs, so in case the doc is not found,
                     * we should return null
                     *
                     * @return - The xmldocument or null
                     */
                    @Override
                    public String next() {
                        String uuid = getUUID(index++);

                        if(uuid == null) {
                            return null;
                        }
                        else {
                            // Remember the last UUID
                            lastUUID.set(uuid);

                            try {
                                return GeoNetworkServiceImpl.this.searchRecordBy(uuid);
                            }
                            catch(MetadataNotFoundException me) {
                                // Should be a very rare case where someone deleted the doc in geonetwork
                                // but the index is not refresh yet, so you will get document not found
                                return null;
                            }
                        }
                    }

                    private String getUUID(int index) {
                        if(response.get().hits().hits().get(index).source() != null
                            && response.get().hits().hits().get(index).source().has(UUID)) {

                            return response.get().hits().hits().get(index++).source().get(UUID).asText();
                        }
                        return null;
                    }
                };
            }
            else {
                throw new MetadataNotFoundException("Unable to find any metadata records in GeoNetwork");
            }
        }
        catch(IOException e) {
            throw new RuntimeException("Failed to fetch data from GeoNetwork Elastic API, too busy?");
        }
    }

    @Override
    public String getIndexName() { return indexName; }

    @Override
    public void setIndexName(String i) { indexName = i; }

    @Override
    public String getServer() { return server; }

    @Override
    public void setServer(String s) { server = s; }

    protected String getGeoNetworkRelatedEndpoint() {
        return getServer() + "/geonetwork/srv/api/records/{uuid}/related";
    }

    protected String getGeoNetworkRecordsEndpoint() {
        return getServer() + "/geonetwork/srv/api/{indexName}/{uuid}";
    }

    protected String getAodnExtRecordsEndpoint() {
        return getServer() + "/geonetwork/srv/api/aodn/records/{uuid}/info";
    }

    protected String getGeoNetworkGroupsEndpoint() {
        return getServer() + "/geonetwork/srv/api/groups/{id}";
    }

    protected String getReIndexEndpoint() {
        return getServer() + "/geonetwork/srv/api/site/index?reset=false&asynchronous=false";
    }
    /**
     * According to ElasticSearch Doc:
     * Avoid using from and size to page too deeply or request too many results at once. Search requests usually
     * span multiple shards. Each shard must load its requested hits and the hits for any previous pages into memory.
     * For deep pages or large sets of results, these operations can significantly increase memory and CPU usage,
     * resulting in degraded performance or node failures.
     *
     * You can use the search_after parameter to retrieve the next page of hits using a set of sort values
     * from the previous page.
     *
     * Noted that the search must always sort the same way, in this search it is UUID, there will be a very small
     * chance that new record added in between calls and therefore sort order changed and some record may skip,
     * but not much we can do because it is non-transactional operation.
     *
     * @param searchAfterUUID The UUID found at the end of the previous search, aka you want result after this UUID
     * @return - Another page of UUID
     */
    protected SearchRequest createSearchAllUUID(String searchAfterUUID) {

        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(indexName)
                .query(new MatchAllQuery.Builder().build()._toQuery())    // Match all
                .source(s -> s
                        .filter(f -> f.includes(UUID)))// Only select uuid field
                .sort(so -> so.field(FieldSort.of(f -> f.field(UUID).order(SortOrder.Asc))))
                .size(ES_PAGE_SIZE);

        // Since we only sort by UUID, therefore we only need to searchAfter UUID only.
        if(searchAfterUUID != null) {
            builder = builder.searchAfter(
                    List.of(FieldValue.of(searchAfterUUID))
            );
        }

        return builder.build();
    }
}
