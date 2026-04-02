package au.org.aodn.esindexer.service;

import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.datadiscoveryai.model.AiEnhancementRequest;
import au.org.aodn.datadiscoveryai.model.AiEnhancementResponse;
import au.org.aodn.datadiscoveryai.service.DataDiscoveryAiService;
import au.org.aodn.esindexer.utils.GcmdKeywordUtils;
import au.org.aodn.esindexer.utils.GeometryUtils;
import au.org.aodn.esindexer.utils.JaxbUtils;
import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import au.org.aodn.metadata.iso19115_3_2018.MDMetadataType;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.stac.model.ThemesModel;
import au.org.aodn.stac.model.AssetModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.springframework.test.util.ReflectionTestUtils;

import static au.org.aodn.esindexer.BaseTestClass.readResourceFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * This test case different from the others as it didn't create docker image to test the whole flow. It only mock the
 * component for speed testing.
 */
@Slf4j
@ActiveProfiles("test")
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

    @MockitoBean
    protected DataDiscoveryAiService dataDiscoveryAiService;

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
                gcmdKeywordUtils,
                dataDiscoveryAiService
        );
        indexerService = spy(indexerService);
        indexerService.self = indexerService;

        // Number is 0, pretend fresh elastic instance
        when(elasticSearchIndexService.getDocumentsCount(anyString()))
                .thenReturn(0L);

        // Mock the protected method to return an empty string list
        doReturn(Collections.emptySet()).when(indexerService).extractTokensFromDescription(anyString(), anyString());

        doNothing()
                .when(elasticSearchIndexService)
                .recreateIndexFromMappingJSONFile(anyString(), anyString());

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

        when(indexCloudOptimizedService.getHitId(eq("35234913-aa3c-48ec-b9a4-77f822f66ef8")))
                .thenReturn("8af21108-c535-43bf-8dab-c1f45a26088c|vessel_trv_realtime_qc.parquet|2009-08|0");

        MetadataEntity mockMetadata = new MetadataEntity();
        mockMetadata.setUuid("35234913-aa3c-48ec-b9a4-77f822f66ef8");
        String mockDname = "vessel_xbt_realtime_nonqc.parquet";
        mockMetadata.setDname(mockDname);
        when(dataAccessService.getMetadataByUuid(eq("35234913-aa3c-48ec-b9a4-77f822f66ef8")))
                .thenReturn(Map.of(mockDname, mockMetadata));
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
    public void verifyProtocolExtractionCorrect() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample8.xml");
        String expected = readResourceFile("classpath:canned/sample8_stac.json");
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
    public void verifyTemporalWithTrailingZ() {
        // "2014-12-31T00:00:00Z" is already UTC, should be returned as-is
        String startResult = ReflectionTestUtils.invokeMethod(service, "convertDateToZonedDateTime", "test-uuid", "2014-12-31T00:00:00Z", true);
        assertEquals("2014-12-31T00:00:00Z", startResult);

        String endResult = ReflectionTestUtils.invokeMethod(service, "convertDateToZonedDateTime", "test-uuid", "2014-12-31T00:00:00Z", false);
        assertEquals("2014-12-31T00:00:00Z", endResult);
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
    /**
     * This XML contains an invalid value in the EAST point of the bounding box, it is set to 360, which is invalid value.
     * Max value allow in this case is 180, so code to set it back to 180 if exceed 180
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyBBoxEastInvalidValueWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample20.xml");
        String expected = readResourceFile("classpath:canned/sample20_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Handle null exception in this case
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyAbstractConstraintsNullWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample21.xml");
        String expected = readResourceFile("classpath:canned/sample21_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Handle case self intersect, code should manage the polygon correctly without problem
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyNoSelfIntersect1() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_self_intersect1.xml");
        String expected = readResourceFile("classpath:canned/sample_self_intersect1_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Handle another self intersect case
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyNoSelfIntersect2() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_self_intersect2.xml");
        String expected = readResourceFile("classpath:canned/sample_self_intersect2_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Handle null exception in this case
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyEmptyLinkName() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_empty_link_name.xml");
        String expected = readResourceFile("classpath:canned/sample_empty_link_name_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Handle null exception in this case
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyVersionedCitationWorks() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample22.xml");
        String expected = readResourceFile("classpath:canned/sample22_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyHandleNullCorrectly() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample23.xml");
        String expected = readResourceFile("classpath:canned/sample23_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }
    /**
     * Verify dataset_group allow multiple group for same dataset
     * @throws IOException - Not expected
     * @throws JSONException - Not expected
     */
    @Test
    public void verifyHandleMultipleGroupCorrectly() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample24.xml");
        String expected = readResourceFile("classpath:canned/sample24_stac.json");

        when(geoNetworkResourceService.findGroupById("b9bf6b57-54a0-44b3-bd17-30ccfb2b246f"))
                .thenReturn("group1, group 2, group3");

        indexerService.indexMetadata(xml);

        verify(expected);
    }

    @Test
    public void verifyAbnormalStructureXmlWork() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/sample_abnormal_structure_GA.xml");
        String expected = readResourceFile("classpath:canned/sample_abnormal_structure_GA_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);
    }

    /**
     * Verify GCMD citation descriptions are excluded from theme concepts.
     * The test sample (abstract_resposibilty_null.xml) contains GCMD citation in its theme descriptions,
     * and the expected output should have empty descriptions for those concepts.
     */
    @Test
    public void verifyGcmdCitationDescriptionExcluded() throws IOException, JSONException {
        String xml = readResourceFile("classpath:canned/abstract_resposibilty_null.xml");
        String expected = readResourceFile("classpath:canned/abstract_resposibilty_null_stac.json");
        indexerService.indexMetadata(xml);

        verify(expected);

        // additionally verify that GCMD descriptions are empty
        Map<?, ?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        List<Map<String, Object>> themes = (List<Map<String, Object>>) content.get("themes");
        for (Map<String, Object> theme : themes) {
            List<Map<String, Object>> concepts = (List<Map<String, Object>>) theme.get("concepts");
            for (Map<String, Object> concept : concepts) {
                String description = (String) concept.get("description");
                Assertions.assertFalse(
                        description != null && description.contains("NASA/Global Change Master Directory"),
                        "GCMD citation should be excluded from concept description"
                );
            }
        }
    }

    /**
     * Test case when AI service is available and returns enhanced fields including: links, description, update frequency, and themes
     * This test verifies that while AI enhancement for link grouping, description formatting, and delivery mode classification always being called,
     * AI keyword classification only called when original themes don't have parameter or GCMD vocab, and ai:parameter_vocabs are properly set
     * @throws IOException - Not expected
     */
    @Test
    public void verifyAiServiceEnhancementWithKeywordClassification() throws IOException, JSONException {
        // Mock the enhanced links returned by AI service
        List<LinkModel> enhancedLinks = Arrays.asList(
                LinkModel.builder()
                        .href("http://www.fish.wa.gov.au/docs/pub/ResProject/stockassessment/project07.php?0405")
                        .rel("related")
                        .type("text/html")
                        .title("{\"title\":\"Project summary - Recreational Fisheries Databases\",\"description\":\"Project summary - Recreational Fisheries Databases\"}")
                        .aiGroup("Document")
                        .build(),
                //there are some links that are not enhanced by AI
                LinkModel.builder()
                        .href("https://geonetwork.edge.aodn.org.au:443/geonetwork/images/logos/2f850269-0bdc-4491-80b0-f837c7eff6e3.png")
                        .rel("icon")
                        .type("image/png")
                        .title("Suggest icon for dataset")
                        .build(),
                LinkModel.builder()
                        .href("https://catalogue.aodn.org.au:443/geonetwork/srv/api/records/516811d7-cce1-207a-e0440003ba8c79dd/attachments/NWMRI_s.png")
                        .rel("preview")
                        .type("image")
                        .build(),
                LinkModel.builder()
                        .href("https://catalogue.aodn.org.au:443/geonetwork/srv/api/records/516811d7-cce1-207a-e0440003ba8c79dd")
                        .rel("describedby")
                        .type("text/html")
                        .title("Full metadata link")
                        .build()
        );

        // Mock the AI enhanced description
        String enhancedDescription = "The section manages recreational fishing information obtained from ongoing provision of data from Angling Club Field Day Records Book, Angler's Log Book, surveys completed by Fisheries Officers and VFLO surveys. Information is now obtained from licenced charter boat operators who have been providing returns since September 2001.\n\nThe section also monitors the general public participation rate and satisfaction with recreational fishing and awareness of the Department's various roles annually based on a phone survey. Similarly, information on recreational catch and fishing effort for the abalone, marron, SW freshwater angling and rock lobster fisheries is collected and reported using an annual telephone survey of licence holders.\n\nTime: ongoing.";

        // Mock the AI enhanced update frequency
        String enhancedUpdateFrequency = "other";

        // Mock the AI predicted themes - these have ai:description so they are AI enhanced
        List<ThemesModel> enhancedThemes = List.of(
                ThemesModel.builder()
                        .scheme("theme")
                        .concepts(List.of(
                                ConceptModel.builder()
                                        .id("Biotic taxonomic identification")
                                        .url("http://vocab.aodn.org.au/def/discovery_parameter/entity/489")
                                        .title("AODN Discovery Parameter Vocabulary")
                                        .description("")
                                        .aiDescription("This is the prediction provided by AI model.")
                                        .build(),
                                ConceptModel.builder()
                                        .id("Abundance of biota")
                                        .url("http://vocab.aodn.org.au/def/discovery_parameter/entity/488")
                                        .title("AODN Discovery Parameter Vocabulary")
                                        .description("")
                                        .aiDescription("This is the prediction provided by AI model.")
                                        .build()
                        ))
                        .build(),
                ThemesModel.builder()
                        .scheme("theme")
                        .concepts(List.of(
                                ConceptModel.builder()
                                        .id("diver")
                                        .url("http://vocab.nerc.ac.uk/collection/L06/current/72")
                                        .title("AODN Platform Vocabulary")
                                        .description("")
                                        .aiDescription("This is the prediction provided by AI model.")
                                        .build()
                        ))
                        .build()
        );

        // Create mock AI response
        AiEnhancementResponse mockAiResponse = Mockito.mock(AiEnhancementResponse.class);

        // Set up AI service to be available and mock the combined enhancement
        when(dataDiscoveryAiService.isServiceAvailable()).thenReturn(true);
        when(dataDiscoveryAiService.enhanceWithAi(any(AiEnhancementRequest.class)))
                .thenReturn(mockAiResponse);
        when(dataDiscoveryAiService.getEnhancedLinks(eq(mockAiResponse))).thenReturn(enhancedLinks);
        when(dataDiscoveryAiService.getEnhancedDescription(eq(mockAiResponse))).thenReturn(enhancedDescription);
        when(dataDiscoveryAiService.getEnhancedUpdateFrequency(eq(mockAiResponse))).thenReturn(enhancedUpdateFrequency);
        // Mock enhanced themes - only returned when keyword classification is called
        when(dataDiscoveryAiService.getEnhancedThemes(eq(mockAiResponse))).thenReturn(enhancedThemes);
        // Mock vocab extract
        when(vocabsService.extractVocabLabelsFromThemes(
                anyList(),
                eq(VocabService.VocabType.AODN_DISCOVERY_PARAMETER_VOCABS),
                anyBoolean()))
                .thenReturn(Set.of("ocean biota"));
        when(vocabsService.extractVocabLabelsFromThemes(
                anyList(),
                eq(VocabService.VocabType.AODN_PLATFORM_VOCABS),
                anyBoolean()))
                .thenReturn(Set.of("diver"));

        String xml = readResourceFile("classpath:canned/aienhancement/sample_need_keyword_enhancement.xml");
        String expected = readResourceFile("classpath:canned/aienhancement/sample_need_keyword_enhancement.json");

        indexerService.indexMetadata(xml);

        // Verify getEnhancedThemes was called exactly once
        Mockito.verify(dataDiscoveryAiService, times(1)).getEnhancedThemes(eq(mockAiResponse));

        Map<?, ?> content = objectMapper.readValue(lastRequest.get().document().toString(), Map.class);
        String actual = objectMapper.writeValueAsString(content);

        JsonNode actualSummaries = objectMapper.readTree(actual).get("summaries");
        JsonNode expectedSummaries = objectMapper.readTree(expected).get("summaries");

        JSONAssert.assertEquals(
                expectedSummaries.get("ai:parameter_vocabs").toString(),
                actualSummaries.get("ai:parameter_vocabs").toString(),
                JSONCompareMode.STRICT
        );
        JSONAssert.assertEquals(
                expectedSummaries.get("ai:platform_vocabs").toString(),
                actualSummaries.get("ai:platform_vocabs").toString(),
                JSONCompareMode.STRICT
        );
    }

    /**
     * Test case when AI service is available and returns enhanced links, enhanced description, and enhanced update frequency
     * This test verifies that both AI link grouping, AI description enhancement, AI delivery mode models are properly called and applied
     * during the indexing process in the service layer
     * @throws IOException - Not expected
     */
    @Test
    public void verifyAiServiceEnhancementWithoutKeywordClassification() throws IOException, JSONException {
        // Mock the enhanced links returned by AI service
        List<LinkModel> enhancedLinks = Arrays.asList(
                LinkModel.builder()
                        .href("https://www.marine.csiro.au/data/trawler/survey_details.cfm?survey=IN2024_V01")
                        .rel("data")
                        .type("")
                        .title("{\"title\":\"MNF Data Trawler\",\"description\":\"Link to processed data and survey information (plans, summaries, etc.) via MNF Data Trawler\"}")
                        .aiGroup("Data Access")
                        .build(),
                LinkModel.builder()
                        .href("https://mnf.csiro.au/")
                        .rel("related")
                        .type("text/html")
                        .title("{\"title\":\"Marine National Facility\",\"description\":\"Link to the Marine National Facility Webpage\"}")
                        .aiGroup("Other")
                        .build(),
                LinkModel.builder()
                        .href("https://doi.org/10.25919/rdrt-bd71")
                        .rel("data")
                        .type("")
                        .title("{\"title\":\"Data Access Portal (DOI)\",\"description\":\"Link to this record at the CSIRO Data Access Portal\"}")
                        .aiGroup("Data Access")
                        .build(),
                LinkModel.builder()
                        .href("http://www.marine.csiro.au/data/underway/?survey=IN2024_V01")
                        .rel("data")
                        .type("")
                        .title("{\"title\":\"Underway Visualisation Tool\",\"description\":\"Link to visualisation tool for Near Real-Time Underway Data (NRUD)\"}")
                        .aiGroup("Data Access")
                        .build(),
                //there are some links that are not enhanced by AI
                LinkModel.builder()
                        .href("https://www.marine.csiro.au/data/trawler/survey_mapfile.cfm?survey=IN2024_V01&data_type=uwy")
                        .rel("preview")
                        .type("image")
                        .build(),
                LinkModel.builder()
                        .href("http://140.79.20.100:8080/geonetwork/srv/eng/catalog.search#/metadata/ff887cf9-18bb-464e-8bad-5dc6e0ad946b")
                        .rel("describedby")
                        .type("text/html")
                        .title("Full metadata link")
                        .build(),
                LinkModel.builder()
                        .href("https://i.creativecommons.org/l/by/4.0/88x31.png")
                        .rel("license")
                        .type("image/png")
                        .build(),
                LinkModel.builder()
                        .href("https://creativecommons.org/licenses/by/4.0/")
                        .rel("license")
                        .type("text/html")
                        .build()
        );

        // Mock the AI enhanced assets
        Map<String, AssetModel> enhancedAssets = Map.of(
                "https://doi.org/10.25919/rdrt-bd71",
                AssetModel.builder()
                        .href("https://doi.org/10.25919/rdrt-bd71")
                        .title("Data Access Portal (DOI)")
                        .description("Link to this record at the CSIRO Data Access Portal")
                        .type("")
                        .role(AssetModel.Role.DATA)
                        .build()
        );

        // Mock the AI enhanced description
        String enhancedDescription = "This record describes the **End of Voyage (EOV)** data archive from the **Marine National Facility (MNF)** RV Investigator voyage **IN2024_V01**, titled \"Multidisciplinary Investigations of the Southern Ocean (MISO): linking physics, biogeochemistry, plankton, aerosols, clouds, and climate.\" The voyage took place between **January 02, 2024** and **March 05, 2024 (AEST)**, departing from **Hobart** and returning to **Fremantle**.\n\nFor further information please refer to the voyage documentation links.\n\nInstruments used and data collected include:\n\n### Regular measurements:\n- Lowered ADCP (LADCP)\n- Acoustic Doppler Current Profiler (ADCP; 75, 150 KHz)\n- Greenhouse Gas Analysers (Picarro)\n- Cloud Condensation Nuclei counter (CCN)\n- Condensation Particle Counters (CPC)\n- Disdrometer\n- Radon sensor\n- Scanning Mobility Particle Sizers (SMPS)\n- CTD\n- Hydrochemistry\n- Triaxus\n- Fisheries Echosounder (EK80)\n- Multibeam Echosounder (EM710, EM122)\n- Sub-bottom Profiler (SBP120)\n- GPS Positioning System\n- Doppler Velocity Log\n- Thermosalinographs (TSG)\n- Fluorometer\n- Oxygen Optode\n- pCO2\n- Multiangle Absorption Photometer (MAAP)\n- Ozone Sensor\n- Nephelometer\n- Atmospheric Temperature, Humidity, Pressure, Wind and Rain sensors\n- Photosynthetically Active Radiation (PAR) sensor\n- Precision Infrared Radiometer (PIR)\n- Precision Spectral Pyranometer (PSP)\n- Starboard and Portside Radiometers\n- Air Sampler\n- Ultra Short BaseLine Underwater Positioning System (USBL)\n- Weather Radar\n- Expendable Bathythermographs (XBTs).\n\n### Voyage-specific measurements:\n\n- **Black Carbon sensor (Aethalometer)**\n- **Mobility particle size spectrometer (MPSS)**\n- **Bongo Net**\n- **Chemical Ionisation Mass Spectrometer (CIMS)**\n- **Cloud Radar (BASTA)**\n- **Fast Repetition Rate Chlorophyll-a Fluorometer (FRRf)**\n- **Mini Micro-Pulse LIDAR (miniMPL)**\n- **Micro Rain Radar (MRR)**\n- **Neutral Cluster Air Ion Spectrometer (NAIS)**\n- **Proton-Transfer-Reaction Mass Spectrometry (PTR-MS)**\n- **Radiosondes**\n- **Cloud and Aerosol Backscatter Lidar (RMAN)**\n- **Stabilised Platform**\n- **Mercury Analyser (Tekran)**\n- **Time of Flight Aerosol Chemical Speciation Monitor (ToF-ACSM)**\n- **Water Vapor Radiometer (WVR)**\n- **Aerosol mass spectrometer (AMS)**\n- **Core Argo floats**\n- **Biogeochemical (BGC) Argo floats**\n- **Near-surface Drifters**\n- **In situ pumps (ISPs)**\n- **Ice Nucleating Particles (INPs)**\n- **Ozone Sensor**\n- **Trace Metal Aerosol Sampling**\n- **Trace Metal CTD Rosette and Bottles**\n- **Organic Sulfur Sequential Chemical Analysis Robot (OSSCAR)**\n- **Omics data and various biological data.**\n\nThe archive for the **IN2024_V01 EOV raw data** is curated by the **CSIRO National Collections and Marine Infrastructure (NCMI) Information and Data Centre (IDC)** in Hobart, with a permanent archive at the **CSIRO Data Access Portal** ([https://data.csiro.au/](https://data.csiro.au/)), providing access to voyage participants and processors of the data collected on the voyage.\n\nAll voyage documentation is available electronically to **MNF support** via the local network. Applications to access voyage documentation by non-CSIRO participants can be made via **data-requests-hf@csiro.au**.\n\nAll processed data from this voyage are made publicly available through the **MNF Data Trawler** (in the related links).";

        // Mock the AI enhanced update frequency
        String enhancedUpdateFrequency = "completed";

        // Create mock AI response
        AiEnhancementResponse mockAiResponse = Mockito.mock(AiEnhancementResponse.class);

        // Set up AI service to be available and mock the combined enhancement
        when(dataDiscoveryAiService.isServiceAvailable()).thenReturn(true);
        when(dataDiscoveryAiService.enhanceWithAi(any(AiEnhancementRequest.class)))
                .thenReturn(mockAiResponse);
        when(dataDiscoveryAiService.getEnhancedLinks(eq(mockAiResponse))).thenReturn(enhancedLinks);
        when(dataDiscoveryAiService.getEnhancedDescription(eq(mockAiResponse))).thenReturn(enhancedDescription);
        when(dataDiscoveryAiService.getEnhancedUpdateFrequency(eq(mockAiResponse))).thenReturn(enhancedUpdateFrequency);

        String xml = readResourceFile("classpath:canned/aienhancement/sample2.xml");
        String expected = readResourceFile("classpath:canned/aienhancement/sample2_stac_ai_enhanced.json");

        indexerService.indexMetadata(xml);

        verify(expected);
    }

}
