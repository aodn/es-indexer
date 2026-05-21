package au.org.aodn.stac.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StacCollectionModelTest {

    // FAIL_ON_UNKNOWN_PROPERTIES=false mirrors how a real STAC consumer reads docs —
    // StacCollectionModel emits getter-only constants (type, stac_version, stac_extensions)
    // that have no deserializer target, and STAC extensions add arbitrary keys.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void serialize_emitsExpectedWireKeys() throws Exception {
        SummariesModel summaries = SummariesModel.builder()
                .score(83)
                .status("completed")
                .updateFrequency("completed")
                .aiDescription("This record describes the End of Voyage archive from RV Investigator IN2019_V06.")
                .aiParameterVocabs(List.of("temperature"))
                .aiPlatformVocabs(List.of("research vessel"))
                .parameterVocabs(List.of("salinity", "carbon", "temperature"))
                .platformVocabs(List.of("buoy"))
                .organisationVocabs(List.of("CSIRO Oceans and Atmosphere"))
                .geometry(Map.of(
                        "type", "Polygon",
                        "coordinates", List.of(List.of(
                                List.of(116, -21), List.of(117, -21), List.of(117, -19),
                                List.of(115, -19), List.of(115, -20), List.of(116, -20),
                                List.of(116, -21)))))
                .build();

        LinkModel link = LinkModel.builder()
                .href("https://doi.org/10.25919/rdrt-bd71")
                .rel("data")
                .title("Data Access Portal (DOI)")
                .description("Link to this record at the CSIRO Data Access Portal")
                .aiGroup("Data Access")
                .aiRole(List.of("download"))
                .build();

        ContactsAddressModel address = ContactsAddressModel.builder()
                .deliveryPoint(List.of("University of Tasmania", "Private Bag 110"))
                .city("Hobart")
                .administrativeArea("Tasmania")
                .postalCode("7001")
                .country("Australia")
                .build();

        ContactsPhoneModel phone = ContactsPhoneModel.builder()
                .roles(List.of("voice"))
                .value("61 3 6226 7488")
                .build();

        ContactsModel contact = ContactsModel.builder()
                .identifier("test-contact-1")
                .organization("Integrated Marine Observing System (IMOS)")
                .position("Data Officer")
                .roles(List.of("pointOfContact", "about"))
                .emails(new LinkedHashSet<>(List.of("info@aodn.org.au")))
                .addresses(new LinkedHashSet<>(List.of(address)))
                .phones(new LinkedHashSet<>(List.of(phone)))
                .build();

        ExtentModel extent = ExtentModel.builder()
                .temporal(List.of(List.of("2007-09-08T14:00:00Z", "2024-04-30T07:07:51Z")))
                .build();

        StacCollectionModel model = StacCollectionModel.builder()
                .uuid("test-uuid-1")
                .title("IMOS - Australian National Mooring Network (ANMN) Facility - Current velocity time-series")
                .description("Time-series observations of current velocity from ANMN moorings.")
                .summaries(summaries)
                .links(List.of(link))
                .contacts(List.of(contact))
                .extent(extent)
                .build();

        JsonNode tree = mapper.valueToTree(model);

        // Top-level snake_case / namespaced keys
        assertEquals("test-uuid-1", tree.get("id").asText());
        assertEquals("Collection", tree.get("type").asText());
        assertNotNull(tree.get("stac_version"));
        assertNotNull(tree.get("stac_extensions"));

        // SummariesModel ai:* / proj:* / snake_case keys
        JsonNode sum = tree.get("summaries");
        assertEquals(83, sum.get("score").asInt());
        assertEquals("completed", sum.get("update_frequency").asText());
        assertTrue(sum.get("ai:description").asText().startsWith("This record describes"));
        assertTrue(sum.has("ai:parameter_vocabs"));
        assertTrue(sum.has("ai:platform_vocabs"));
        assertTrue(sum.has("parameter_vocabs"));
        assertTrue(sum.has("platform_vocabs"));
        assertTrue(sum.has("organisation_vocabs"));
        assertTrue(sum.has("proj:geometry"));

        // LinkModel ai:* keys
        JsonNode l = tree.get("links").get(0);
        assertEquals("Data Access", l.get("ai:group").asText());
        assertTrue(l.get("ai:role").isArray());
        assertEquals("Link to this record at the CSIRO Data Access Portal",
                l.get("description").asText());

        // ContactsModel.identifier and ContactsAddressModel snake_case keys
        JsonNode c = tree.get("contacts").get(0);
        assertEquals("test-contact-1", c.get("identifier").asText());
        JsonNode addr = c.get("addresses").get(0);
        assertTrue(addr.has("delivery_point"));
        assertEquals("Tasmania", addr.get("administrative_area").asText());
        assertEquals("7001", addr.get("postal_code").asText());

        // ExtentModel.temporal is List<List<String>>
        JsonNode temporal = tree.get("extent").get("temporal");
        assertTrue(temporal.isArray());
        assertTrue(temporal.get(0).isArray());
        assertEquals("2007-09-08T14:00:00Z", temporal.get(0).get(0).asText());
    }

    @Test
    void summariesModel_deserialize_acceptsSnakeCaseAndNamespacedKeys() throws Exception {
        String json = """
                {
                  "score": 83,
                  "update_frequency": "completed",
                  "ai:description": "End of Voyage archive from RV Investigator IN2019_V06",
                  "ai:parameter_vocabs": ["temperature"],
                  "parameter_vocabs": ["salinity", "carbon", "temperature"],
                  "platform_vocabs": ["buoy"],
                  "organisation_vocabs": ["CSIRO Oceans and Atmosphere"],
                  "proj:geometry": {"type": "Point", "coordinates": [116.0, -21.0]}
                }
                """;

        SummariesModel parsed = mapper.readValue(json, SummariesModel.class);

        assertEquals(83, parsed.getScore());
        assertEquals("completed", parsed.getUpdateFrequency());
        assertEquals("End of Voyage archive from RV Investigator IN2019_V06",
                parsed.getAiDescription());
        assertEquals(List.of("temperature"), parsed.getAiParameterVocabs());
        assertEquals(List.of("salinity", "carbon", "temperature"), parsed.getParameterVocabs());
        assertEquals(List.of("buoy"), parsed.getPlatformVocabs());
        assertEquals(List.of("CSIRO Oceans and Atmosphere"), parsed.getOrganisationVocabs());
        assertNotNull(parsed.getGeometry());
    }

    @Test
    void linkModel_deserialize_acceptsAiKeys() throws Exception {
        String json = """
                {
                  "href": "https://doi.org/10.25919/rdrt-bd71",
                  "rel": "data",
                  "ai:group": "Data Access",
                  "ai:role": ["download"],
                  "description": "Link to this record at the CSIRO Data Access Portal"
                }
                """;

        LinkModel link = mapper.readValue(json, LinkModel.class);

        assertEquals("Data Access", link.getAiGroup());
        assertEquals(List.of("download"), link.getAiRole());
        assertEquals("Link to this record at the CSIRO Data Access Portal",
                link.getDescription());
    }

    @Test
    void fullDocument_roundtrip_preservesWireKeys() throws Exception {
        SummariesModel summaries = SummariesModel.builder()
                .score(83)
                .updateFrequency("completed")
                .aiDescription("End of Voyage archive from RV Investigator IN2019_V06")
                .aiParameterVocabs(List.of("temperature"))
                .aiPlatformVocabs(List.of("research vessel"))
                .parameterVocabs(List.of("salinity", "carbon", "temperature"))
                .platformVocabs(List.of("buoy"))
                .organisationVocabs(List.of("CSIRO Oceans and Atmosphere"))
                .geometry(Map.of("type", "Point", "coordinates", List.of(116.0, -21.0)))
                .build();

        LinkModel link = LinkModel.builder()
                .href("https://doi.org/10.25919/rdrt-bd71")
                .rel("data")
                .title("Data Access Portal (DOI)")
                .description("Link to this record at the CSIRO Data Access Portal")
                .aiGroup("Data Access")
                .aiRole(List.of("download"))
                .build();

        ContactsAddressModel address = ContactsAddressModel.builder()
                .deliveryPoint(List.of("University of Tasmania", "Private Bag 110"))
                .city("Hobart")
                .administrativeArea("Tasmania")
                .postalCode("7001")
                .country("Australia")
                .build();

        ContactsPhoneModel phone = ContactsPhoneModel.builder()
                .roles(List.of("voice"))
                .value("61 3 6226 7488")
                .build();

        ContactsModel contact = ContactsModel.builder()
                .identifier("test-contact-1")
                .organization("Integrated Marine Observing System (IMOS)")
                .position("Data Officer")
                .roles(List.of("pointOfContact", "about"))
                .emails(new LinkedHashSet<>(List.of("info@aodn.org.au")))
                .addresses(new LinkedHashSet<>(List.of(address)))
                .phones(new LinkedHashSet<>(List.of(phone)))
                .build();

        ExtentModel extent = ExtentModel.builder()
                .temporal(List.of(List.of("2007-09-08T14:00:00Z", "2024-04-30T07:07:51Z")))
                .build();

        ProviderModel provider = ProviderModel.builder()
                .name("Integrated Marine Observing System (IMOS)")
                .roles(List.of("distributor"))
                .url("http://imos.org.au/aodn.html")
                .build();

        LanguageModel language = LanguageModel.builder()
                .code("eng")
                .name("English")
                .build();

        ConceptModel concept = ConceptModel.builder()
                .id("Oceans | Ocean Temperature | Water Temperature")
                .title("NASA/Global Change Master Directory Earth Science Keywords Version 5.3.8")
                .description("GCMD")
                .aiDescription("AI-suggested keyword")
                .build();

        ThemesModel theme = ThemesModel.builder()
                .scheme("theme")
                .concepts(List.of(concept))
                .build();

        AssetModel asset = AssetModel.builder()
                .href("https://thredds.aodn.org.au/thredds/dodsC/IMOS/ANMN/sample.nc")
                .title("Current velocity NetCDF")
                .role(AssetModel.Role.DATA)
                .build();

        SearchSuggestionsModel suggestions = SearchSuggestionsModel.builder()
                .abstractPhrases(java.util.Set.of("ocean current velocity time-series"))
                .build();

        StacCollectionModel original = StacCollectionModel.builder()
                .uuid("test-uuid-1")
                .title("IMOS - ANMN Facility - Current velocity time-series")
                .description("Time-series observations of current velocity from ANMN moorings.")
                .summaries(summaries)
                .links(List.of(link))
                .contacts(List.of(contact))
                .extent(extent)
                .providers(List.of(provider))
                .languages(List.of(language))
                .themes(List.of(theme))
                .assets(Map.of("data", asset))
                .searchSuggestionsModel(suggestions)
                .build();

        String json = mapper.writeValueAsString(original);
        StacCollectionModel parsed = mapper.readValue(json, StacCollectionModel.class);
        JsonNode tree = mapper.valueToTree(parsed);

        // Top-level wire keys survive the round trip.
        assertEquals("test-uuid-1", tree.get("id").asText());
        assertEquals("Collection", tree.get("type").asText());
        assertEquals("1.0.0", tree.get("stac_version").asText());
        assertTrue(tree.has("search_suggestions"));

        // Nested snake_case / ai:* / proj:* keys survive too.
        assertEquals("completed", tree.get("summaries").get("update_frequency").asText());
        assertTrue(tree.get("summaries").get("ai:description").asText()
                .contains("RV Investigator IN2019_V06"));
        assertTrue(tree.get("summaries").has("proj:geometry"));
        assertEquals("Data Access", tree.get("links").get(0).get("ai:group").asText());
        assertEquals("test-contact-1",
                tree.get("contacts").get(0).get("identifier").asText());
        assertTrue(tree.get("contacts").get(0).get("addresses").get(0).has("delivery_point"));
        assertEquals("AI-suggested keyword",
                tree.get("themes").get(0).get("concepts").get(0).get("ai:description").asText());

        // Round-tripped object equals the original.
        assertEquals(original, parsed);
    }
}
