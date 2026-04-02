package au.org.aodn.datadiscoveryai.enums;
import lombok.Getter;

@Getter
public enum AiEnhancementSummaryField {
    AI_DESCRIPTION("ai:description"),
    AI_UPDATE_FREQUENCY("ai:update_frequency");

    private final String fieldName;

    AiEnhancementSummaryField(String fieldName) {
        this.fieldName = fieldName;
    }
}
