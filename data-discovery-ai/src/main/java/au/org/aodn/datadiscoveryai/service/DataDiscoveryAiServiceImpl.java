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

import java.util.ArrayList;
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
    public List<LinkModel> enhanceLinkGrouping(String uuid, List<LinkModel> links) {
        if (links == null || links.isEmpty()) {
            return links;
        }

        AiEnhancementResponse response = enhanceWithAi(uuid, links, null, null);
        if (response != null && response.getLinks() != null) {
            return convertAiEnhancedLinksToLinkModels(response.getLinks());
        }
        return links;
    }

    @Override
    public String enhanceDescription(String uuid, String title, String description) {
        if ((title == null || title.isEmpty()) && (description == null || description.isEmpty())) {
            return null;
        }

        AiEnhancementResponse response = enhanceWithAi(uuid, null, title, description);
        if (response != null && response.getSummaries() != null && response.getSummaries().containsKey("ai:description")) {
            return response.getSummaries().get("ai:description");
        }
        return null;
    }

    @Override
    public AiEnhancementResponse enhanceWithAi(String uuid, List<LinkModel> links, String title, String description) {
        List<String> selectedModels = new ArrayList<>();

        // Add models based on provided parameters
        if (links != null && !links.isEmpty()) {
            selectedModels.add(AIModel.LINK_GROUPING.getValue());
        }
        if ((title != null && !title.isEmpty()) || (description != null && !description.isEmpty())) {
            selectedModels.add(AIModel.DESCRIPTION_FORMATTING.getValue());
        }

        if (selectedModels.isEmpty()) {
            return null;
        }

        try {
            AiEnhancementRequest request = AiEnhancementRequest.builder()
                    .selectedModel(selectedModels)
                    .uuid(uuid)
                    .title(title)
                    .abstractText(description)
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
                    .doOnNext(event -> {
                        if ("error".equals(event.event())) {
                            log.error("Failed to call Data Discovery AI service: {}", event.data());
                        } else if ("processing".equals(event.event())) {
                            log.info("Data Discovery AI service processing...");
                        }
                    })
                    .filter(event -> "done".equals(event.event()))
                    .blockFirst();

            if (doneEvent != null && doneEvent.data() != null) {
                log.info("Successfully calling Data Discovery AI service for UUID: {} with {} models", uuid, selectedModels.size());
                return objectMapper.readValue(doneEvent.data(), AiEnhancementResponse.class);
            } else {
                log.warn("Received non-successful response from AI service: Processing not completed.");
                return null;
            }

        } catch (HttpClientErrorException e) {
            log.error("Client error when calling Data Discovery AI service for UUID: {} - Status: {}, Response: {}",
                    uuid, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (RestClientException e) {
            log.error("Error calling Data Discovery AI service for UUID: {}", uuid, e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error when enhancing content for UUID: {}", uuid, e);
            return null;
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

    /**
     * Public utility method to convert AI-enhanced links to LinkModel objects
     * This is used by other services that need to convert AI response links
     */
    public List<LinkModel> convertAiLinksToLinkModels(List<AiEnhancedLink> aiEnhancedLinks) {
        return convertAiEnhancedLinksToLinkModels(aiEnhancedLinks);
    }

}
