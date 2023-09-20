package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.dto.GeoNetworkSearchRequestBodyDTO;
import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GeoNetworkResourceServiceImpl implements GeoNetworkResourceService {
    @Autowired
    RestTemplate restTemplate;

    @Value("${geonetwork.search.api.endpoint}")
    private String searchEndpoint;

    public Map<String, Object> searchMetadataRecordByUUID(String uuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeoNetworkSearchRequestBodyDTO searchRequestBodyDTO = new GeoNetworkSearchRequestBodyDTO(uuid);
        HttpEntity<GeoNetworkSearchRequestBodyDTO> requestEntity = new HttpEntity<>(searchRequestBodyDTO, headers);
        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(searchEndpoint, requestEntity, Map.class);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            Map<String, Object> outerHits = (Map<String, Object>) Objects.requireNonNull(responseEntity.getBody()).get("hits");
            Map<String, Object> total = (Map<String, Object>) outerHits.get("total");
            if ((int) total.get("value") > 0) {
                List<Map<String, Object>> innerHits = (List<Map<String, Object>>) outerHits.get("hits");
                return (Map<String, Object>) innerHits.get(0).get("_source");
            } else {
                throw new MetadataNotFoundException("Unable to find metadata record with UUID: " + uuid + " in GeoNetwork");
            }
        } else {
            throw new RuntimeException("Failed to fetch data from the API");
        }
    }
}
