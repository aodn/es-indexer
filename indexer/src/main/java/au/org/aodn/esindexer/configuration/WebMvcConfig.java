package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.service.VocabService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
@EnableCaching
@EnableScheduling
public class WebMvcConfig {

    @Autowired
    protected ObjectMapper indexerObjectMapper;

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                VocabService.VocabType.Names.AODN_DISCOVERY_PARAMETER_VOCABS,
                VocabService.VocabType.Names.AODN_PLATFORM_VOCABS,
                VocabService.VocabType.Names.AODN_ORGANISATION_VOCABS
        );
    }

    @PostConstruct
    public void init() {
        JavaTimeModule module = new JavaTimeModule();

        // Avoid output date-time string become number
        indexerObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        indexerObjectMapper.registerModule(module);
    }

    @Bean("indexerRestTemplate")
    public RestTemplate indexerRestTemplate() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.parseMediaType("text/plain;charset=utf-8"), MediaType.APPLICATION_OCTET_STREAM));

        final RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(converter);

        return restTemplate;
    }
}
