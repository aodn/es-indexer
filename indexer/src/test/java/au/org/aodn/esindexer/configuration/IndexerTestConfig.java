package au.org.aodn.esindexer.configuration;

import au.org.aodn.ardcvocabs.model.ParameterVocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabsService;
import au.org.aodn.esindexer.BaseTestClass;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Configuration
public class IndexerTestConfig {

    /**
     * If you need to load mock data, you can consider do it here
     * @return Mock object of VocabsUtils
     */
    @Bean
    public ArdcVocabsService createArdcVocabsService() throws IOException {
        String json = BaseTestClass.readResourceFile("classpath:canned/aodn_discovery_parameter_vocab.json");
        List<ParameterVocabModel> parameterVocabs = (new ObjectMapper())
                .readValue(json, new TypeReference<List<ParameterVocabModel>>() {});

        ArdcVocabsService service = Mockito.mock(ArdcVocabsService.class);
        when(service.getParameterVocab(anyString()))
                .thenReturn(parameterVocabs);

        return service;
    }
}
