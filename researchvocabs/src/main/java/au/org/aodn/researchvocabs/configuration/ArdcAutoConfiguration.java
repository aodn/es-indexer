package au.org.aodn.researchvocabs.configuration;

import au.org.aodn.researchvocabs.service.ArdcVocabsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Slf4j
@AutoConfiguration  // More expressive vs @Configuration
@ConditionalOnMissingBean(ArdcVocabsService.class)
public class ArdcAutoConfiguration {

    @Bean
    public ArdcVocabsService createArdcVocabsService() {
        log.info("Create ArdcVocabsService");
        return new ArdcVocabsService();
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
