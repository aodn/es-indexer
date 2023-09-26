package au.org.aodn.esindexer.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MetadataMapper {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MetadataParser metadataParser;

    // TODO: map metadata values from GeoNetwork to portal index
    public JSONObject mapMetadataValuesForPortalIndex(JsonNode rootNode) {
        JSONObject mappedMetadataValues = new JSONObject();

        // put the parsed values into the portal index structure
        mappedMetadataValues.put("metadataIdentifier", metadataParser.getMetadataIdentifier(rootNode));

        // return the mapped metadata values
        return mappedMetadataValues;
    }
}
