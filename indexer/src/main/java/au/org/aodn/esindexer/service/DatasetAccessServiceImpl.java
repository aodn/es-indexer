package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MetadataNotFoundException;
import au.org.aodn.esindexer.model.DataRecord;
import au.org.aodn.esindexer.model.Dataset;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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
    public Dataset getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate) {

        // currently, we force to get data in the same month and year to simplify the logic
        if (startDate.getMonth() != endDate.getMonth() || startDate.getYear() != endDate.getYear()) {
            throw new IllegalArgumentException("Start date and end date must be in the same month and year");
        }

        try {
            HttpEntity<String> request = getRequestEntity(null, null);

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromHttpUrl(getDatasetAccessEndpoint() + "/data/{uuid}")
                    .queryParam("is_to_index", "true")
                    .queryParam("start_date", startDate)
                    .queryParam("end_date", endDate)
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<DataRecord[]> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    DataRecord[].class,
                    params
            );



            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                List<DataRecord> records = new ArrayList<>();
                if (responseEntity.getBody() != null) {
                    records = List.of(responseEntity.getBody());
                }
                var recordsToIndex = summarizeRecords(records);
                return new Dataset(
                        uuid,
                        YearMonth.of(startDate.getYear(), startDate.getMonth()),
                        recordsToIndex
                );
            }
            throw new RuntimeException("Unable to retrieve dataset with UUID: " + uuid );

        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find dataset with UUID: " + uuid + " in GeoNetwork");
        } catch (Exception e) {
            throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + e.getMessage(), e);
        }
    }

    @Override
    public boolean doesDataExist(String uuid, LocalDate startDate, LocalDate endDate) {
        try {
            HttpEntity<String> request = getRequestEntity(null, null);

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromHttpUrl(getDatasetAccessEndpoint() + "/data/{uuid}/has_data")
                    .queryParam("start_date", startDate)
                    .queryParam("end_date", endDate)
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<Boolean> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Boolean.class,
                    params
            );

            return Boolean.TRUE.equals(responseEntity.getBody());

        } catch (HttpClientErrorException.NotFound e) {
            throw new MetadataNotFoundException("Unable to find dataset with UUID: " + uuid + " in GeoNetwork");
        } catch (Exception e) {
            throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + e.getMessage(), e);
        }
    }

    /**
     * Summarize the records by counting the number if all the concerned fields are the same
     * @param records the records to summarize
     * @return the summarized records
     */
    private List<DataRecord> summarizeRecords(List<DataRecord> records) {
        var summarizedRecords = new ArrayList<DataRecord>();
        for (var record: records) {
            if (summarizedRecords.contains(record)) {
                var existingRecord = summarizedRecords.get(summarizedRecords.indexOf(record));
                existingRecord.incrementCount();
            } else {
                summarizedRecords.add(record);
            }
        }
        return summarizedRecords;
    }

    private String getDatasetAccessEndpoint() {
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
