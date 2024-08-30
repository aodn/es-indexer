package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.configuration.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

public interface VocabService {
    List<VocabModel> getVocabTreeFromArdcByType(String vocabApiBase, VocabApiPaths vocabApiPaths);
    List<String> extractVocabLabelsFromThemes(List<ThemesModel> themes, String vocabType) throws IOException;

    void populateVocabsData() throws IOException;
    void clearParameterVocabCache();
    void clearPlatformVocabCache();
    void clearOrganisationVocabCache();

    List<JsonNode> getParameterVocabs() throws IOException;
    List<JsonNode> getPlatformVocabs() throws IOException;
    List<JsonNode> getOrganisationVocabs() throws IOException;
}
