package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.ExtractingValueException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MetadataParser {

    private JsonNode extractingConfig;
    private static final Logger logger = LoggerFactory.getLogger(MetadataParser.class);

    protected String getTextValueAtPath(JsonNode node, String key) {
        String[] pathElements = extractingConfig.get(key).toString().replace("\"", "").split("/");
        for (String currentElement : pathElements) {
            node = node.path(currentElement);
            if (node.isMissingNode()) {
                throw new ExtractingValueException("Error extracting value from GeoNetwork metadata JSON");
            }
            if (currentElement.equals("#text")) {
                return node.asText();
            }
        }
        // throw exception if it reaches here
        throw new ExtractingValueException("Error extracting value from GeoNetwork metadata JSON");
    }

    // inject ObjectMapper via constructor to read extracting config file
    @Autowired
    MetadataParser(ObjectMapper objectMapper) {
        ClassPathResource extracting = new ClassPathResource("config_files/" + AppConstants.METADATA_EXTRACTING_CONFIG);
        try {
            extractingConfig = objectMapper.readTree(extracting.getInputStream());
        } catch (IOException e) {
            throw new ExtractingValueException("Error reading extracting config file");
        }
    }

    public JSONObject extractToMappedValues(JsonNode rootNode) {
        JSONObject mappedValues = new JSONObject();
        extractingConfig.fieldNames().forEachRemaining(key -> {
            logger.info("Extracting value for key: " + key);
            // TODO: not everything is a string, need to handle other types
            mappedValues.put(key, this.getTextValueAtPath(rootNode, key));
        });
        return mappedValues;
    }
}
