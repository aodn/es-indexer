package au.org.aodn.stac.model;

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

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialize_emitsExpectedWireKeys() throws Exception {
        SummariesModel summaries = SummariesModel.builder()
                .score(7)
                .status("onGoing")
                .updateFrequency("daily")
                .aiDescription("ai summary")
                .aiParameterVocabs(List.of("temperature"))
                .aiPlatformVocabs(List.of("mooring"))
                .parameterVocabs(List.of("temp"))
                .platformVocabs(List.of("buoy"))
                .organisationVocabs(List.of("IMOS"))
                .geometry(Map.of("type", "Point"))
                .build();

        LinkModel link = LinkModel.builder()
                .href("https://example.org")
                .rel("self")
                .title("Self")
                .description("self link")
                .aiGroup("g1")
                .aiRole(List.of("primary"))
                .build();

        ContactsAddressModel address = ContactsAddressModel.builder()
                .deliveryPoint(List.of("123 Ocean Rd"))
                .city("Hobart")
                .administrativeArea("TAS")
                .postalCode("7000")
                .country("AU")
                .build();

        ContactsPhoneModel phone = ContactsPhoneModel.builder()
                .roles(List.of("voice"))
                .value("+61 3 1234 5678")
                .build();

        ContactsModel contact = ContactsModel.builder()
                .identifier("contact-1")
                .name("Jane Doe")
                .organization("IMOS")
                .roles(List.of("pointOfContact"))
                .addresses(new LinkedHashSet<>(List.of(address)))
                .phones(new LinkedHashSet<>(List.of(phone)))
                .build();

        ExtentModel extent = ExtentModel.builder()
                .temporal(List.of(List.of("2020-01-01T00:00:00Z", "2021-01-01T00:00:00Z")))
                .build();

        StacCollectionModel model = StacCollectionModel.builder()
                .uuid("abc-123")
                .title("A collection")
                .description("desc")
                .summaries(summaries)
                .links(List.of(link))
                .contacts(List.of(contact))
                .extent(extent)
                .build();

        JsonNode tree = mapper.valueToTree(model);

        // Top-level snake_case / namespaced keys
        assertEquals("abc-123", tree.get("id").asText());
        assertEquals("Collection", tree.get("type").asText());
        assertNotNull(tree.get("stac_version"));
        assertNotNull(tree.get("stac_extensions"));

        // SummariesModel ai:* / proj:* / snake_case keys
        JsonNode sum = tree.get("summaries");
        assertEquals(7, sum.get("score").asInt());
        assertEquals("daily", sum.get("update_frequency").asText());
        assertEquals("ai summary", sum.get("ai:description").asText());
        assertTrue(sum.has("ai:parameter_vocabs"));
        assertTrue(sum.has("ai:platform_vocabs"));
        assertTrue(sum.has("parameter_vocabs"));
        assertTrue(sum.has("platform_vocabs"));
        assertTrue(sum.has("organisation_vocabs"));
        assertTrue(sum.has("proj:geometry"));

        // LinkModel ai:* keys
        JsonNode l = tree.get("links").get(0);
        assertEquals("g1", l.get("ai:group").asText());
        assertTrue(l.get("ai:role").isArray());
        assertEquals("self link", l.get("description").asText());

        // ContactsModel.identifier and ContactsAddressModel snake_case keys
        JsonNode c = tree.get("contacts").get(0);
        assertEquals("contact-1", c.get("identifier").asText());
        JsonNode addr = c.get("addresses").get(0);
        assertTrue(addr.has("delivery_point"));
        assertEquals("TAS", addr.get("administrative_area").asText());
        assertEquals("7000", addr.get("postal_code").asText());

        // ExtentModel.temporal is List<List<String>>, not String[]
        JsonNode temporal = tree.get("extent").get("temporal");
        assertTrue(temporal.isArray());
        assertTrue(temporal.get(0).isArray());
        assertEquals("2020-01-01T00:00:00Z", temporal.get(0).get(0).asText());
    }

    @Test
    void summariesModel_deserialize_acceptsSnakeCaseAndNamespacedKeys() throws Exception {
        String json = """
                {
                  "score": 5,
                  "update_frequency": "weekly",
                  "ai:description": "x",
                  "ai:parameter_vocabs": ["t"],
                  "parameter_vocabs": ["t2"],
                  "platform_vocabs": ["p"],
                  "organisation_vocabs": ["o"],
                  "proj:geometry": {"type": "Point"}
                }
                """;

        SummariesModel parsed = mapper.readValue(json, SummariesModel.class);

        assertEquals(5, parsed.getScore());
        assertEquals("weekly", parsed.getUpdateFrequency());
        assertEquals("x", parsed.getAiDescription());
        assertEquals(List.of("t"), parsed.getAiParameterVocabs());
        assertEquals(List.of("t2"), parsed.getParameterVocabs());
        assertEquals(List.of("p"), parsed.getPlatformVocabs());
        assertEquals(List.of("o"), parsed.getOrganisationVocabs());
        assertNotNull(parsed.getGeometry());
    }

    @Test
    void linkModel_deserialize_acceptsAiKeys() throws Exception {
        String json = """
                {"href": "h", "rel": "self", "ai:group": "g", "ai:role": ["r"], "description": "d"}
                """;

        LinkModel link = mapper.readValue(json, LinkModel.class);

        assertEquals("g", link.getAiGroup());
        assertEquals(List.of("r"), link.getAiRole());
        assertEquals("d", link.getDescription());
    }
}
