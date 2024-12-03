package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.PathName;
import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;

import java.util.List;
import java.util.Map;

public interface ArdcVocabService {
    Map<String, Map<PathName, String>> getResolvedPathCollection();
    List<VocabModel> getVocabTreeFromArdcByType(Map<PathName, String> resolvedPaths);
}
