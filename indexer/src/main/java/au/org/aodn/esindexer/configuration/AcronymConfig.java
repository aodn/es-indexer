package au.org.aodn.esindexer.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "elasticsearch.acronyms")
public class AcronymConfig {
    // ES synonyms set ID; must match the schema's acronym_synonym_filter, which references it as:
    //   "synonyms_set": "portal-acronyms"
    private String name = "portal-acronyms";
    private List<String> values = new ArrayList<>();
}
