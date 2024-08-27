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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;
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
    protected ArdcVocabsService ardcVocabsService;

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
                ardcVocabsService
        );
    }

    @BeforeEach
    public void createIndexerService() throws IOException {
        indexerService = new IndexerServiceImpl(
                "any-works",
                "shingle_analyser",
                objectMapper,
                jaxbUtils,
                rankingService,
                geoNetworkResourceService,
                portalElasticsearchClient,
                elasticSearchIndexService,
                service,
                ardcVocabsService
        );
        indexerService = spy(indexerService);

        // Number is 0, pretend fresh elastic instance
        when(elasticSearchIndexService.getDocumentsCount(anyString()))
                .thenReturn(0L);

        // Mock the protected method to return an empty string list
        doReturn(Collections.emptyList()).when(indexerService).extractTokensFromDescription(anyString());

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
        Assertions.assertEquals(objectMapper.readTree(expected), objectMapper.readTree(out.strip()), "Stac not equals for sample 8");
    }

    @Test
    public void verifyMetadataContactCorrect() throws IOException {
        String xml = readResourceFile("classpath:canned/sample10.xml");
        String expected = readResourceFile("classpath:canned/sample10_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(objectMapper.readTree(expected), objectMapper.readTree(out.strip()), "Stac not equals for sample 10");
    }

    @Test
    public void verifyLicenseCorrect() throws IOException {

        // if license is in citation block, it should be extracted and added to the collection
        String xml1 = readResourceFile("classpath:canned/sample10.xml");
        String expected1 = readResourceFile("classpath:canned/sample10_stac.json");
        indexerService.indexMetadata(xml1);
        Map<?,?> content1 = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out1 = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content1);
        Assertions.assertEquals(objectMapper.readTree(expected1), objectMapper.readTree(out1.strip()), "Stac not equals for sample 10");

        // if license is not in citation block, it should try to find it in "other constraints"
        String xml2 = readResourceFile("classpath:canned/sample11.xml");
        String expected2 = readResourceFile("classpath:canned/sample11_stac.json");
        indexerService.indexMetadata(xml2);
        Map<?,?> content2 = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out2 = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content2);
        Assertions.assertEquals(objectMapper.readTree(expected2), objectMapper.readTree(out2.strip()), "Stac not equals for sample 11");

        // if both blocks all don't have license, it should return empty string
        String xml3 = readResourceFile("classpath:canned/sample7.xml");
        String expected3 = readResourceFile("classpath:canned/sample7_stac_no_es.json");
        indexerService.indexMetadata(xml3);
        var b = lastRequest.get().document();
        var a = lastRequest.get().document().toString();
        Map<?,?> content3 = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out3 = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content3);
        Assertions.assertEquals(objectMapper.readTree(expected3), objectMapper.readTree(out3.strip()), "Stac not equals for sample 7");
    }

    @Test
    public void verifySummaryGeoCorrect1() throws IOException, JAXBException {
        // We only index one record, the
        String xml = readResourceFile("classpath:canned/sample9.xml");
        String expected = readResourceFile("classpath:canned/sample9_stac.json");
        indexerService.indexMetadata(xml);

        // We use a mock to pretend insert value into Elastic, there we store what is being send to elastic
        // and now we can use it to compare expected result.
        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(objectMapper.readTree(expected), objectMapper.readTree(out.strip()), "Stac not equals for sample 9");
    }

    @Test
    public void verifySummaryGeoCorrect2() throws IOException {
        // We only index one record, the
        String xml = readResourceFile("classpath:canned/sample10.xml");
        String expected = readResourceFile("classpath:canned/sample10_stac.json");
        indexerService.indexMetadata(xml);

        // We use a mock to pretend insert value into Elastic, there we store what is being send to elastic
        // and now we can use it to compare expected result.
        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(objectMapper.readTree(expected), objectMapper.readTree(out.strip()), "Stac not equals for sample 10");
    }

    @Test
    public void verifyMultipleTemporal1Extents() throws IOException {
        String xml = readResourceFile("classpath:canned/sample_multiple_temporal1.xml");
        String expected = readResourceFile("classpath:canned/sample_multiple_temporal1_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        log.info(out);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for sample_multiple_temporal1_stac"
        );
    }

    @Test
    public void verifyMultipleTemporal2Extents() throws IOException {
        String xml = readResourceFile("classpath:canned/sample_multiple_temporal2.xml");
        String expected = readResourceFile("classpath:canned/sample_multiple_temporal2_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for sample_multiple_temporal2_stac"
        );
    }

    @Test
    public void verifyMultipleTemporalExtentsNull() throws IOException {
        String xml = readResourceFile("classpath:canned/sample_multiple_temporal_null.xml");
        String expected = readResourceFile("classpath:canned/sample_multiple_temporal_null_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for sample_multiple_temporal_null_stac"
        );
    }

    @Test
    public void verifyNullKeywords() throws IOException {
        String xml = readResourceFile("classpath:canned/keywords_null.xml");
        String expected = readResourceFile("classpath:canned/keywords_null_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for keywords_null"
        );
    }

    @Test
    public void verifyNullAbstractResponsibility() throws IOException {
        String xml = readResourceFile("classpath:canned/abstract_resposibilty_null.xml");
        String expected = readResourceFile("classpath:canned/abstract_resposibilty_null_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for keywords_null of sample12"
        );
    }
    /**
     * TODO: Havier -
     * @throws IOException
     */
    @Test
    public void verifyAbstractCIParty1() throws IOException {
        String xml = readResourceFile("classpath:canned/sample12.xml");
        String expected = readResourceFile("classpath:canned/sample12_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for sample12"
        );
    }

    @Test
    public void verifyAbstractResponsibilityNullWorks() throws IOException {
        String xml = readResourceFile("classpath:canned/sample13.xml");
        String expected = readResourceFile("classpath:canned/sample13_stac.json");
        indexerService.indexMetadata(xml);

        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        Assertions.assertEquals(
                objectMapper.readTree(expected),
                objectMapper.readTree(out.strip()),
                "Stac not equals for sample13"
        );
    }
}
