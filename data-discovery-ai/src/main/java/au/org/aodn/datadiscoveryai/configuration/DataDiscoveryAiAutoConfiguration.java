package au.org.aodn.datadiscoveryai.configuration;

import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiServiceImpl;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
            @Value("${datadiscoveryai.apiKey}") String apiKey) {

        // Create a simple RestTemplate for API calls
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30 seconds
        factory.setReadTimeout(60000);    // 60 seconds
        
        RestTemplate restTemplate = new RestTemplate(factory);

        log.info("Configuring Data Discovery AI service with URL: {}{}", serviceUrl, baseUrl);

        return new DataDiscoveryAiServiceImpl(serviceUrl, baseUrl, apiKey, restTemplate, objectMapper);
    }
} 
