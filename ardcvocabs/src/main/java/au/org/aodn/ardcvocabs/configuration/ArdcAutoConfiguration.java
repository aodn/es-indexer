package au.org.aodn.ardcvocabs.configuration;

import au.org.aodn.ardcvocabs.service.OrganisationVocabsService;
import au.org.aodn.ardcvocabs.service.ParameterVocabsService;
import au.org.aodn.ardcvocabs.service.PlatformVocabsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Slf4j
@AutoConfiguration  // More expressive vs @Configuration
@ConditionalOnMissingBean(ParameterVocabsService.class)
public class ArdcAutoConfiguration {

    @Bean
    public ParameterVocabsService createParameterVocabsService() {
        log.info("Create ParameterVocabsService");
        return new ParameterVocabsService();
    }

    @Bean
    public PlatformVocabsService createPlatformVocabsService() {
        log.info("Create PlatformVocabsService");
        return new PlatformVocabsService();
    }

    @Bean
    public OrganisationVocabsService createOrganisationVocabsService() {
        log.info("Create OrganisationVocabsService");
        return new OrganisationVocabsService();
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
