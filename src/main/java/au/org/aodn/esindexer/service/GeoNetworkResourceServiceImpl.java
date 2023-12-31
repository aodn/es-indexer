package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.dto.MetadataRecordsCountRequestBodyDTO;
import au.org.aodn.esindexer.dto.GeoNetworkSearchRequestBodyDTO;
import au.org.aodn.esindexer.exception.GNConnectionRefusedException;
import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.utils.StringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeoNetworkResourceServiceImpl implements GeoNetworkResourceService {
    @Autowired
    RestTemplate restTemplate;

    @Value("${geonetwork.search.api.endpoint}")
    private String geoNetworkElasticsearchEndpoint;

    @Value("${geonetwork.records.endpoint}")
    private String geoNetworkRecordsEndpoint;

    private static final Logger logger = LoggerFactory.getLogger(GeoNetworkResourceServiceImpl.class);

    public JSONObject searchMetadataRecordByUUIDFromGNRecordsIndex(String uuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        GeoNetworkSearchRequestBodyDTO searchRequestBodyDTO = new GeoNetworkSearchRequestBodyDTO(uuid);
        HttpEntity<GeoNetworkSearchRequestBodyDTO> requestEntity = new HttpEntity<>(searchRequestBodyDTO, headers);
        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(geoNetworkElasticsearchEndpoint, requestEntity, Map.class);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            JSONObject jsonResult = new JSONObject(responseEntity.getBody());
            JSONObject outerHits = jsonResult.getJSONObject("hits");
            JSONObject total = outerHits.getJSONObject("total");
            if ((int) total.get("value") > 0) {
                JSONArray innerHits = outerHits.getJSONArray("hits");
                return innerHits.getJSONObject(0).getJSONObject("_source");
            } else {
                throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
            }
        } else {
            throw new RuntimeException("Failed to fetch data from the API");
        }
    }

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
            }
            else {
                throw new RuntimeException("Failed to fetch data from the API");
            }
        }
        catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }

    public String searchMetadataRecordByUUIDFromGN4(String uuid) {
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
        }
        catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
        }
    }

    protected ResponseEntity<Map> responseEntityInit() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        MetadataRecordsCountRequestBodyDTO requestBodyDTO = new MetadataRecordsCountRequestBodyDTO();
        HttpEntity<MetadataRecordsCountRequestBodyDTO> requestEntity = new HttpEntity<>(requestBodyDTO, headers);
        return restTemplate.postForEntity(geoNetworkElasticsearchEndpoint, requestEntity, Map.class);
    }

    public int getMetadataRecordsCount() {
        ResponseEntity<Map> responseEntity = this.responseEntityInit();
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            JSONObject jsonResult = new JSONObject(responseEntity.getBody());
            JSONObject outerHits = jsonResult.getJSONObject("hits");
            JSONObject total = outerHits.getJSONObject("total");
            return (int) total.get("value");
        } else {
            throw new RuntimeException("Failed to fetch data from the API");
        }
    }

    public List<String> getAllMetadataRecords() {
        ResponseEntity<Map> responseEntity = this.responseEntityInit();
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            JSONObject jsonResult = new JSONObject(responseEntity.getBody());
            JSONObject outerHits = jsonResult.getJSONObject("hits");
            JSONArray innerHit = outerHits.getJSONArray("hits");
            if (!innerHit.isEmpty()) {
                logger.info("Found " + innerHit.length() + " metadata records in GeoNetwork");
                List<String> metadataRecords = new ArrayList<>();
                for (int i = 0; i < innerHit.length(); i++) {
                    // TODO: can this be optimised pulling all records directly from some endpoint of GN4?
                    // for now assume that gn_records contents are same as in GN4, search for list of UUIDs from gn_records to get metadata records from GN4
                    String uuid = (String) innerHit.getJSONObject(i).getJSONObject("_source").get("uuid");
                    metadataRecords.add(this.searchMetadataRecordByUUIDFromGN4(uuid));
                }
                return metadataRecords;
            } else {
                throw new MetadataNotFoundException("Unable to find metadata records in GeoNetwork");
            }
        } else {
            throw new RuntimeException("Failed to fetch data from the API");
        }
    }
}
