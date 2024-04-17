package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.abstracts.OgcApiRequestEntityCreator;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class AodnDiscoveryParameterVocabUtils {

    @Value("${ogc-api.host}")
    private String ogcApiHost;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    OgcApiRequestEntityCreator ogcApiRequestEntityCreator;

    /* run this method after the bean is created */
    @PostConstruct
    protected List<JsonNode> fetchAodnDiscoveryParameterVocabs() {
        HttpEntity<String> requestEntity = ogcApiRequestEntityCreator.getRequestEntity(MediaType.APPLICATION_JSON, null);
        ResponseEntity<JsonNode> responseEntity = restTemplate.exchange(
                ogcApiHost + "/api/v1/ogc/ext/parameter/categories",
                HttpMethod.GET,
                requestEntity,
                JsonNode.class);

        List<JsonNode> results = new ArrayList<>();
        for (JsonNode obj : Objects.requireNonNull(responseEntity.getBody())) {
            /*
            the json object contains all level vocabs (first -> second -> bottom)
            has list of items inside "narrower" field but empty inside "broader" field,
            the pattern can be verified by inspecting the response from OGCApi parameter categories endpoint
            and from the current AODN portal's parameter menu
            "results" length should be 4;
             */
            if (!obj.get("narrower").isEmpty() && obj.get("broader").isEmpty()) {
                results.add(obj);
            }
        }

        return results;
    }

    protected boolean themesMatchConcept(List<ThemesModel> themes, ConceptModel concept) {
        for (ThemesModel theme : themes) {
            for (ConceptModel themeConcept : theme.getConcepts()) {
                /*
                comparing by hashcode (id and url) of the concept object
                this will prevent cases where bottom-level vocabs are the same in text, but their parent vocabs are different
                e.g "car -> parts" vs "bike -> parts" ("parts" is the same but different parent)
                 */
                if (themeConcept.equals(concept)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<String> getAodnDiscoveryCategories(List<ThemesModel> themes) {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        for (JsonNode topLevelVocab : this.fetchAodnDiscoveryParameterVocabs()) {
            if (topLevelVocab.has("narrower") && !topLevelVocab.get("narrower").isEmpty()) {
                for (JsonNode secondLevelVocab : topLevelVocab.get("narrower")) {
                    String secondLevelVocabLabel = secondLevelVocab.get("label").asText();
                    if (secondLevelVocab.has("narrower") && !secondLevelVocab.get("narrower").isEmpty()) {
                        for (JsonNode bottomLevelVocab : secondLevelVocab.get("narrower")) {
                            // map the original values to a ConceptModel object for doing comparison
                            ConceptModel bottomConcept = ConceptModel.builder()
                                    .id(bottomLevelVocab.get("label").asText())
                                    .url(bottomLevelVocab.get("about").asText())
                                    .build();
                            // Compare with themes' concepts
                            if (themesMatchConcept(themes, bottomConcept)) {
                                results.add(secondLevelVocabLabel);
                                break; // To avoid duplicates because under the same second-level vocab there can be multiple bottom-level vocabs that pass the condition
                            }
                        }
                    }
                }
            }
        }
        return results;
    }
}
