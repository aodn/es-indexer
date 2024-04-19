package au.org.aodn.esindexer.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArdcVocabsConfig {
    @Bean
    public ArdcVocabsService ardcVocabsService() {
        return new ArdcVocabsService();
    }
}
