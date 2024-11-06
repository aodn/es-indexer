package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.model.Datum;
import au.org.aodn.esindexer.model.TemporalExtent;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataAccessServiceImpl implements DataAccessService {

    private String serverUrl;

    @Override
    public String getServiceUrl() {
        return serverUrl;
    }

    @Override
    public void setServiceUrl(String url) {
        this.serverUrl = url;
    }

    public DataAccessServiceImpl(String serverUrl) {
       setServiceUrl(serverUrl);
    }

    private final RestTemplate restTemplate = new RestTemplate();
    @Override
    public Datum[] getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate) {

        // currently, we force to get data in the same year to simplify the logic
        if (startDate.getYear() != endDate.getYear()) {
            throw new IllegalArgumentException("Start date and end date must be in the same year");
        }

        try {
            HttpEntity<String> request = getRequestEntity(null, null);

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromHttpUrl(getDataAccessEndpoint() + "/data/{uuid}")
                    .queryParam("is_to_index", "true")
                    .queryParam("start_date", startDate)
                    .queryParam("end_date", endDate)
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<Datum[]> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Datum[].class,
                    params
            );

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                List<Datum> data = new ArrayList<>();
                if (responseEntity.getBody() != null) {
                    data = List.of(responseEntity.getBody());
                }
                var dataToIndex = aggregateData(data);
                return dataToIndex.toArray(new Datum[0]);
            }
            throw new RuntimeException("Unable to retrieve dataset with UUID: " + uuid );

        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find dataset with UUID: " + uuid + " in GeoNetwork");
        } catch (Exception e) {
            throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + e.getMessage(), e);
        }
    }

    @Override
    public TemporalExtent getTemporalExtentOf(String uuid) {
        try {
            HttpEntity<String> request = getRequestEntity(null, null);

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromHttpUrl(getDataAccessEndpoint() + "/data/{uuid}/temporal_extent")
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<TemporalExtent> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    TemporalExtent.class,
                    params
            );

            return responseEntity.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find dataset with UUID: " + uuid + " in GeoNetwork");
        } catch (Exception e) {
            throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + e.getMessage(), e);
        }
    }

    /**
     * Summarize the data by counting the number if all the concerned fields are the same
     * @param data the data to summarize
     * @return the summarized data
     */
    private List<Datum> aggregateData(List<Datum> data) {
        var aggregatedData = new ArrayList<Datum>();
        for (var datum: data) {
            if (aggregatedData.contains(datum)) {
                var existingDatum = aggregatedData.get(aggregatedData.indexOf(datum));
                existingDatum.incrementCount();
            } else {
                aggregatedData.add(datum);
            }
        }
        return aggregatedData;
    }

    private String getDataAccessEndpoint() {
        return getServiceUrl() + "/api/v1/das/";
    }


    // parameters are not in use for now. May be useful in the future so just keep it
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
