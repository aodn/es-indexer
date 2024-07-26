package au.org.aodn.esindexer.model;

import lombok.Getter;

@Getter
public enum MediaType {
    TEXT_HTML("text/html"),
    TEXT_XML("text/xml"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    APPLICATION_JSON("application/json"),
    ;

    private final String value;

    MediaType(String value) {
        this.value = value;
    }
}
