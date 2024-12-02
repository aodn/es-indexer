package au.org.aodn.ardcvocabs.model;

import lombok.Getter;

@Getter
public enum ArdcRootPaths {
    PARAMETER_VOCAB(
            "/viewById/24",
            "/viewById/22"
    ),
    PLATFORM_VOCAB(
            "/viewById/26",
            "/viewById/25"
    ),
    ORGANISATION_VOCAB(
            "/viewById/29",
            "/viewById/28"
    );


    private final String categoryRoot;
    private final String vocabRoot;

    ArdcRootPaths(String categoryRoot, String vocabRoot) {
        String rootHostname = "https://vocabs.ardc.edu.au";
        this.categoryRoot = rootHostname + categoryRoot;
        this.vocabRoot = rootHostname + vocabRoot;
    }
}
