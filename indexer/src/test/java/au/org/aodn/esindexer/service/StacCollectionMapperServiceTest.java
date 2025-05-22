package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.utils.GcmdKeywordUtils;
import au.org.aodn.esindexer.utils.GeometryUtils;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.StacCollectionModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
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
public class StacCollectionMapperServiceTest {

    protected ObjectMapper objectMapper = new ObjectMapper();
    protected JaxbUtils<MDMetadataType> jaxbUtils = new JaxbUtils<>(MDMetadataType.class);

    @MockitoBean
    protected GeoNetworkServiceImpl geoNetworkResourceService;

    @MockitoBean
    protected ElasticsearchClient portalElasticsearchClient;

    @MockitoBean
    protected ElasticSearchIndexService elasticSearchIndexService;

    @MockitoBean
    protected VocabService vocabsService;

    @MockitoBean
    protected RankingService rankingService;

    @MockitoBean
    protected GcmdKeywordUtils gcmdKeywordUtils;

    @MockitoBean
    protected IndexCloudOptimizedService indexCloudOptimizedService;

    @MockitoBean
    protected DataAccessService dataAccessService;

    @Autowired
    protected StacCollectionMapperService service;

    protected AtomicReference<IndexRequest<JsonData>> lastRequest = new AtomicReference<>();

    protected IndexerMetadataServiceImpl indexerService;

    @BeforeAll
    public static void preSetup() {
        GeometryUtils.setCoastalPrecision(0.05);
    }

    protected void verify(String expected) throws JsonProcessingException, JSONException {
        Map<?,?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String out = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        log.info(out);
        JSONAssert.assertEquals(
                objectMapper.readTree(expected).toPrettyString(),
                objectMapper.readTree(out.strip()).toPrettyString(),
                JSONCompareMode.STRICT
        );
    }

    public StacCollectionMapperServiceTest() throws JAXBException {
        GeometryUtils.init();
    }

    @AfterEach
    public void cleanUp() {
        Mockito.reset(
                geoNetworkResourceService,
                portalElasticsearchClient,
                elasticSearchIndexService,
                vocabsService
        );
    }

    @BeforeEach
    public void createIndexerService() throws IOException {
        indexerService = new IndexerMetadataServiceImpl(
                "any-works",
                "shingle_analyser",
                objectMapper,
                jaxbUtils,
                rankingService,
                geoNetworkResourceService,
                portalElasticsearchClient,
                elasticSearchIndexService,
                service,
                vocabsService,
                gcmdKeywordUtils
        );
        indexerService = spy(indexerService);

        // Number is 0, pretend fresh elastic instance
        when(elasticSearchIndexService.getDocumentsCount(anyString()))
                .thenReturn(0L);

        // Mock the protected method to return an empty string list
        doReturn(Collections.emptySet()).when(indexerService).extractTokensFromDescription(anyString());

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
                        .index((IndexRequest<?>)any(IndexRequest.class));

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

        when(indexCloudOptimizedService.hasIndex(eq("35234913-aa3c-48ec-b9a4-77f822f66ef8")))
                .thenReturn(true);

    }

