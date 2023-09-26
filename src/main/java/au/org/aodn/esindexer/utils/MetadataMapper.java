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
    public JSONObject mapMetadataValuesForPortalIndex(JSONObject metadataValues) throws JsonProcessingException {
        JSONObject mappedMetadataValues = new JSONObject();

        // find the values from GeoNetwork metadata record
        String metadataIdentifier = metadataParser.getMetadataIdentifier(metadataValues);

        // put the values into the portal index structure
        mappedMetadataValues.put("metadataIdentifier", metadataIdentifier);

        // return the mapped metadata values
        return mappedMetadataValues;
    }
}
