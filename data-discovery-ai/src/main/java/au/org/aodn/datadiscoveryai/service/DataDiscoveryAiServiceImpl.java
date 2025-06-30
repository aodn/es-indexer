package au.org.aodn.datadiscoveryai.service;

import au.org.aodn.datadiscoveryai.enums.AIModel;
import au.org.aodn.datadiscoveryai.model.AiEnhancedLink;
import au.org.aodn.datadiscoveryai.model.AiEnhancementRequest;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.stac.model.LinkModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DataDiscoveryAiServiceImpl implements DataDiscoveryAiService {

    private final String serviceUrl;
    private final String baseUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;

    public DataDiscoveryAiServiceImpl(String serviceUrl, String baseUrl, String apiKey, 
                                     RestTemplate restTemplate) {
        this.serviceUrl = serviceUrl;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                headers.set("X-API-Key", apiKey);
            }

            HttpEntity<AiEnhancementRequest> requestEntity = new HttpEntity<>(request, headers);

            String url = serviceUrl + baseUrl;

            ResponseEntity<AiEnhancementResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<AiEnhancementResponse>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully calling Data Discovery AI service for UUID: {} with {} links", uuid, links.size());
                return convertAiEnhancedLinksToLinkModels(response.getBody().getLinks());
            } else {
                log.warn("Received non-successful response from AI service: {}", response.getStatusCode());
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
