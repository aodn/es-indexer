package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface VocabService {
    List<String> extractVocabLabelsFromThemes(List<ThemesModel> themes, String vocabType) throws IOException;

    void populateVocabsData();
    void clearParameterVocabCache();
    void clearPlatformVocabCache();
    void clearOrganisationVocabCache();

    List<JsonNode> getParameterVocabs() throws IOException;
    List<JsonNode> getPlatformVocabs() throws IOException;
    List<JsonNode> getOrganisationVocabs() throws IOException;
}
