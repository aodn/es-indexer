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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

@Slf4j
public class DataAccessServiceImpl implements DataAccessService {

    protected final String accessEndPoint;
    protected final RestTemplate restTemplate;
    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;
    protected ExecutorService executorService;

    private final static int MAX_RETRY_ATTEMPT = 100;  //times
    private final static int RETRY_DELAY_SECOND = 10; // second
    private final static int HEALTH_CHECK_INTERVAL_SECOND = 30;

    @Value("${dataAccessService.server.healthCheck:true}")
    protected boolean healthCheck;

    public DataAccessServiceImpl(String serverUrl, String baseUrl, RestTemplate restTemplate, WebClient webClient, ObjectMapper objectMapper) {
        this.accessEndPoint = serverUrl + baseUrl;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        int numThreads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(numThreads);
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

    protected boolean isSafeId(String id) {
        // Path-safe id only: metadata ids are not always RFC-4122 UUIDs.
        return id != null && id.matches("^[a-zA-Z0-9-_]+$");
    }

    /**
     * Re-throw transient server errors so {@link Retryable} can back off and retry
     * (e.g. nginx 502/504 while the data-access service restarts).
     */
    protected void rethrowIfRetryable(RuntimeException ex) {
        if (ex instanceof HttpServerErrorException.ServiceUnavailable
                || ex instanceof HttpServerErrorException.BadGateway
                || ex instanceof HttpServerErrorException.GatewayTimeout
                || ex instanceof ResourceAccessException) {
            log.warn("Data access service temporary failure, will retry: {}", ex.getMessage());
            throw ex;
        }
    }

    @Override
    @Retryable(
            retryFor = {
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.BadGateway.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    ResourceAccessException.class
            },
            maxAttempts = MAX_RETRY_ATTEMPT,
            backoff = @Backoff(delay = RETRY_DELAY_SECOND * 1000)
    )
    public Map<String, MetadataEntity> getMetadataByUuid(String uuid) {

        // Validate path argument
        if(isSafeId(uuid)) {
            // Sometimes the server is down due to SPOT instance or software update.
            waitTillServiceUp();

            HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));

            URI uri = UriComponentsBuilder
                    .fromUriString(getDataAccessEndpoint())
                    .pathSegment("metadata", "{uuid}")
                    .buildAndExpand(uuid)
                    .toUri();

            try {
                ResponseEntity<Map<String, MetadataEntity>> responseEntity = restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        request,
                        new ParameterizedTypeReference<>() {
                        }
                );
                if (responseEntity.getStatusCode().is2xxSuccessful()) {
                    return responseEntity.getBody();
                } else {
                    return null;
                }
            }
            catch(HttpClientErrorException.NotFound e) {
                return null;
            }
            catch (HttpServerErrorException | ResourceAccessException e) {
                rethrowIfRetryable(e);
                throw e;
            }
        }
        else {
            log.warn("Id not in correct format {}", uuid);
            return null;
        }
    }

    @Override
    @Retryable(
            retryFor = {
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.BadGateway.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    ResourceAccessException.class
            },
            maxAttempts = MAX_RETRY_ATTEMPT,
            backoff = @Backoff(delay = RETRY_DELAY_SECOND * 1000)
    )
    public Map<String, Map<String, MetadataEntity>> getAllMetadata() {

        try {
            HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));
            String url = UriComponentsBuilder
                    .fromUriString(getDataAccessEndpoint() + "/metadata")
                    .toUriString();

            ResponseEntity<Map<String, Map<String, MetadataEntity>>> responseEntity = restTemplate.exchange(
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
                return Map.of();
            }
        } catch (HttpServerErrorException.ServiceUnavailable ex) {
                // Caught when FastAPI returns 503 (api_status is False)
                String errorJson = ex.getResponseBodyAsString();
                // errorJson contains: {"detail":"API is not ready. Metadata initialization is still in progress."}
                log.error("FastAPI is not ready yet: {}", errorJson);
                throw ex;
        } catch (HttpServerErrorException.BadGateway | HttpServerErrorException.GatewayTimeout | ResourceAccessException ex) {
                // Nginx/proxy returns 502/504 while backend restarts
                rethrowIfRetryable(ex);
                throw ex;
        }
    }

    @Override
    public HealthStatus getHealthStatus() {
        HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));
        String url = UriComponentsBuilder
                .fromUriString(getDataAccessEndpoint() + "/health")
                .toUriString();

        ResponseEntity<Map<String, String>> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {
                },
                Map.of()
        );

        return (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) ?
            HealthStatus.fromValue(responseEntity.getBody().get("status")) :
            HealthStatus.UNKNOWN;
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
                if (responseEntity.getBody() != null && !responseEntity.getBody().isEmpty()) {
                    // The response is a JSON string (e.g., "https://..."), so we need to deserialize it
                    // to remove the enclosing quotes and handle any escape characters properly
                    String notebookUrl = objectMapper.readValue(responseEntity.getBody(), String.class);
                    return Optional.of(notebookUrl);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    protected void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @Retryable(
            retryFor = {
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.BadGateway.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    ResourceAccessException.class
            },
            maxAttempts = MAX_RETRY_ATTEMPT,
            backoff = @Backoff(delay = RETRY_DELAY_SECOND * 1000)
    )
    public List<TemporalExtent> getTemporalExtentOf(String uuid, String key) {
        if(isSafeId(uuid)) {
            log.info("Fetching temporal extent of UUID: {}, {}", uuid, key);
            try {
                HttpEntity<String> request = getRequestEntity(List.of(MediaType.APPLICATION_JSON));

                String url = UriComponentsBuilder.fromUriString(getDataAccessEndpoint() + "/data/{uuid}/{key}/temporal_extent")
                        .buildAndExpand(uuid, key)
                        .toUriString();

                ResponseEntity<List<TemporalExtent>> responseEntity = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        request,
                        new ParameterizedTypeReference<>() {
                        },
                        Map.of()
                );

                return responseEntity.getBody();

            }
            catch (HttpClientErrorException.NotFound e) {
                throw new MetadataNotFoundException("temporal_extent of uuid not found: " + uuid + " in DataAccess Service");
            }
            catch (HttpServerErrorException | ResourceAccessException e) {
                // Must rethrow as-is so @Retryable can match 502/503/504 and connection errors
                rethrowIfRetryable(e);
                throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + " " + e.getMessage(), e);
            }
            catch (Exception e) {
                throw new RuntimeException("Exception thrown while retrieving dataset with UUID: " + uuid + " " + e.getMessage(), e);
            }
        }
        else {
            throw new MetadataNotFoundException("Malform UUID in request: " + uuid);
        }
    }
    /**
     * Wait till the service is up. Keeps polling when health returns non-UP or throws
     * (e.g. nginx 502 while the backend is restarting).
     */
    @Override
    public void waitTillServiceUp() {
        if(healthCheck) {
            while (true) {
                try {
                    if (this.getHealthStatus() == HealthStatus.UP) {
                        return;
                    }
                    log.info("Data access service not UP yet, waiting before re-check");
                } catch (Exception e) {
                    log.warn("Data access service health check failed, waiting before re-check: {}", e.getMessage());
                }
                try {
                    TimeUnit.SECONDS.sleep(HEALTH_CHECK_INTERVAL_SECOND);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (executorService != null) {
            shutdownExecutor(executorService);
        }
    }

}
