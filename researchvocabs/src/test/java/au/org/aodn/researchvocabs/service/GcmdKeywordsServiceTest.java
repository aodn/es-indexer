package au.org.aodn.researchvocabs.service;

import au.org.aodn.researchvocabs.model.GcmdKeywordModel;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@ExtendWith(MockitoExtension.class)
class GcmdKeywordsServiceTest {

    @Mock
    private RestTemplate mockRestTemplate;

    @InjectMocks
    private GcmdKeywordsService gcmdKeywordsService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testGetGcmdKeywords() throws Exception {
        // Create expect result
        Mockito.when(mockRestTemplate.<ObjectNode>getForObject(anyString(), any(), any(Object[].class)))
                .thenReturn((ObjectNode)objectMapper.readTree(ResourceUtils.getFile("classpath:databag/gcmd_keywords_api_response.json")));

        List<GcmdKeywordModel> categoryVocabModelList = gcmdKeywordsService.getGcmdKeywords("");

        assertEquals("Total equals", 33, categoryVocabModelList.size());
    }
}
