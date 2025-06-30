package au.org.aodn.datadiscoveryai.configuration;

import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiServiceImpl;
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

    @Bean
    @ConditionalOnMissingBean(DataDiscoveryAiService.class)
    public DataDiscoveryAiServiceImpl createDataDiscoveryAiService(
            @Value("${datadiscoveryai.host}") String serviceUrl,
            @Value("${datadiscoveryai.baseUrl}") String baseUrl,
            @Value("${datadiscoveryai.apiKey}") String apiKey) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30 seconds
        factory.setReadTimeout(60000);    // 60 seconds

        RestTemplate restTemplate = new RestTemplate(factory);

        return new DataDiscoveryAiServiceImpl(serviceUrl, baseUrl, apiKey, restTemplate);
    }
}
