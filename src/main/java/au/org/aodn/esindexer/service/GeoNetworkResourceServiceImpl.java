package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.dto.MetadataRecordsCountRequestBodyDTO;
import au.org.aodn.esindexer.dto.GeoNetworkSearchRequestBodyDTO;
import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class GeoNetworkResourceServiceImpl implements GeoNetworkResourceService {
    @Autowired
    RestTemplate restTemplate;

    @Value("${geonetwork.search.api.endpoint}")
    private String searchEndpoint;

    public JSONObject searchMetadataRecordByUUID(String uuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        GeoNetworkSearchRequestBodyDTO searchRequestBodyDTO = new GeoNetworkSearchRequestBodyDTO(uuid);
        HttpEntity<GeoNetworkSearchRequestBodyDTO> requestEntity = new HttpEntity<>(searchRequestBodyDTO, headers);
        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(searchEndpoint, requestEntity, Map.class);
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

    public int getMetadataRecordsCount() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        MetadataRecordsCountRequestBodyDTO requestBodyDTO = new MetadataRecordsCountRequestBodyDTO();
        HttpEntity<MetadataRecordsCountRequestBodyDTO> requestEntity = new HttpEntity<>(requestBodyDTO, headers);
        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(searchEndpoint, requestEntity, Map.class);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            JSONObject jsonResult = new JSONObject(responseEntity.getBody());
            JSONObject outerHits = jsonResult.getJSONObject("hits");
            JSONObject total = outerHits.getJSONObject("total");
            return (int) total.get("value");
        } else {
            throw new RuntimeException("Failed to fetch data from the API");
        }
    }
}
