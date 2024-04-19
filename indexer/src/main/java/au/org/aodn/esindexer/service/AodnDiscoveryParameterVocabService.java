package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.abstracts.OgcApiRequestEntityCreator;
import au.org.aodn.esindexer.utils.CacheArdcVocabsUtils;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Service
public class AodnDiscoveryParameterVocabService {
    @Autowired
    RestTemplate restTemplate;

    @Autowired
    OgcApiRequestEntityCreator ogcApiRequestEntityCreator;

    @Autowired
    CacheArdcVocabsUtils cacheArdcVocabsUtils;

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

    public List<String> getAodnDiscoveryCategories(List<ThemesModel> themes) throws IOException {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        for (JsonNode topLevelVocab : cacheArdcVocabsUtils.getDiscoveryCategories()) {
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
