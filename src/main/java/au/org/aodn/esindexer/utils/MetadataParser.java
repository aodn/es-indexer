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

    protected String getTextValueAtPath(JsonNode rootNode, String key) {

         /*
         Some metadata fields can be retrieved by different GML paths in different metadata records.

         E.g
         "license" can be retrieved by "mdb:identificationInfo/mri:MD_DataIdentification/mri:resourceConstraints/mco:MD_LegalConstraints/mco:reference/cit:CI_Citation/cit:onlineResource/cit:CI_OnlineResource/cit:linkage/gco:CharacterString/#text"
         OR "mdb:identificationInfo/mri:MD_DataIdentification/mri:resourceConstraints/mco:MD_LegalConstraints/mco:otherConstraints/gco:CharacterString/#text"

         Hence, the mapping config file contains the path to the field in the following format:

         {
            "key": ["path_option_1", "path_option_2", ...]
         }

         Notice "OR", if the field cannot be found in the first path, it will try the second path, and so on.
         */
        for (JsonNode pathOption : extractingConfig.get(key)) {
            String[] pathElements = pathOption.toString().replace("\"", "").split("/");
            JsonNode searchNode = rootNode;
            logger.info("Trying path: " + pathOption);
            for (String currentElement : pathElements) {
                searchNode = searchNode.path(currentElement);
                if (searchNode.isMissingNode()) {
                    logger.info("Path not found: " + pathOption);
                    break;
                }
                if (currentElement.equals("#text")) {
                    logger.info("Found value: " + searchNode.asText()) ;
                    return searchNode.asText();
                }
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
