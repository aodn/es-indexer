package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@ExtendWith(MockitoExtension.class)
public class PlatformVocabProcessorTest {

    @Mock
    RestTemplate mockRestTemplate;

    @InjectMocks
    protected VocabProcessorImpl vocabProcessor;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void verifyGetPlatformVocabs() throws IOException {
        // Create expect result
        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-platform-category-vocabulary/version-1-2/concept.json"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/platform_vocabs/vocab0.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-platform-category-vocabulary/version-1-2/concept.json?_page=1"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/platform_vocabs/vocab1.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-platform-vocabulary/version-6-1/concept.json"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/platform_vocabs/vocab_discovery0.json")));

        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(endsWith("/aodn-platform-vocabulary/version-6-1/resource.json?uri=http://vocab.aodn.org.au/def/platform/entity/116"), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/platform_vocabs/vocab_entity_116.json")));

        List<VocabModel> platformVocabs = vocabProcessor.getPlatformVocabs("");
        assertEquals("Total equals", 10, platformVocabs.size());
    }
}
