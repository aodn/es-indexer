package au.org.aodn.ardcvocabs.model;

import lombok.Getter;

@Getter
public enum ArdcCurrentPaths {
    PARAMETER_VOCAB(
            "/aodn-parameter-category-vocabulary/current/concept.json",
            "/aodn-discovery-parameter-vocabulary/current/concept.json"
    ),
    PLATFORM_VOCAB(
            "/aodn-platform-category-vocabulary/current/concept.json",
            "/aodn-platform-vocabulary/current/concept.json"
    ),
    ORGANISATION_VOCAB(
            "/aodn-organisation-category-vocabulary/current/concept.json",
            "/aodn-organisation-vocabulary/current/concept.json"
    );


    private final String categoryCurrent;
    private final String vocabCurrent;

    ArdcCurrentPaths(String categoryRoot, String vocabRoot) {
        String rootHostname = "https://vocabs.ardc.edu.au/repository/api/lda/aodn";
        this.categoryCurrent = rootHostname + categoryRoot;
        this.vocabCurrent = rootHostname + vocabRoot;
    }
}
