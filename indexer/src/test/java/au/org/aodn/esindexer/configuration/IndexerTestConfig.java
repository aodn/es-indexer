package au.org.aodn.esindexer.configuration;

import au.org.aodn.ardcvocabs.model.VocabModel;
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
    // TODO: fix this
//    /**
//     * If you need to load mock data, you can consider do it here
//     * @return Mock object of VocabsUtils
//     */
//    @Bean
//    public VocabProcessor createVocabProcessor() throws IOException {
//        String json = BaseTestClass.readResourceFile("classpath:canned/aodn_discovery_parameter_vocabs.json");
//        List<VocabModel> parameterVocabs = (new ObjectMapper())
//                .readValue(json, new TypeReference<List<VocabModel>>() {});
//
//        VocabProcessor service = Mockito.mock(VocabProcessor.class);
//        when(service.getParameterVocabs(anyString()))
//                .thenReturn(parameterVocabs);
//
//        return service;
//    }
//
//    @Bean
//    public VocabProcessor createVocabProcessor() throws IOException {
//        String json = BaseTestClass.readResourceFile("classpath:canned/aodn_platform_vocabs.json");
//        List<VocabModel> platformVocabs = (new ObjectMapper())
//                .readValue(json, new TypeReference<List<VocabModel>>() {});
//
//        VocabProcessor service = Mockito.mock(VocabProcessor.class);
//        when(service.getPlatformVocabs(anyString()))
//                .thenReturn(platformVocabs);
//
//        return service;
//    }
//
//    @Bean
//    public VocabProcessor createVocabProcessor() throws IOException {
//        String json = BaseTestClass.readResourceFile("classpath:canned/aodn_organisation_vocabs.json");
//        List<VocabModel> organisationVocabs = (new ObjectMapper())
//                .readValue(json, new TypeReference<List<VocabModel>>() {});
//
//        VocabProcessor service = Mockito.mock(VocabProcessor.class);
//        when(service.getOrganisationVocabs(anyString()))
//                .thenReturn(organisationVocabs);
//
//        return service;
//    }
}
