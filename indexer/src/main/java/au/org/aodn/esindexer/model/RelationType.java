package au.org.aodn.esindexer.model;

import lombok.Getter;

@Getter
public enum RelationType {
    SELF("self"),
    LICENSE("license"),
    ;

    private final String value;

    RelationType(String value) {
        this.value = value;
    }
}