    @Test
    public void verifyPointOfContactCorrect() throws IOException, JSONException {
        // We only index one record, the
        String xml = readResourceFile("classpath:canned/sample8.xml");
        String expected = readResourceFile("classpath:canned/sample8_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyMetadataContactCorrect() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample10.xml");
        String expected = readResourceFile("classpath:canned/sample10_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyLicenseCorrect() throws IOException, JSONException {

        // if license is in citation block, it should be extracted and added to the collection
        String xml1 = readResourceFile("classpath:canned/sample10.xml");
        String expected1 = readResourceFile("classpath:canned/sample10_stac.json");
        indexerService.indexMetadata(xml1);

        verify(expected1);

        // if license is not in citation block, it should try to find it in "other constraints"
        String xml2 = readResourceFile("classpath:canned/sample11.xml");
        String expected2 = readResourceFile("classpath:canned/sample11_stac.json");
        indexerService.indexMetadata(xml2);

        verify(expected2);

        // if both blocks all don't have license, it should return empty string
        String xml3 = readResourceFile("classpath:canned/sample7.xml");
        String expected3 = readResourceFile("classpath:canned/sample7_stac_no_es.json");
        indexerService.indexMetadata(xml3);

        verify(expected3);
    }

    @Test
    public void verifySummaryGeoCorrect1() throws IOException, JSONException {
        // We only index one record, the
        String xml = readResourceFile("classpath:canned/sample9.xml");
        String expected = readResourceFile("classpath:canned/sample9_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifySummaryGeoCorrect2() throws IOException, JSONException {
        // We only index one record, the
        String xml = readResourceFile("classpath:canned/sample10.xml");
        String expected = readResourceFile("classpath:canned/sample10_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyMultipleTemporal1Extents() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_multiple_temporal1.xml");
        String expected = readResourceFile("classpath:canned/sample_multiple_temporal1_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyMultipleTemporal2Extents() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_multiple_temporal2.xml");
        String expected = readResourceFile("classpath:canned/sample_multiple_temporal2_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyMultipleTemporalExtentsNull() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_multiple_temporal_null.xml");
        String expected = readResourceFile("classpath:canned/sample_multiple_temporal_null_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyNullKeywords() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/keywords_null.xml");
        String expected = readResourceFile("classpath:canned/keywords_null_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyNullAbstractResponsibility() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/abstract_resposibilty_null.xml");
        String expected = readResourceFile("classpath:canned/abstract_resposibilty_null_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyAbstractCIParty1() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample12.xml");
        String expected = readResourceFile("classpath:canned/sample12_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyAbstractResponsibilityNullWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample13.xml");
        String expected = readResourceFile("classpath:canned/sample13_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyTitleFreeThemes() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample14.xml");
        String expected = readResourceFile("classpath:canned/sample14_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * During polygon construction, there are times that the XML do not provide the proper dimension, 2D or 3D.
     * We assume it is 2D by default and here is the test case for that.
     *
     * @throws IOException - Do not expect to throw
     */
    @Test
    public void verifyPolygonMissingDimensionAttributeWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample15.xml");
        String expected = readResourceFile("classpath:canned/sample15_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyProviderNameNullWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample16.xml");
        String expected = readResourceFile("classpath:canned/sample16_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyCiRoleCodeNullWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample17.xml");
        String expected = readResourceFile("classpath:canned/sample17_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * This test repeat some test with the exception that when calling the dataservice, it return something
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyNotebookLink() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample17.xml");
        String expected = readResourceFile("classpath:canned/sample17_stac_notebook.json");

        when(dataAccessService.getNotebookLink(eq("0bef875d-5f77-4b31-bd56-de73fafc2b2e")))
                .thenReturn(Optional.of("https://nbviewer.org/github/aodn/aodn_cloud_optimised/blob/main/notebooks/autonomous_underwater_vehicle.ipynb"));

        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Metadata have geometry which result in a point and not a polygon
     *
     * @throws IOException - Do not expect to throw
     */
    @Test
    public void verifyHandleProjectionGeometry() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_incorrect_projection.xml");
        String expected = readResourceFile("classpath:canned/sample_incorrect_projection_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Metadata have geometry which result in a point and not a polygon
     *
     * @throws IOException - Do not expect to throw
     */
    @Test
    public void verifyAbstractCitationNullWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_abstract_citation_null.xml");
        String expected = readResourceFile("classpath:canned/sample_abstract_citation_null_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * The date field contains year only or year-month only. We need to handle this case.
     *
     * @throws IOException - Do not expect to throw
     */
    @Test
    public void verifyMalformDateTimeWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_malform_date.xml");
        String expected = readResourceFile("classpath:canned/sample_malform_date_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Sample contains invalid polygon where only x coor is valid but y is NaN
     *
     * @throws IOException - Do not expect to throw
     */
    @Test
    public void verifyMalformCoordinateWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_invalid_polygon.xml");
        String expected = readResourceFile("classpath:canned/sample_invalid_polygon.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyNonNodedIntersectionsWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_non_noded_intersections.xml");
        String expected = readResourceFile("classpath:canned/sample_non_noded_intersections_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * There is a missing enum publication, this case is check if we works after fix
     * @throws IOException - Not expect to throw
     * @throws JSONException - Not expect to throw
     */
    @Test
    public void verifyNoMissingGeonetworkFieldEnum() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_geoenum_publication.xml");
        String expected = readResourceFile("classpath:canned/sample_geoenum_publication_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Make sure we do not include empty polygon and cause the GeometryJson parse error
     * @throws IOException - Not expect to throw
     * @throws JSONException - Not expect to throw
     */
    @Test
    public void verifyNoJsonStringError() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample18.xml");
        String expected = readResourceFile("classpath:canned/sample18_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Verify if cloud optimized data indexed, we will have the assets attribute.
     * @throws IOException - Not expect to throw
     * @throws JSONException - Not expect to throw
     */
    @Test
    public void verifyHasIndex() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample.xml");
        String expected = readResourceFile("classpath:canned/dataservice/35234913-aa3c-48ec-b9a4-77f822f66ef8/sample.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
}
