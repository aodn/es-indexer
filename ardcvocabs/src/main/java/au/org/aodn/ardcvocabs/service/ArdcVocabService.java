package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;

import java.util.List;

public interface ArdcVocabService {
    List<VocabModel> getVocabTreeFromArdcByType(VocabApiPaths vocabApiPaths);
}
