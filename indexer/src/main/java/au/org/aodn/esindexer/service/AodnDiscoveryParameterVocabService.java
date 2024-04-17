package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.CategoryVocabModel;
import au.org.aodn.esindexer.abstracts.OgcApiRequestEntityCreator;
import au.org.aodn.esindexer.utils.CacheArdcVocabsUtils;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class AodnDiscoveryParameterVocabService {
    @Autowired
    RestTemplate restTemplate;

    @Autowired
    OgcApiRequestEntityCreator ogcApiRequestEntityCreator;

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

    public List<String> getAodnDiscoveryCategories(List<ThemesModel> themes) {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        for (CategoryVocabModel topLevelVocab : cacheArdcVocabsUtils.getCachedData()) {
            if (!topLevelVocab.getNarrower().isEmpty()) {
                for (CategoryVocabModel secondLevelVocab : topLevelVocab.getNarrower()) {
                    String secondLevelVocabLabel = secondLevelVocab.getLabel();
                    if (!secondLevelVocab.getNarrower().isEmpty()) {
                        for (CategoryVocabModel bottomLevelVocab : secondLevelVocab.getNarrower()) {
                            // map the original values to a ConceptModel object for doing comparison
                            ConceptModel bottomConcept = ConceptModel.builder()
                                    .id(bottomLevelVocab.getLabel())
                                    .url(bottomLevelVocab.getAbout())
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
