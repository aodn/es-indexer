package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class CustomVersionsDto {
    @JsonProperty("organisationCategoryVersion")
    String organisationCategoryVersion;
    @JsonProperty("organisationVocabVersion")
    String organisationVocabVersion;
}
