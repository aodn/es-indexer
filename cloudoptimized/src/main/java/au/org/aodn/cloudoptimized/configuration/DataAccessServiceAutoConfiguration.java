package au.org.aodn.cloudoptimized.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.cloudoptimized.service.DataAccessServiceImpl;
import au.org.aodn.cloudoptimized.service.GzipDefaultHeadersInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(DataAccessServiceAutoConfiguration.class)
public class DataAccessServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataAccessService.class)
    public DataAccessServiceImpl createDataAccessService(
            @Value("${dataaccess.host:http://localhost:5000}") String serverUrl,
            @Value("${dataaccess.baseUrl:/api/v1/das/}") String baseUrl,
            @Value("${dataaccess.apiKey:TEMP}") String apiKey){

        // A special rest template that turn on compression on both send and receive
        // it is important because cloud optimize is large
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(factory);
        // Add interceptor for default headers and GZIP compression
        restTemplate.getInterceptors().add(new GzipDefaultHeadersInterceptor(apiKey));

        return new DataAccessServiceImpl(serverUrl, baseUrl, restTemplate);
    }
}
