package au.org.aodn.cloudoptimized.service;

import au.org.aodn.cloudoptimized.enums.GeoJsonProperty;
import au.org.aodn.cloudoptimized.model.*;
import au.org.aodn.cloudoptimized.model.geojson.FeatureCollectionGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.FeatureGeoJson;
import au.org.aodn.cloudoptimized.model.geojson.PointGeoJson;
import au.org.aodn.metadata.geonetwork.exception.MetadataNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DataAccessServiceImpl implements DataAccessService {

    protected final String accessEndPoint;
    protected final RestTemplate restTemplate;
    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;

    public DataAccessServiceImpl(String serverUrl, String baseUrl, RestTemplate restTemplate, WebClient webClient, ObjectMapper objectMapper) {
        this.accessEndPoint = serverUrl + baseUrl;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    protected String getDataAccessEndpoint() {
        return this.accessEndPoint;
    }

    // parameters are not in use for now. May be useful in the future so just keep it
    protected HttpEntity<String> getRequestEntity(List<MediaType> accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(accept);
        return new HttpEntity<>(headers);
    }

    protected FeatureCollectionGeoJson toFeatureCollection(String uuid, Map<? extends CloudOptimizedEntry, Long> data) {

        // Because the data provider provides data by month, so assume all the dates
        // in data here are all the same. So just get the first date to set
        String dateToSet = null;
        var features = new ArrayList<FeatureGeoJson>();
        for (var entry: data.entrySet()) {
            var feature = new FeatureGeoJson(new PointGeoJson(entry.getKey().getLongitude(), entry.getKey().getLatitude()));
            feature.addProperty(GeoJsonProperty.COUNT.getValue(), entry.getValue());
            feature.addProperty(GeoJsonProperty.DATE.getValue(), entry.getKey().getTime().toString());
            features.add(feature);

            if (dateToSet == null) {
                dateToSet = entry.getKey().getTime().toString();
            } else {
                // if the date is not the same, there must be something wrong in dataprovider. throw exception
                if (!dateToSet.equals(entry.getKey().getTime().toString())) {
                    throw new IllegalArgumentException("All the dates in the data must be the same");
                }
            }
        }
        var featureCollection = new FeatureCollectionGeoJson();
        featureCollection.setFeatures(features);
        featureCollection.addProperty(GeoJsonProperty.COLLECTION.getValue(), uuid);
        featureCollection.addProperty(GeoJsonProperty.DATE.getValue(), dateToSet);
        return featureCollection;
    }

    @Override
    public MetadataEntity getMetadataByUuid(String uuid) {
        HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));

        String url = UriComponentsBuilder
                .fromUriString(getDataAccessEndpoint() + "/metadata/{uuid}")
                .buildAndExpand(uuid)
                .toUriString();

        ResponseEntity<MetadataEntity> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                },
                Map.of()
        );
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        } else {
            return null;
        }
    }


    @Override
    public List<MetadataEntity> getAllMetadata() {
        HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));
        String url = UriComponentsBuilder
                .fromUriString(getDataAccessEndpoint() + "/metadata")
                .toUriString();

        ResponseEntity<List<MetadataEntity>> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                },
                Map.of()
        );
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        } else {
            return List.of();
        }
    }

    @Override
    public Optional<String> getNotebookLink(String uuid) {
        try {
            HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));

            String url = UriComponentsBuilder
                    .fromUriString(getDataAccessEndpoint() + "/data/{uuid}/notebook_url")
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<>() {
                    },
                    Map.of()
            );

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                if (responseEntity.getBody() != null || !responseEntity.getBody().isEmpty()) {
                    return Optional.of(responseEntity.getBody());
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public FeatureCollectionGeoJson getIndexingDatasetByMonth(String uuid, YearMonth yearMonth, List<MetadataFields> fields) {

        var startDate = yearMonth.atDay(1);
        var endDate = yearMonth.atEndOfMonth();

        // currently, we force to get data in the same year to simplify the logic
        if (startDate.getYear() != endDate.getYear()) {
            throw new IllegalArgumentException("Start date and end date must be in the same year");
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromUriString(getDataAccessEndpoint() + "/data/{uuid}")
                    .queryParam("is_to_index", "true")
                    .queryParam("f", "sse/json")
                    .queryParam("start_date", startDate)
                    .queryParam("end_date", endDate)
                    .queryParam("columns", fields)
                    .buildAndExpand(uuid)
                    .toUriString();

            List<CloudOptimizedEntryReducePrecision> allEntries = webClient.get()
                    .uri(url, params)
                    .accept(MediaType.TEXT_EVENT_STREAM) // Explicitly accept SSE
                    .retrieve()
                    .bodyToFlux(String.class)
                    .filter(data -> data.contains("data")) // Filter for data: lines
                    .map(data -> {
                        try {
                            // Deserialize raw event into SSEEvent
                            return objectMapper.readValue(data, SSEEvent.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to parse SSE event: " + data, e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(event -> !"[]".equalsIgnoreCase(event.getData()))   // Remove empty data
                    .filter(event -> "completed".equals(event.getStatus()))     // Only process completed events
                    .map(event -> {
                        try {
                            // Deserialize the data field (JSON string) into List<CloudOptimizedEntryReducePrecision>
                            if (event.getData() != null) {
                                return objectMapper.readValue(event.getData(), new TypeReference<>() {});
                            }
                            return Collections.<CloudOptimizedEntryReducePrecision>emptyList(); // Handle null or missing data
                        }
                        catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to parse SSE data: " + event.getData(), e);
                        }
                    })
                    .flatMap(Flux::fromIterable)
                    .collectList()
                    .block();

            return allEntries != null ? toFeatureCollection(uuid, aggregateData(allEntries)) : null;

        } catch (Exception e) {
            // Do nothing just return empty list
            log.info("Unable to find cloud optimized data with UUID: {} in S3 for {} -> {}", uuid, startDate, endDate, e);
            log.warn("error message is: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public List<TemporalExtent> getTemporalExtentOf(String uuid) {
        try {
            HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> params = new HashMap<>();
            params.put("uuid", uuid);

            String url = UriComponentsBuilder.fromUriString(getDataAccessEndpoint() + "/data/{uuid}/temporal_extent")
                    .buildAndExpand(uuid)
                    .toUriString();

            ResponseEntity<List<TemporalExtent>> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<>() {
                    },
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
     *
     * @param data the data to summarize
     * @return the summarized data
     */
    @Override
    public Map<? extends CloudOptimizedEntry, Long> aggregateData(List<? extends CloudOptimizedEntry> data) {
        return data.stream()
                .collect(Collectors.groupingBy(
                        d -> d,
                        Collectors.counting()
                ));
    }
}
