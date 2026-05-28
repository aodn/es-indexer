package au.org.aodn.cloudoptimized.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.Mockito.mock;

@Configuration
@EnableRetry
public class DataAccessServiceTestConfig {

    @Bean
    public DataAccessServiceImpl createDataAccessService(
            RestTemplate restTemplate,
            WebClient webClient,
            ObjectMapper objectMapper){

        return new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                restTemplate,
                webClient,
                objectMapper);
    }

    @Bean
    public RestTemplate restTemplate() {
        return mock(RestTemplate.class);
    }

    @Bean
    public WebClient webClient() {
        return mock(WebClient.class);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
