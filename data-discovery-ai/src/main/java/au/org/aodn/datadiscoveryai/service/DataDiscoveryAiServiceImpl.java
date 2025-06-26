package au.org.aodn.datadiscoveryai.service;

import au.org.aodn.datadiscoveryai.model.AiEnhancedLink;
import au.org.aodn.datadiscoveryai.model.AiEnhancementRequest;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.stac.model.LinkModel;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public DataDiscoveryAiServiceImpl(String serviceUrl, String baseUrl, String apiKey, 
                                     RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.serviceUrl = serviceUrl;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LinkModel> enhanceLinksWithAiGrouping(String uuid, List<LinkModel> links) {
        if (!isServiceAvailable()) {
            log.warn("Data Discovery AI service is not available, returning original links");
            return links;
        }

        if (links == null || links.isEmpty()) {
            log.debug("No links provided for enhancement, returning empty list");
            return links;
        }

        var selectedModels = List.of("link_grouping");

        try {
            // Prepare the request
            AiEnhancementRequest request = AiEnhancementRequest.builder()
                    .selectedModel(selectedModels)
                    .uuid(uuid)
                    .links(links)
                    .build();

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                headers.set("X-API-Key", apiKey);
            }

            HttpEntity<AiEnhancementRequest> requestEntity = new HttpEntity<>(request, headers);

            String url = serviceUrl + baseUrl;

            log.info("Calling Data Discovery AI service for UUID: {} with {} links", uuid, links.size());

            // Make the API call
            ResponseEntity<AiEnhancementResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<AiEnhancementResponse>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully received AI enhancement for UUID: {}", uuid);
                return convertAiEnhancedLinksToLinkModels(response.getBody().getLinks());
            } else {
                log.warn("Received non-successful response from AI service: {}", response.getStatusCode());
                return links; // Return original links on failure
            }

        } catch (HttpClientErrorException e) {
            log.error("Client error when calling Data Discovery AI service for UUID: {} - Status: {}, Response: {}", 
                     uuid, e.getStatusCode(), e.getResponseBodyAsString());
            return links; // Return original links on failure
        } catch (RestClientException e) {
            log.error("Error calling Data Discovery AI service for UUID: {}", uuid, e);
            return links; // Return original links on failure
        } catch (Exception e) {
            log.error("Unexpected error when enhancing links for UUID: {}", uuid, e);
            return links; // Return original links on failure
        }
    }

    @Override
    public boolean isServiceAvailable() {
        try {
            String healthUrl = serviceUrl + "/api/v1/ml/health";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                headers.set("X-API-Key", apiKey);
            }

            HttpEntity<?> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    healthUrl,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.debug("Data Discovery AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Convert AI enhanced links back to LinkModel format, preserving the AI group information
     * in the ai:group property
     */
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
