package au.org.aodn.stac.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonUtil {

    protected final static ObjectMapper mapper = new ObjectMapper();

    protected final static String SEMANTIC_TEXT = "semantic_text";

    public static <T> String toJsonString(T instance) {
        try{
            return mapper.writeValueAsString(instance);
        }
        catch (JsonProcessingException ignored){}
        return null;
    }

    public static Reader createJsonStream(String indexMappingFile, Map<String, String> param) throws IOException {
        return createJsonStream(indexMappingFile, param, true);
    }

    /**
     * @param semanticEnabled when false, every semantic_text field is stripped from the mapping
     *                        before the index is created.
     */
    public static Reader createJsonStream(String indexMappingFile, Map<String, String> param, boolean semanticEnabled) throws IOException {
        try (InputStream inputStream = JsonUtil.class.getResourceAsStream("/schema/" + indexMappingFile)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                if(param != null) {
                    for (Map.Entry<String, String> entry : param.entrySet()) {
                        json = json.replace("${" + entry.getKey() + "}", entry.getValue());
                    }
                }
                if (!semanticEnabled) {
                    json = stripSemanticTextFields(json);
                }
                return new StringReader(json);
            }
        }
        return null;
    }

    /**
     * Removes every semantic_text field from an index mapping, together with any copy_to
     * Elasticsearch inference only valid in certain license, in dev and test env, it is not avaiable.
     * So stripping the field keeps the same schema file usable on both licensed and unlicensed clusters
     * @param json the raw index mapping JSON
     * @return the mapping with no semantic fields, or the input unchanged when it had none
     */
    public static String stripSemanticTextFields(String json) throws IOException {
        JsonNode root = mapper.readTree(json);

        Set<String> removed = new HashSet<>();
        removeSemanticTextFields(root, removed);
        if (removed.isEmpty()) {
            return json;
        }

        removeCopyToTargets(root, removed);
        return mapper.writeValueAsString(root);
    }

    /**
     * Walks every property block in schema - mappings nest them under object and nested fields - removing
     * the fields typed semantic_text and collecting their names.
     */
    private static void removeSemanticTextFields(JsonNode node, Set<String> removed) {
        JsonNode properties = node.get("properties");
        if (properties instanceof ObjectNode props) {
            List<String> semanticFields = new ArrayList<>();
            props.fields().forEachRemaining(field -> {
                JsonNode type = field.getValue().get("type");
                if (type != null && SEMANTIC_TEXT.equals(type.asText())) {
                    semanticFields.add(field.getKey());
                }
            });
            semanticFields.forEach(name -> {
                props.remove(name);
                removed.add(name);
            });
        }
        node.forEach(child -> removeSemanticTextFields(child, removed));
    }

    /**
     * Drops copy_to references to fields that no longer exist, this copy_to is used for mapping a text field to a
     * semantic_text field.
     */
    private static void removeCopyToTargets(JsonNode node, Set<String> removed) {
        if (node instanceof ObjectNode object) {
            JsonNode copyTo = object.get("copy_to");
            if (copyTo != null && copyTo.isTextual() && removed.contains(copyTo.asText())) {
                object.remove("copy_to");
            }
            else if (copyTo != null && copyTo.isArray()) {
                ArrayNode kept = mapper.createArrayNode();
                copyTo.forEach(target -> {
                    if (!removed.contains(target.asText())) {
                        kept.add(target);
                    }
                });
                if (kept.isEmpty()) {
                    object.remove("copy_to");
                }
                else {
                    object.set("copy_to", kept);
                }
            }
        }
        node.forEach(child -> removeCopyToTargets(child, removed));
    }
}
