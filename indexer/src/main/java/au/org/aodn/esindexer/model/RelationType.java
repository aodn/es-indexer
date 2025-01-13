package au.org.aodn.esindexer.model;

import lombok.Getter;
// TODO: Should use some lib provide value or move it to stacmodel folder
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
    WFS("wfs"),
    WMS("wms"),
    ;

    private final String value;

    RelationType(String value) {
        this.value = value;
    }
}
