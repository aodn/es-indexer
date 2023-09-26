package au.org.aodn.esindexer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MetadataParser {
    @Autowired
    ObjectMapper objectMapper;

    public String getMetadataIdentifier(JsonNode rootNode) {
        return rootNode.path("mdb:metadataIdentifier")
                .path("mcc:MD_Identifier")
                .path("mcc:code")
                .path("gco:CharacterString")
                .path("#text")
                .asText();
    }
}
