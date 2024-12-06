package au.org.aodn.ardcvocabs.model;
import lombok.Getter;

@Getter
public enum VocabApiPaths {
    PARAMETER_VOCAB(
            "/aodn-parameter-category-vocabulary/current/concept.json",
            "/aodn-parameter-category-vocabulary/current/resource.json?uri=%s",
            "/aodn-discovery-parameter-vocabulary/current/concept.json",
            "/aodn-discovery-parameter-vocabulary/current/resource.json?uri=%s"
    ),
    PLATFORM_VOCAB(
            "/aodn-platform-category-vocabulary/current/concept.json",
            "/aodn-platform-category-vocabulary/current/resource.json?uri=%s",
            "/aodn-platform-vocabulary/current/concept.json",
            "/aodn-platform-vocabulary/current/resource.json?uri=%s"
    ),
    ORGANISATION_VOCAB(
            "/aodn-organisation-category-vocabulary/current/concept.json",
            "/aodn-organisation-category-vocabulary/current/resource.json?uri=%s",
            "/aodn-organisation-vocabulary/current/concept.json",
            "/aodn-organisation-vocabulary/current/resource.json?uri=%s"
    );

    private final String vocabCategoryApiPath;
    private final String vocabCategoryDetailsApiPath;
    private final String vocabApiPath;
    private final String vocabDetailsApiPath;

    VocabApiPaths(String vocabCategoryApiPath, String vocabCategoryDetailsApiPath, String vocabApiPath, String vocabDetailsApiPath) {
        this.vocabCategoryApiPath = vocabCategoryApiPath;
        this.vocabCategoryDetailsApiPath = vocabCategoryDetailsApiPath;
        this.vocabApiPath = vocabApiPath;
        this.vocabDetailsApiPath = vocabDetailsApiPath;
    }
}
