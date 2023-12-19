package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.utils.StringUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
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

    @Value("${geonetwork.records.endpoint}")
    private String geoNetworkRecordsEndpoint;

    protected static final Logger logger = LoggerFactory.getLogger(GeoNetworkServiceImpl.class);

    protected final SearchRequest GEONETWORK_ALL_UUID;

    protected final SearchRequest GEONETWORK_ALL_COUNT;

    public GeoNetworkServiceImpl(@Value("${geonetwork.search.api.index}") String indexName) {

        GEONETWORK_ALL_UUID = new SearchRequest.Builder()
                .index(indexName)
                .query(new MatchAllQuery.Builder().build()._toQuery())    // Match all
                .source(s -> s
                        .filter(f -> f.includes("uuid")))           // Only select uuid field
                .build();

        GEONETWORK_ALL_COUNT = new SearchRequest.Builder()
                .index(indexName)
                .size(0)            // Do not return value, use count is enough
                .query(new MatchAllQuery.Builder().build()._toQuery())    // Match all
                .source(s -> s
                        .filter(f -> f.includes("uuid")))           // Only select uuid field
                .build();
    }

    /**
     * This function use the search function provided by GeoNetwork to get information not possible by normal API,
     * Geonetwork4 use elastic search at the back and hence we issue an Elastic Search here.
     *
     * Since it access the internal structure directly, try to avoid using it if possible.
     * @param uuid
     * @return
     */
//    public JSONObject searchMetadataBy(String uuid) {
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        GeoNetworkSearchRequestBodyDTO searchRequestBodyDTO = new GeoNetworkSearchRequestBodyDTO(uuid);
//        HttpEntity<GeoNetworkSearchRequestBodyDTO> requestEntity = new HttpEntity<>(searchRequestBodyDTO, headers);
//
//        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(geoNetworkSearchEndpoint, requestEntity, Map.class);
//        if (responseEntity.getStatusCode().is2xxSuccessful()) {
//            JSONObject jsonResult = new JSONObject(responseEntity.getBody());
//            JSONObject outerHits = jsonResult.getJSONObject("hits");
//            JSONObject total = outerHits.getJSONObject("total");
//            if ((int) total.get("value") > 0) {
//                JSONArray innerHits = outerHits.getJSONArray("hits");
//                return innerHits.getJSONObject(0).getJSONObject("_source");
//            }
//            else {
//                throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
//            }
//        } else {
//            throw new RuntimeException("Failed to fetch data from the API");
//        }
//    }

    protected String findFormatterId(String uuid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<String> requestEntity = new HttpEntity<>(headers);
            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            ResponseEntity<JsonNode> responseEntity = restTemplate.exchange(
                    geoNetworkRecordsEndpoint,
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class, params);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                if (Objects.requireNonNull(responseEntity.getBody()).get("@xsi:schemaLocation").asText().contains("www.isotc211.org/2005/gmd")) {
                    return "iso19115-3.2018";
                } else {
                    return "xml";
                }
            } else {
                throw new RuntimeException("Failed to fetch data from the API");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }

    public String searchRecordBy(String uuid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_XML));
            HttpEntity<String> requestEntity = new HttpEntity<>(headers);

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);
            params.put("formatterId", this.findFormatterId(uuid));

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    geoNetworkRecordsEndpoint + "/formatters/{formatterId}",
                    HttpMethod.GET,
                    requestEntity,
                    String.class,
                    params);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                return StringUtil.encodeUTF8(Objects.requireNonNull(responseEntity.getBody()));
            } else {
                throw new RuntimeException("Failed to fetch data from the API");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }

//    protected ResponseEntity<Map> responseEntityInit() {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        MetadataRecordsCountRequestBodyDTO requestBodyDTO = new MetadataRecordsCountRequestBodyDTO();
//        HttpEntity<MetadataRecordsCountRequestBodyDTO> requestEntity = new HttpEntity<>(requestBodyDTO, headers);
//        return restTemplate.postForEntity(geoNetworkSearchEndpoint, requestEntity, Map.class);
//    }

    public long getMetadataRecordsCount() {
        try {
            // Do not use hits().total() as it may be different from hits().hits().size() if index isn't refresh
            final SearchResponse<ObjectNode> response = gn4ElasticClient.search(GEONETWORK_ALL_COUNT, ObjectNode.class);
            return response.hits().hits().size();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to fetch data from the API", e);
        }
    }

    public Iterable<String> getAllMetadataRecords() {

        try {
            final SearchResponse<ObjectNode> response = gn4ElasticClient.search(GEONETWORK_ALL_UUID, ObjectNode.class);
            // Do not use hits().total() as it may be different from hits().hits().size() if index isn't refresh
            if(Objects.requireNonNull(response.hits().hits()).size() != 0) {
                // Delay the
                return () -> new Iterator<>() {

                    protected int index = 0;

                    @Override
                    public boolean hasNext() {
                        return index < response.hits().hits().size();
                    }

                    @Override
                    public String next() {
                        // TODO: Potential problem with edge case where the list is bigger then default fetch
                        // and you need to page, the response.hits().total().value() return long but get() below use int.
                        String uuid = response.hits().hits().get(index++).source().get("uuid").asText();
                        return GeoNetworkServiceImpl.this.searchRecordBy(uuid);
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
//    public List<String> getAllMetadataRecords() {
//        ResponseEntity<Map> responseEntity = this.responseEntityInit();
//        if (responseEntity.getStatusCode().is2xxSuccessful()) {
//            JSONObject jsonResult = new JSONObject(responseEntity.getBody());
//            JSONObject outerHits = jsonResult.getJSONObject("hits");
//            JSONArray innerHit = outerHits.getJSONArray("hits");
//            if (!innerHit.isEmpty()) {
//                logger.info("Found " + innerHit.length() + " metadata records in GeoNetwork");
//                List<String> metadataRecords = new ArrayList<>();
//                for (int i = 0; i < innerHit.length(); i++) {
//                    // TODO: Potential problem with consume too many memory as you read all record.
//                    String uuid = (String) innerHit.getJSONObject(i).getJSONObject("_source").get("uuid");
//                    metadataRecords.add(this.searchRecordBy(uuid));
//                }
//                return metadataRecords;
//            } else {
//                throw new MetadataNotFoundException("Unable to find metadata records in GeoNetwork");
//            }
//        } else {
//            throw new RuntimeException("Failed to fetch data from the API");
//        }
//    }
}
