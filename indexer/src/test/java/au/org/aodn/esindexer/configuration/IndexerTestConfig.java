package au.org.aodn.esindexer.configuration;

import au.org.aodn.researchvocabs.model.CategoryVocabModel;
import au.org.aodn.researchvocabs.service.ArdcVocabsService;
import au.org.aodn.esindexer.BaseTestClass;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
        List<CategoryVocabModel> categoryVocabModels = (new ObjectMapper())
                .readValue(json, new TypeReference<List<CategoryVocabModel>>() {});

        ArdcVocabsService service = Mockito.mock(ArdcVocabsService.class);
        when(service.getParameterCategory(anyString()))
                .thenReturn(categoryVocabModels);

        return service;
    }
}
