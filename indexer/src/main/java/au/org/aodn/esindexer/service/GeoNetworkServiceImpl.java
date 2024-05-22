package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.utils.StringUtil;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.utils.UrlUtils;
import au.org.aodn.stac.model.LinkModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

public class GeoNetworkServiceImpl implements GeoNetworkService {

    public static final String SUGGEST_LOGOS = "suggest_logos";
    public static final String THUMB_NAILS = "thumbnails";
    public static final String URL = "url";

    @Autowired
    protected UrlUtils urlUtils;

    protected static final Logger logger = LogManager.getLogger(GeoNetworkServiceImpl.class);

    protected RestTemplate indexerRestTemplate;
    protected ElasticsearchClient gn4ElasticClient;
    protected final SearchRequest GEONETWORK_ALL_UUID;
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

        GEONETWORK_ALL_UUID = new SearchRequest.Builder()
                .index(indexName)
                .query(new MatchAllQuery.Builder().build()._toQuery())    // Match all
                .source(s -> s
                        .filter(f -> f.includes(UUID)))// Only select uuid field
                // TODO: redesign the iterator to be more efficient
                /* by default ES will return just 10 top hits (10 records of the thousands available records),
                the iterator implementation in getAllMetadataRecords() method will help saving memory but process those 10 records only,
                need to temporarily increase the size of returning hits
                 */
                .size(2000)
                .build();
        logger.info("GEONETWORK_ALL_UUID -> {}", GEONETWORK_ALL_UUID);

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
        Optional<Map<String, ?>> optRelated = getRecordRelated(uuid);
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
            logger.warn("Fail to call API on additional info, please check api exist?", clientErrorException);
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
     */
    protected Optional<Map<String, ?>> getRecordRelated(String uuid) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put(UUID, uuid);

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

    @Override
    public Iterable<String> getAllMetadataRecords() {

        try {
            // TODO: Can the elastic index not update after insert dataset into GeoNetwork?
            final SearchResponse<ObjectNode> response = gn4ElasticClient.search(GEONETWORK_ALL_UUID, ObjectNode.class);

            if(response.hits() != null && response.hits().hits() != null && !response.hits().hits().isEmpty()) {
                // Use iterator so that we can get record by record, otherwise we need to store all record
                // in memory which use up lots of memory
                return () -> new Iterator<>() {

                    private int index = 0;

                    @Override
                    public boolean hasNext() {
                        return index < response.hits().hits().size();
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
                        // TODO: Potential problem with edge case where the list is bigger then size set Integer.MAX
                        String uuid = response.hits().hits().get(index++).source().get(UUID).asText();
                        try {
                            return GeoNetworkServiceImpl.this.searchRecordBy(uuid);
                        }
                        catch(MetadataNotFoundException me) {
                            // Should be a very rare case where someone deleted the doc in geonetwork
                            // but the index is not refresh yet, so you will get document not found
                            return null;
                        }
                    }
                };
            }
            else {
                throw new MetadataNotFoundException("Unable to find any metadata records in GeoNetwork");
            }
        }
        catch(IOException e) {
            throw new RuntimeException("Failed to fetch data from the API");
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
}
