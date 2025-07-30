package au.org.aodn.datadiscoveryai.configuration;

import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiServiceImpl;
import au.org.aodn.datadiscoveryai.service.GzipRequestResponseInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(DataDiscoveryAiAutoConfiguration.class)
public class DataDiscoveryAiAutoConfiguration {
    protected ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Bean
    @ConditionalOnMissingBean(DataDiscoveryAiService.class)
    public DataDiscoveryAiServiceImpl createDataDiscoveryAiService(
            @Value("${datadiscoveryai.host}") String serviceUrl,
            @Value("${datadiscoveryai.baseUrl}") String baseUrl,
            @Value("${datadiscoveryai.apiKey}") String apiKey,
            @Value("${datadiscoveryai.internalAiHeaderSecret}") String internalKey,
            @Qualifier("dataDiscoveryAiWebClient") WebClient  webClient) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30 seconds
        factory.setReadTimeout(60000);    // 60 seconds

        RestTemplate restTemplate = new RestTemplate(factory);
        // Add GZIP interceptor
        restTemplate.setInterceptors(Collections.singletonList(new GzipRequestResponseInterceptor(apiKey,internalKey)));

        return new DataDiscoveryAiServiceImpl(serviceUrl, baseUrl, restTemplate, webClient, objectMapper);
    }

    @Bean("dataDiscoveryAiWebClient")
    public WebClient createAIWebClient(@Value("${datadiscoveryai.apiKey:TEMP}") String apiKey) {
        HttpHeaders defaultHeaders = new HttpHeaders();
        defaultHeaders.add("X-API-Key", apiKey);

        // Use WebClient for SSE
        return WebClient.builder()
                .defaultHeaders(headers -> headers.addAll(defaultHeaders)) // Set default headers
                .build();
    }
}
