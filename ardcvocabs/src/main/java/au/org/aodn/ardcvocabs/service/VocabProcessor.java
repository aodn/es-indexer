package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.VocabModel;

import java.util.List;

public interface VocabProcessor {
    List<VocabModel> getParameterVocabs(String vocabApiBase);
    List<VocabModel> getPlatformVocabs(String vocabApiBase);
    List<VocabModel> getOrganisationVocabs(String vocabApiBase);
}
