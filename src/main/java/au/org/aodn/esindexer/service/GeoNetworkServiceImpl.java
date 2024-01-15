package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.utils.StringUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Service
public class GeoNetworkServiceImpl implements GeoNetworkService {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    @Qualifier("gn4ElasticsearchClient")
    protected ElasticsearchClient gn4ElasticClient;

    protected static final Logger logger = LoggerFactory.getLogger(GeoNetworkServiceImpl.class);

    protected final SearchRequest GEONETWORK_ALL_UUID;

    protected final SearchRequest GEONETWORK_ALL_COUNT;

    protected String indexName;

    protected String server;

    protected HttpEntity<String> getRequestEntity(String body) {
        return getRequestEntity(null, body);
    }

    protected HttpEntity<String> getRequestEntity(MediaType accept, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(accept == null ? MediaType.APPLICATION_XML : accept));
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    public GeoNetworkServiceImpl(
            @Value("${geonetwork.host}") String server,
            @Value("${geonetwork.search.api.index}") String indexName) {

        GEONETWORK_ALL_UUID = new SearchRequest.Builder()
                .index(indexName)
                .query(new MatchAllQuery.Builder().build()._toQuery())    // Match all
                .source(s -> s
                        .filter(f -> f.includes("uuid")))           // Only select uuid field
                .build();
        logger.info("GEONETWORK_ALL_UUID -> {}", GEONETWORK_ALL_UUID);

        GEONETWORK_ALL_COUNT = new SearchRequest.Builder()
                .index(indexName)
                //.size(0)            // Do not return value, use count is enough
                .query(new MatchAllQuery.Builder().build()._toQuery())    // Match all
                .source(s -> s
                        .filter(f -> f.includes("uuid")))           // Only select uuid field
                .build();

        logger.info("GEONETWORK_ALL_COUNT -> {}", GEONETWORK_ALL_COUNT);

        setIndexName(indexName);
        setServer(server);
    }

    protected String findFormatterId(String uuid) {
        try {
            HttpEntity<String> requestEntity = getRequestEntity(MediaType.APPLICATION_JSON, null);

            Map<String, Object> params = new HashMap<>();
            params.put("indexName", getIndexName());
            params.put("uuid", uuid);

            ResponseEntity<JsonNode> responseEntity = restTemplate.exchange(
                    getGeoNetworkRecordsEndpoint(),
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class, params);

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
            params.put("uuid", uuid);
            params.put("formatterId", this.findFormatterId(uuid));

            ResponseEntity<String> responseEntity = restTemplate.exchange(
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

            if(Objects.requireNonNull(response.hits().hits().size() != 0)) {
                // Use iterator so that we can get record by record, otherwise we need to store all record
                // in memory which use up lots of memory
                return () -> new Iterator<>() {

                    protected int index = 0;

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
                        String uuid = response.hits().hits().get(index++).source().get("uuid").asText();
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
                throw new MetadataNotFoundException("Unable to find metadata records in GeoNetwork");
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

    protected String getGeoNetworkRecordsEndpoint() {
        return getServer() + "/geonetwork/srv/api/{indexName}/{uuid}";
    }

    protected String getReIndexEndpoint() {
        return getServer() + "/geonetwork/srv/api/site/index?reset=false&asynchronous=false";
    }
}
