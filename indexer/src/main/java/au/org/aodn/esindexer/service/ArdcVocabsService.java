package au.org.aodn.esindexer.service;

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
public class ArdcVocabsService {
    @Autowired
    VocabsUtils cacheArdcVocabsUtils;

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

    protected boolean themesMatchConcept(List<ThemesModel> themes, ConceptModel thatConcept) {
        for (ThemesModel theme : themes) {
            for (ConceptModel thisConcept : theme.getConcepts()) {
                /*
                comparing by combined values (id and url) of the concept object
                this will prevent cases where bottom-level vocabs are the same in text, but their parent vocabs are different
                e.g "car -> parts" vs "bike -> parts" ("parts" is the same but different parent)
                 */
                if (thisConcept.equals(thatConcept)) {
                    /* thisConcept is the extracted from the themes of the record...theme.getConcepts()
                    thatConcept is the object created by iterating over the parameter_vocabs cache...ConceptModel thatConcept = ConceptModel.builder()
                    using overriding equals method to compare the two objects, this is not checking instanceof ConceptModel class
                     */
                    return true;
                }
            }
        }
        return false;
    }

    /*
    this method for analysing the AODN discovery parameter vocabularies of a record aka bottom-level vocabs (found in the themes section)
    and returning the second-level vocabularies that match (1 level up from the bottom-level vocabularies)
     */
    public List<String> getParameterVocabs(List<ThemesModel> themes) throws IOException {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        for (JsonNode topLevelVocab : cacheArdcVocabsUtils.getParameterVocabs()) {
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
                                results.add(secondLevelVocabLabel.toLowerCase());
                                break; // To avoid duplicates because under the same second-level vocab there can be multiple bottom-level vocabs that pass the condition
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    // TODO: getPlatformVocabs
    public List<String> getPlatformVocabs() throws IOException {
        List<String> results = new ArrayList<>();
//        for (JsonNode vocab : cacheArdcVocabsUtils.getPlatformVocabs()) {
//            results.add("sample platform vocab");
//        }
        return results;
    }

    // TODO: getOrganisationVocabs
    public List<String> getOrganisationVocabs() throws IOException {
        List<String> results = new ArrayList<>();
//        for (JsonNode vocab : cacheArdcVocabsUtils.getOrganisationVocabs()) {
//            results.add("sample organisation vocab");
//        }
        return results;
    }
}
