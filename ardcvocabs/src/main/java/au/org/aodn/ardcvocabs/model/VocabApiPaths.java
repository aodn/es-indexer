package au.org.aodn.ardcvocabs.model;
import lombok.Getter;

@Getter
public enum VocabApiPaths {
    PARAMETER_VOCAB(
            "/aodn-parameter-category-vocabulary/%s/concept.json",
            "/aodn-parameter-category-vocabulary/%s/resource.json?uri=%s",
            "/aodn-discovery-parameter-vocabulary/%s/concept.json",
            "/aodn-discovery-parameter-vocabulary/%s/resource.json?uri=%s"
    ),
    PLATFORM_VOCAB(
            "/aodn-platform-category-vocabulary/%s/concept.json",
            "/aodn-platform-category-vocabulary/%s/resource.json?uri=%s",
            "/aodn-platform-vocabulary/%s/concept.json",
            "/aodn-platform-vocabulary/%s/resource.json?uri=%s"
    ),
    ORGANISATION_VOCAB(
            "/aodn-organisation-category-vocabulary/%s/concept",
            "/aodn-organisation-category-vocabulary/%s/resource.json?uri=%s",
            "/aodn-organisation-vocabulary/%s/concept",
            "/aodn-organisation-vocabulary/%s/resource.json?uri=%s"
    );

    private final String categoryApiTemplate;
    private final String categoryDetailsTemplate;
    private final String vocabApiTemplate;
    private final String vocabDetailsTemplate;

    VocabApiPaths(String categoryApiTemplate, String categoryDetailsTemplate, String vocabApiTemplate, String vocabDetailsTemplate) {
        this.categoryApiTemplate = categoryApiTemplate;
        this.categoryDetailsTemplate = categoryDetailsTemplate;
        this.vocabApiTemplate = vocabApiTemplate;
        this.vocabDetailsTemplate = vocabDetailsTemplate;
    }
}
