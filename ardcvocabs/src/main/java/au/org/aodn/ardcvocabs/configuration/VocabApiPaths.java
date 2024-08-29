package au.org.aodn.ardcvocabs.configuration;

import lombok.Getter;

@Getter
public enum VocabApiPaths {
    PARAMETER_VOCAB(
            "/aodn-parameter-category-vocabulary/version-2-1/concept.json",
            "/aodn-discovery-parameter-vocabulary/version-1-6/concept.json",
            "/aodn-discovery-parameter-vocabulary/version-1-6/resource.json?uri=%s"
    ),
    PLATFORM_VOCAB(
            "/aodn-platform-category-vocabulary/version-1-2/concept.json",
            "/aodn-platform-vocabulary/version-6-1/concept.json",
            "/aodn-platform-vocabulary/version-6-1/resource.json?uri=%s"
    ),
    ORGANISATION_VOCAB(
            "/aodn-organisation-category-vocabulary/version-2-5/concept",
            "/aodn-organisation-vocabulary/version-2-5/concept",
            "/aodn-organisation-vocabulary/version-2-5/resource.json?uri=%s"
    );

    private final String categoryVocabApiPath;
    private final String vocabApiPath;
    private final String vocabDetailsApiPath;

    VocabApiPaths(String categoryVocabApiPath, String vocabApiPath, String vocabDetailsApiPath) {
        this.categoryVocabApiPath = categoryVocabApiPath;
        this.vocabApiPath = vocabApiPath;
        this.vocabDetailsApiPath = vocabDetailsApiPath;
    }
}
