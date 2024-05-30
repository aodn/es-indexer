package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.StacCollectionModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * This test case different from the others as it didn't create docker image to test the whole flow. It only mock the
 * component for speed testing.
 */
@Slf4j
@SpringBootTest(classes = {StacCollectionMapperServiceImpl.class})
public class StacCollectionMapperServiceTests {

    protected ObjectMapper objectMapper = new ObjectMapper();
    protected JaxbUtils<MDMetadataType> jaxbUtils = new JaxbUtils<>(MDMetadataType.class);

    @MockBean
    protected GeoNetworkServiceImpl geoNetworkResourceService;

    @MockBean
    protected ElasticsearchClient portalElasticsearchClient;

    @MockBean
    protected ElasticSearchIndexService elasticSearchIndexService;

    @MockBean
    protected AodnDiscoveryParameterVocabService aodnDiscoveryParameterVocabService;

    @MockBean
    protected RankingService rankingService;

    @Autowired
    protected StacCollectionMapperService service;

    protected AtomicReference<IndexRequest<JsonData>> lastRequest = new AtomicReference<>();

    protected IndexerServiceImpl indexerService;

    public StacCollectionMapperServiceTests() throws JAXBException {
    }

    @AfterEach
    public void cleanUp() {
        Mockito.reset(
                geoNetworkResourceService,
                portalElasticsearchClient,
                elasticSearchIndexService,
                aodnDiscoveryParameterVocabService
        );
    }

    @BeforeEach
    public void createIndexerService() throws IOException {
        indexerService = new IndexerServiceImpl(
                "any-works",
                objectMapper,
                jaxbUtils,
                rankingService,
                geoNetworkResourceService,
                portalElasticsearchClient,
                elasticSearchIndexService,
                service,
                aodnDiscoveryParameterVocabService
        );

        // Number is 0, pretend fresh elastic instance
        when(elasticSearchIndexService.getDocumentsCount(anyString()))
                .thenReturn(0L);

        doNothing()
                .when(elasticSearchIndexService)
                .createIndexFromMappingJSONFile(anyString(), anyString());

        doAnswer(ans -> {
            lastRequest.set(ans.getArgument(0));
            IndexResponse response = Mockito.mock(IndexResponse.class);

            when(response.version()).thenReturn(1L);
            when(response.toString()).thenReturn("");

            return response;
        })
                .when(portalElasticsearchClient)
                        .index(any(IndexRequest.class));

        doAnswer(ans -> {
            SearchResponse<ObjectNode> response = Mockito.mock();
            HitsMetadata<ObjectNode> metadata = Mockito.mock();
            TotalHits totalHits = Mockito.mock();

            when(totalHits.value()).thenReturn(1L);
            when(metadata.total()).thenReturn(totalHits);
            when(response.hits()).thenReturn(metadata);

            return response;
        })
                .when(portalElasticsearchClient)
                        .search(any(Function.class), eq(ObjectNode.class));

        // Pretend empty elastic
        when(geoNetworkResourceService.isMetadataRecordsCountLessThan(anyInt()))
                .thenReturn(Boolean.TRUE);

        when(geoNetworkResourceService.searchRecordBy(anyString()))
                .thenReturn("");

        when(rankingService.evaluateCompleteness(any(StacCollectionModel.class)))
                .thenReturn(1);

    }

    @Test
    public void verifyPointOfContactCorrect() throws IOException, JAXBException {
        // We only index one record, the
        String xml = readResourceFile("classpath:canned/sample8.xml");
        String expected = readResourceFile("classpath:canned/sample8_stac.json");
        indexerService.indexMetadata(xml);

        // We use a mock to pretend insert value into Elastic, there we store what is being send to elastic
        // and now we can use it to compare expected result.
        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        assertEquals("Stac equals", objectMapper.readTree(expected), objectMapper.readTree(out.strip()));
    }
}
