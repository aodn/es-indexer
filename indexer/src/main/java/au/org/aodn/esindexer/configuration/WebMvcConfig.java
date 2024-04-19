package au.org.aodn.esindexer.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
@ComponentScan("au.org.aodn.ardcvocabs")
@EnableCaching
@EnableScheduling
public class WebMvcConfig {

    @Autowired
    protected ObjectMapper indexObjectMapper;

    @Autowired
    protected ArdcVocabsService ardcVocabsService;

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager(AppConstants.AODN_DISCOVERY_CATEGORIES_CACHE);
    }

    @PostConstruct
    public void init() {
        JavaTimeModule module = new JavaTimeModule();

        // Avoid output date-time string become number
        indexObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        indexObjectMapper.registerModule(module);
    }

    @Bean("indexerRestTemplate")
    public RestTemplate indexerRestTemplate() {
        final RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(jacksonSupportedTypes());
        return restTemplate;
    }

    private HttpMessageConverter jacksonSupportedTypes() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.parseMediaType("text/plain;charset=utf-8"), MediaType.APPLICATION_OCTET_STREAM));
        return converter;
    }
}
