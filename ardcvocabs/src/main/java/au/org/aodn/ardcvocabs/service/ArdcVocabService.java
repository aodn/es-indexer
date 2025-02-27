package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.ArdcCurrentPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;

import java.util.List;

public interface ArdcVocabService {
    boolean isVersionEquals(ArdcCurrentPaths path, String version);
    List<VocabModel> getARDCVocabByType(ArdcCurrentPaths path);
}
