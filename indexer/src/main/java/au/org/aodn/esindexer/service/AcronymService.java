package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Owns the acronym synonyms use-case: pushing the configured acronyms into the ES synonyms set. */
@Slf4j
@Service
public class AcronymService {

    private final String synonymSetName;
    private final List<String> acronyms;
    private final ElasticsearchClient portalElasticsearchClient;

    public AcronymService(Environment environment,
                          @Qualifier("portalElasticsearchClient") ElasticsearchClient portalElasticsearchClient) {
        // ES synonyms set ID; must match the schema's acronym_synonym_filter "synonyms_set" ("portal-acronyms").
        this.synonymSetName = environment.getProperty("elasticsearch.acronyms.name", "portal-acronyms");
        // Read the YAML list elasticsearch.acronyms.values into List<String> (Binder handles sequences; @Value can't).
        this.acronyms = Binder.get(environment)
                .bind("elasticsearch.acronyms.values", Bindable.listOf(String.class))
                .orElse(List.of());
        this.portalElasticsearchClient = portalElasticsearchClient;
    }

    /**
     * Push the configured acronyms into the ES synonyms set (creates or fully replaces it; overwrites, never appends).
     * Live update, no reindex required.
     */
    public void syncAcronyms() throws IOException {
        List<SynonymRule> synonymRules = acronyms.stream()
                .map(rule -> SynonymRule.of(r -> r.synonyms(rule)))
                .collect(Collectors.toList());
        portalElasticsearchClient.synonyms().putSynonym(b -> b
                .id(synonymSetName)
                .synonymsSet(synonymRules));
        log.info("Replaced synonyms set '{}' with {} rules", synonymSetName, synonymRules.size());
    }
}
