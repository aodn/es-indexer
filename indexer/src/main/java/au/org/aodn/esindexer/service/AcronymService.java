package au.org.aodn.esindexer.service;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/** Owns the acronym synonyms use-case: pushing the configured acronyms into the ES synonyms set. */
@Service
public class AcronymService {

    private final String synonymSetName;
    private final List<String> acronyms;
    private final ElasticSearchIndexService elasticSearchIndexService;

    public AcronymService(Environment environment, ElasticSearchIndexService elasticSearchIndexService) {
        // ES synonyms set ID; must match the schema's acronym_synonym_filter "synonyms_set" ("portal-acronyms").
        this.synonymSetName = environment.getProperty("elasticsearch.acronyms.name", "portal-acronyms");
        // Read the YAML list elasticsearch.acronyms.values into List<String> (Binder handles sequences; @Value can't).
        this.acronyms = Binder.get(environment)
                .bind("elasticsearch.acronyms.values", Bindable.listOf(String.class))
                .orElse(List.of());
        this.elasticSearchIndexService = elasticSearchIndexService;
    }

    /** Push the configured acronyms into the ES synonyms set. Live update, no reindex required. */
    public void syncAcronyms() throws IOException {
        elasticSearchIndexService.replaceSynonymSet(synonymSetName, acronyms);
    }
}
