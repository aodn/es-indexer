package au.org.aodn.datadiscoveryai.enums;

import lombok.Getter;

@Getter
public enum AIModel {
    KEYWORK_CLASSIFICATION("keyword_classification"),
    LINK_GROUPING("link_grouping"),
    DELIVERY_CLASSIFICATION("delivery_classification"),
    DESCRIPTION_FORMATTING("description_formatting"),
    ;

    private final String value;

    AIModel(String value) {
        this.value = value;
    }
}
