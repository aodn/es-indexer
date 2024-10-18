package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.model.DataRecord;
import au.org.aodn.esindexer.model.Dataset;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatasetAccessServiceImpl implements DatasetAccessService {

    private String serverUrl;

    @Override
    public String getServiceUrl() {
        return serverUrl;
    }

    @Override
    public void setServiceUrl(String url) {
        this.serverUrl = url;
    }

    public DatasetAccessServiceImpl(String serverUrl) {
       setServiceUrl(serverUrl);
    }

    private final RestTemplate restTemplate = new RestTemplate();
    @Override
    public Dataset getIndexingDatasetBy(String uuid) {
        try {
            HttpEntity<String> request = getRequestEntity(null, null);

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromHttpUrl(getDatasetAccessEndpoint() + "/data/{uuid}")
                    .queryParam("is_to_index", "true")
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<DataRecord[]> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    DataRecord[].class,
                    params
            );

//            ResponseEntity<DataRecord[]> responseEntity = restTemplate.getForEntity(url, DataRecord[].class, params);


            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                var records = responseEntity.getBody();
                return new Dataset(uuid, List.of(records));
            }
            throw new RuntimeException("Unable to retrieve dataset with UUID: " + uuid );

        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find dataset with UUID: " + uuid + " in GeoNetwork");
        } catch (Exception e) {
            throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + e.getMessage(), e);
        }
    }

    private String getDatasetAccessEndpoint() {
        return getServiceUrl() + "/api/v1/das/";
    }


    protected HttpEntity<String> getRequestEntity(MediaType accept, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_JSON,
                MediaType.valueOf("application/*+json"),
                MediaType.ALL
        ));
        return body == null ? new org.springframework.http.HttpEntity<>(headers) : new org.springframework.http.HttpEntity<>(body, headers);
    }
}
