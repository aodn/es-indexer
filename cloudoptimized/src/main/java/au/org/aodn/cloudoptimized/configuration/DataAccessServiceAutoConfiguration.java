package au.org.aodn.cloudoptimized.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.cloudoptimized.service.DataAccessServiceImpl;
import au.org.aodn.cloudoptimized.service.GzipDefaultHeadersInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(DataAccessServiceAutoConfiguration.class)
public class DataAccessServiceAutoConfiguration {

    protected ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Bean
    @ConditionalOnMissingBean(DataAccessService.class)
    public DataAccessServiceImpl createDataAccessService(
            @Value("${dataaccess.host:http://localhost:5000}") String serverUrl,
            @Value("${dataaccess.baseUrl:/api/v1/das/}") String baseUrl,
            @Value("${dataaccess.apiKey:TEMP}") String apiKey,
            WebClient webClient){

        // A special rest template that turn on compression on both send and receive
        // it is important because cloud optimize is large
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(factory);
        // Add interceptor for default headers and GZIP compression
        restTemplate.getInterceptors().add(new GzipDefaultHeadersInterceptor(apiKey));

        return new DataAccessServiceImpl(serverUrl, baseUrl, restTemplate, webClient, objectMapper);
    }

    @Bean
    public WebClient createWebClient(@Value("${dataaccess.apiKey:TEMP}") String apiKey) {
        HttpHeaders defaultHeaders = new HttpHeaders();
        defaultHeaders.add("X-API-Key", apiKey);

        // Optional: Increase buffer limit if individual events are still too large
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    // TODO: The source should split it to multiple message
                    configurer.defaultCodecs().maxInMemorySize(350 * 1024 * 1024); // 350 MB
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                })
                .build();

        // Use WebClient for SSE
        return WebClient.builder()
                .defaultHeaders(headers -> headers.addAll(defaultHeaders)) // Set default headers
                .exchangeStrategies(strategies)
                .build();
    }
}
