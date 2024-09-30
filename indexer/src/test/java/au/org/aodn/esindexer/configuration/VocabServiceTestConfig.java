package au.org.aodn.esindexer.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImpl;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImplTest;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
public class VocabServiceTestConfig {
    /**
     * Create this bean for testing, once created the default auto config will not init the bean for you
     * @return - Mock bean
     */
    @Bean
    public ArdcVocabService createMockArdcVocabService() throws IOException {

        RestTemplate template = Mockito.mock(RestTemplate.class);
        RetryTemplate retryTemplate = new RetryTemplate();

        ArdcVocabServiceImplTest.setupPlatformMockRestTemplate(template);
        ArdcVocabServiceImplTest.setupParameterVocabMockRestTemplate(template);
        ArdcVocabServiceImplTest.setupOrganizationMockRestTemplate(template);

        return new ArdcVocabServiceImpl(template, retryTemplate);
    }
}
