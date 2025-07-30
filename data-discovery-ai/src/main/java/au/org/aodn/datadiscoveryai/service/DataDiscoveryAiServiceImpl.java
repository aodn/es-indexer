package au.org.aodn.datadiscoveryai.service;

import au.org.aodn.datadiscoveryai.enums.AIModel;
import au.org.aodn.datadiscoveryai.model.AiEnhancedLink;
import au.org.aodn.datadiscoveryai.model.AiEnhancementRequest;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.stac.model.LinkModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DataDiscoveryAiServiceImpl implements DataDiscoveryAiService {

    private final String serviceUrl;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;

    public DataDiscoveryAiServiceImpl(String serviceUrl, String baseUrl,
                                      RestTemplate restTemplate, WebClient webClient, ObjectMapper objectMapper) {
        this.serviceUrl = serviceUrl;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LinkModel> enhanceWithLinkGrouping(String uuid, List<LinkModel> links) {
        if (links == null || links.isEmpty()) {
            return links;
        }

        var selectedModels = List.of(AIModel.LINK_GROUPING.getValue());

        try {
            AiEnhancementRequest request = AiEnhancementRequest.builder()
                    .selectedModel(selectedModels)
                    .uuid(uuid)
                    .links(links)
                    .build();

            String url = serviceUrl + baseUrl;

            Flux<ServerSentEvent<String>> eventStream = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

            ServerSentEvent<String> doneEvent = eventStream
                    .filter(e -> "done".equals(e.event()))
                    .blockFirst();

            if (doneEvent != null && doneEvent.data() != null) {
                log.info("Successfully calling Data Discovery AI service for UUID: {} with {} links", uuid, links.size());
                AiEnhancementResponse responseObj = objectMapper.readValue(doneEvent.data(), AiEnhancementResponse.class);
                return convertAiEnhancedLinksToLinkModels(responseObj.getLinks());
            } else {
                log.warn("Received non-successful response from AI service: Processing not completed.");
                return links;
            }

        } catch (HttpClientErrorException e) {
            log.error("Client error when calling Data Discovery AI service for UUID: {} - Status: {}, Response: {}",
                    uuid, e.getStatusCode(), e.getResponseBodyAsString());
            return links;
        } catch (RestClientException e) {
            log.error("Error calling Data Discovery AI service for UUID: {}", uuid, e);
            return links;
        } catch (Exception e) {
            log.error("Unexpected error when enhancing links for UUID: {}", uuid, e);
            return links;
        }
    }

    @Override
    public boolean isServiceAvailable() {
        try {
            String healthUrl = serviceUrl + "/api/v1/ml/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Data Discovery AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    private List<LinkModel> convertAiEnhancedLinksToLinkModels(List<AiEnhancedLink> aiEnhancedLinks) {
        if (aiEnhancedLinks == null) {
            return List.of();
        }

        return aiEnhancedLinks.stream()
                .map(aiLink -> LinkModel.builder()
                        .href(aiLink.getHref())
                        .rel(aiLink.getRel())
                        .type(aiLink.getType())
                        .title(aiLink.getTitle())
                        .aiGroup(aiLink.getAiGroup())
                        .build())
                .collect(Collectors.toList());
    }
}
