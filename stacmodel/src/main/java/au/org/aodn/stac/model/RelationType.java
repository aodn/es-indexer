package au.org.aodn.stac.model;

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
    WFS("wfs"),
    WMS("wms"),
    ;

    private final String value;

    RelationType(String value) {
        this.value = value;
    }
}
