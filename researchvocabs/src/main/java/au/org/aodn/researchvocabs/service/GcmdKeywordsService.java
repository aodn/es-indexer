package au.org.aodn.researchvocabs.service;

import au.org.aodn.researchvocabs.model.GcmdKeywordModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static au.org.aodn.researchvocabs.utils.ResearchVocabsUtils.isNodeValid;


@Slf4j
@Service
public class GcmdKeywordsService {
    @Autowired
    protected RestTemplate gcmdVocabRestTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    public List<GcmdKeywordModel> getGcmdKeywords(String nasaApiEndpoint) {
        try {
            ObjectNode respondObject = gcmdVocabRestTemplate.getForObject(nasaApiEndpoint, ObjectNode.class);
            if (isNodeValid.apply(respondObject, "tree") && isNodeValid.apply(respondObject.get("tree"), "treeData")) {
                JsonNode treeNode = respondObject.get("tree");
                List<GcmdKeywordModel> gcmdKeywords = new ArrayList<>();
                if (treeNode != null) {
                    JsonNode treeData = treeNode.get("treeData");
                    for (JsonNode treeItem : treeData) {
                        if (treeItem != null && isNodeValid.apply(treeItem, "children")) {
                            JsonNode childrenNodes = treeItem.get("children");
                            if (childrenNodes != null) {
                                List<GcmdKeywordModel> keywords = objectMapper.convertValue(childrenNodes, new TypeReference<List<GcmdKeywordModel>>() {});
                                gcmdKeywords.addAll(keywords);
                            }
                        }
                    }
                }
                return gcmdKeywords;
            }
        } catch (Exception e) {
            log.error("Error fetching GCMD keywords from endpoint: {}", nasaApiEndpoint, e);
        }
        return null;
    }
}
