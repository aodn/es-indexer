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

    enum VocabType {
        AODN_DISCOVERY_PARAMETER_VOCABS(Names.AODN_DISCOVERY_PARAMETER_VOCABS),
        AODN_PLATFORM_VOCABS(Names.AODN_PLATFORM_VOCABS),
        AODN_ORGANISATION_VOCABS(Names.AODN_ORGANISATION_VOCABS);
        // We need constant string for @Cacheable
        public static class Names {
            public static final String AODN_DISCOVERY_PARAMETER_VOCABS = "parameter_vocabs";
            public static final String AODN_PLATFORM_VOCABS = "platform_vocabs";
            public static final String AODN_ORGANISATION_VOCABS = "organisation_vocabs";
        }

        final String type;

        VocabType(String type) {
            this.type = type;
        }
    }
    List<String> extractVocabLabelsFromThemes(List<ThemesModel> themes, VocabType vocabType) throws IOException;
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
