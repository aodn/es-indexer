package au.org.aodn.stac.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StacItemModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void builderDefaults_typeAndStacVersion() {
        StacItemModel item = StacItemModel.builder().uuid("test-uuid-1").build();

        assertEquals("Feature", item.getType());
        assertEquals("1.0.0", item.getStacVersion());
    }

    @Test
    void serialize_emitsExpectedWireKeys() {
        LinkModel link = LinkModel.builder()
                .href("http://imos.org.au/nationalmooringnetwork.html")
                .rel("related")
                .title("ANMN page on IMOS website")
                .build();

        StacItemModel item = StacItemModel.builder()
                .uuid("test-uuid-1")
                .collection("ae86e2f5-eaaf-459e-a405-e654d85adb9c")
                .geometry(Map.of(
                        "type", "Point",
                        "coordinates", List.of(BigDecimal.valueOf(116.0), BigDecimal.valueOf(-21.0))))
                .bbox(List.of(List.of(
                        BigDecimal.valueOf(113.0), BigDecimal.valueOf(-43.0),
                        BigDecimal.valueOf(154.0), BigDecimal.valueOf(-9.0))))
                .properties(Map.of("datetime", "2007-09-08T14:00:00Z"))
                .links(List.of(link))
                .build();

        JsonNode tree = mapper.valueToTree(item);

        assertEquals("test-uuid-1", tree.get("id").asText());
        assertEquals("Feature", tree.get("type").asText());
        assertEquals("1.0.0", tree.get("stac_version").asText());
        assertEquals("ae86e2f5-eaaf-459e-a405-e654d85adb9c", tree.get("collection").asText());
        assertNotNull(tree.get("geometry"));
        assertNotNull(tree.get("bbox"));
        assertEquals("2007-09-08T14:00:00Z", tree.get("properties").get("datetime").asText());
        assertEquals("http://imos.org.au/nationalmooringnetwork.html",
                tree.get("links").get(0).get("href").asText());
        assertTrue(tree.get("stac_extensions").isArray());
    }

    @Test
    void deserialize_setsTypeAndStacVersionFromInput() throws Exception {
        // Step 1 #6: type and stacVersion must be settable so input docs deserialize,
        // not getter-only constants.
        String json = """
                {
                  "type": "Feature",
                  "stac_version": "1.0.0",
                  "id": "test-uuid-1",
                  "collection": "ae86e2f5-eaaf-459e-a405-e654d85adb9c",
                  "geometry": {"type": "Point", "coordinates": [116.0, -21.0]},
                  "bbox": [[113.0, -43.0, 154.0, -9.0]],
                  "properties": {"datetime": "2007-09-08T14:00:00Z"}
                }
                """;

        StacItemModel parsed = mapper.readValue(json, StacItemModel.class);

        assertEquals("Feature", parsed.getType());
        assertEquals("1.0.0", parsed.getStacVersion());
        assertEquals("test-uuid-1", parsed.getUuid());
        assertEquals("ae86e2f5-eaaf-459e-a405-e654d85adb9c", parsed.getCollection());
        assertNotNull(parsed.getGeometry());
        assertNotNull(parsed.getBbox());
        assertNotNull(parsed.getProperties());
    }
}
