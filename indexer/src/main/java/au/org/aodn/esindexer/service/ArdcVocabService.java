package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.utils.VocabsUtils;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Service
public class ArdcVocabService {
    @Autowired
    VocabsUtils vocabsUtils;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    /*
    this method for analysing the vocabularies of a record aka bottom-level vocabs (found in the themes section)
    and returning the second-level vocabularies that match (1 level up from the bottom-level vocabularies)
     */
    public List<String> getVocabLabelsByThemes(List<ThemesModel> themes, String vocabType) throws IOException {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        List<JsonNode> vocabs = switch (vocabType) {
            case AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_KEY -> vocabsUtils.getParameterVocabs();
            case AppConstants.AODN_PLATFORM_VOCABS_KEY -> vocabsUtils.getPlatformVocabs();
            default -> new ArrayList<>();
        };

        if (!vocabs.isEmpty()) {
            for (JsonNode topLevelVocab : vocabs) {
                if (topLevelVocab != null && topLevelVocab.has("narrower") && !topLevelVocab.get("narrower").isEmpty()) {
                    for (JsonNode secondLevelVocab : topLevelVocab.get("narrower")) {
                        if (secondLevelVocab != null && secondLevelVocab.has("label") && secondLevelVocab.has("narrower") && !secondLevelVocab.get("narrower").isEmpty()) {
                            String secondLevelVocabLabel = secondLevelVocab.get("label").asText();
                            for (JsonNode bottomLevelVocab : secondLevelVocab.get("narrower")) {
                                if (bottomLevelVocab != null && bottomLevelVocab.has("label") && bottomLevelVocab.has("about")) {
                                    // map the original values to a ConceptModel object for doing comparison
                                    ConceptModel bottomConcept = ConceptModel.builder()
                                            .id(bottomLevelVocab.get("label").asText())
                                            .url(bottomLevelVocab.get("about").asText())
                                            .build();

                                    // Compare with themes' concepts
                                    if (VocabsUtils.themesMatchConcept(themes, bottomConcept)) {
                                        results.add(secondLevelVocabLabel.toLowerCase());
                                        break; // To avoid duplicates because under the same second-level vocab there can be multiple bottom-level vocabs that pass the condition
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    // used by debugging/development api endpoint
    public List<JsonNode> getParameterVocabs() throws IOException {
        return vocabsUtils.getParameterVocabs();
    }

    // used by debugging/development api endpoint
    public List<JsonNode> getPlatformVocabs() throws IOException {
        return vocabsUtils.getPlatformVocabs();
    }
}
