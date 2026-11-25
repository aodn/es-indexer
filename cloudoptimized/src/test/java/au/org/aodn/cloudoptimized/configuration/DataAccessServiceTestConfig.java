package au.org.aodn.cloudoptimized.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessServiceImpl;
import au.org.aodn.cloudoptimized.service.NotebookUrlCheck;
import au.org.aodn.cloudoptimized.service.NotebookUrlValidator;
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
            ObjectMapper objectMapper,
            NotebookUrlValidator notebookUrlValidator){

        return new DataAccessServiceImpl(
                "http://localhost",
                "/api",
                restTemplate,
                webClient,
                objectMapper,
                notebookUrlValidator);
    }

    @Bean
    public NotebookUrlValidator notebookUrlValidator() {
        // Always valid, so the test never sends a request to the notebook host
        return url -> NotebookUrlCheck.ok();
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
