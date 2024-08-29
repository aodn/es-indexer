package au.org.aodn.ardcvocabs.configuration;

import au.org.aodn.ardcvocabs.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Slf4j
@AutoConfiguration  // More expressive vs @Configuration
public class ArdcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(VocabProcessorImpl.class)
    public VocabProcessorImpl createVocabProcessor() {
        log.info("Create VocabProcessor bean");
        return new VocabProcessorImpl();
    }

    /**
     * In case the one who use this lib have not created it.
     * @return RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate vocabRestTemplate() {
        return new RestTemplate();
    }
}
