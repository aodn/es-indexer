package au.org.aodn.researchvocabs.configuration;

import au.org.aodn.researchvocabs.service.GcmdKeywordsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Slf4j
@AutoConfiguration  // More expressive vs @Configuration
@ConditionalOnMissingBean(GcmdKeywordsService.class)
public class GcmdAutoConfiguration {

    @Bean
    public GcmdKeywordsService createArdcVocabsService() {
        log.info("Create GcmdVocabsService");
        return new GcmdKeywordsService();
    }
    /**
     * In case the one who use this lib have not created it.
     * @return RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate gcmdVocabRestTemplate() {
        return new RestTemplate();
    }
}
