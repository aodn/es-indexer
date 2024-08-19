package au.org.aodn.esindexer.model;

import lombok.Getter;

@Getter
public enum RelationType {
    SELF("self"),
    PARENT("parent"),
    SIBLING("sibling"),
    CHILD("child"),
    LICENSE("license"),
    RELATED("related"),
    DESCRIBEDBY("describedby"),
    ICON("icon"),
    PREVIEW("preview"),
    ;

    private final String value;

    RelationType(String value) {
        this.value = value;
    }
}
