package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LanguageModel {
    protected String code;
    protected String name;
}
