package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Owns the acronym synonyms use-case: pushing the configured acronyms into the ES synonyms set. */
@Slf4j
public class AcronymService {

    private final ElasticsearchClient portalElasticsearchClient;
    private final String synonymSetName;
    private final List<String> acronyms;

    public AcronymService(
            String synonymSetName,
            List<String> acronyms,
            ElasticsearchClient portalElasticsearchClient) {
        this.synonymSetName = synonymSetName;
        this.acronyms = acronyms;
        this.portalElasticsearchClient = portalElasticsearchClient;
    }
    /**
     * Push the configured acronyms into the ES synonyms set (creates or fully replaces it; overwrites, never appends).
     * Triggered on a full reindex or by POST /api/v1/indexer/index/acronyms — live update, no reindex required.
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
