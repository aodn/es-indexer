package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.PathName;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.stac.model.ContactsModel;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface VocabService {
    List<String> extractVocabLabelsFromThemes(List<ThemesModel> themes, String vocabType) throws IOException;
    List<String> extractOrganisationVocabLabelsFromThemes(List<ThemesModel> themes) throws IOException;
    List<VocabModel> getMappedOrganisationVocabsFromContacts(List<ContactsModel> contacts) throws IOException;
    void populateVocabsData(Map<String, Map<PathName, String>> resolvedPathCollection) throws IOException;
    void populateVocabsDataAsync(Map<String, Map<PathName, String>> resolvedPathCollection);
    void clearParameterVocabCache();
    void clearPlatformVocabCache();
    void clearOrganisationVocabCache();
    List<JsonNode> getParameterVocabs() throws IOException;
    List<JsonNode> getPlatformVocabs() throws IOException;
    List<JsonNode> getOrganisationVocabs() throws IOException;
}
