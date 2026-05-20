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
        StacItemModel item = StacItemModel.builder().uuid("item-1").build();

        assertEquals("Feature", item.getType());
        assertEquals("1.0.0", item.getStacVersion());
    }

    @Test
    void serialize_emitsExpectedWireKeys() {
        LinkModel link = LinkModel.builder()
                .href("https://example.org")
                .rel("self")
                .build();

        StacItemModel item = StacItemModel.builder()
                .uuid("item-1")
                .collection("col-1")
                .geometry(Map.of("type", "Point"))
                .bbox(List.of(List.of(BigDecimal.ZERO, BigDecimal.ZERO)))
                .properties(Map.of("datetime", "2020-01-01T00:00:00Z"))
                .links(List.of(link))
                .build();

        JsonNode tree = mapper.valueToTree(item);

        assertEquals("item-1", tree.get("id").asText());
        assertEquals("Feature", tree.get("type").asText());
        assertEquals("1.0.0", tree.get("stac_version").asText());
        assertEquals("col-1", tree.get("collection").asText());
        assertNotNull(tree.get("geometry"));
        assertNotNull(tree.get("bbox"));
        assertNotNull(tree.get("properties"));
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
                  "id": "item-1",
                  "collection": "col-1",
                  "geometry": {"type": "Point"},
                  "bbox": [[0, 0]],
                  "properties": {"datetime": "2020-01-01T00:00:00Z"}
                }
                """;

        StacItemModel parsed = mapper.readValue(json, StacItemModel.class);

        assertEquals("Feature", parsed.getType());
        assertEquals("1.0.0", parsed.getStacVersion());
        assertEquals("item-1", parsed.getUuid());
        assertEquals("col-1", parsed.getCollection());
        assertNotNull(parsed.getGeometry());
        assertNotNull(parsed.getBbox());
        assertNotNull(parsed.getProperties());
    }
}
