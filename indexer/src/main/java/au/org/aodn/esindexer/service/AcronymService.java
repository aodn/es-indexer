package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.configuration.AcronymConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** Owns the acronym synonyms use-case: pushing the configured acronyms into the ES synonyms set. */
@Service
public class AcronymService {

    private final AcronymConfig acronymConfig;
    private final ElasticSearchIndexService elasticSearchIndexService;

    public AcronymService(AcronymConfig acronymConfig, ElasticSearchIndexService elasticSearchIndexService) {
        this.acronymConfig = acronymConfig;
        this.elasticSearchIndexService = elasticSearchIndexService;
    }

    /** Push the configured acronyms into the ES synonyms set. Live update, no reindex required. */
    public void syncAcronyms() throws IOException {
        elasticSearchIndexService.replaceSynonymSet(acronymConfig.getName(), acronymConfig.getValues());
    }
}
