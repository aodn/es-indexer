package au.org.aodn.ardcvocabs.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class Config {
    @Bean("ardcObjectMapper")
    public ObjectMapper ardcObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

    @Bean("ardcVocabRestTemplate")
    @ConditionalOnMissingBean(name = "restTemplate")
    public RestTemplate ardcVocabRestTemplate() {
        return new RestTemplate();
    }
}
