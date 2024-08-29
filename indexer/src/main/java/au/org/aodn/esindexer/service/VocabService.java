package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

public interface VocabService {
    List<VocabModel> getParameterVocabsFromArdc(String vocabApiBase);
    List<VocabModel> getPlatformVocabsFromArdc(String vocabApiBase);
    List<VocabModel> getOrganisationVocabsFromArdc(String vocabApiBase);
    List<String> getVocabLabelsByThemes(List<ThemesModel> themes, String vocabType) throws IOException;
    void populateVocabsData() throws IOException;
    void clearOrganisationVocabsCache();
    void clearPlatformVocabsCache();
    void clearParameterVocabsCache();
    List<JsonNode> getOrganisationVocabsFromEs() throws IOException;
    List<JsonNode> getPlatformVocabsFromEs() throws IOException;
    List<JsonNode> getParameterVocabsFromEs() throws IOException;
}
