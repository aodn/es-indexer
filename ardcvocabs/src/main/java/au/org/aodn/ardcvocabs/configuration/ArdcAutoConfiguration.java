package au.org.aodn.ardcvocabs.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Slf4j
@AutoConfiguration  // More expressive vs @Configuration
@ConditionalOnMissingBean(ArdcVocabService.class)
public class ArdcAutoConfiguration {

    @Bean
    public ArdcVocabService createArdcVocabsService(RestTemplate restTemplate) {
        log.info("Create ArdcVocabsService");
        return new ArdcVocabServiceImpl(restTemplate);
    }
    /**
     * In case the one who use this lib have not created it.
     * @return RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate ardcVocabRestTemplate() {
        return new RestTemplate();
    }
}
