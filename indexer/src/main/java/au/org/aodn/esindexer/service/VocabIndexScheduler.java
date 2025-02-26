package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.ArdcCurrentPaths;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.exception.IndexNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Random;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class VocabIndexScheduler {
    protected final Random random = new Random();
    protected VocabService vocabService;
    protected ArdcVocabService ardcVocabService;

    @Value("${elasticsearch.vocabs_index.enableRefreshDelay:true}")
    protected Boolean enableRefreshDelay;

    @Autowired
    public void setVocabService(VocabService vocabService) {
        this.vocabService = vocabService;
    }

    @Autowired
    public void setArdcVocabService(ArdcVocabService ardcVocabService) {
        this.ardcVocabService = ardcVocabService;
    }

    protected static String getDocVersion(List<JsonNode> nodes) {
        if(!nodes.isEmpty() && nodes.get(0).has("version")) {
            return nodes.get(0).get("version").asText();
        }
        return "";
    }

    @PostConstruct
    public void init() throws IOException {
        try {
            if (checkVersionDiff()) {
                // Version exist means we have outdated docs, we can do a async load and refresh cache in the
                // background to speed up the start.
                log.info("Async refresh ARDC vocabularies due to version diff");
                CompletableFuture<Void> f = vocabService.populateVocabsDataAsync(0);
                f.thenRun(this::refreshCaches);
            } else {
                log.info("ARDC vocabularies data version same, download skipped");
            }
        }
        catch(IndexNotFoundException ioe) {
            // Check version failed due to index not exist, it can be a new installed es-instance, populate with sync
            vocabService.populateVocabsData();
        }
        refreshCaches();
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() throws IOException {
        try {
            if (checkVersionDiff()) {
                if(enableRefreshDelay) {
                    // Apply a random 0-30 minutes delay on refresh, this is used to avoid multiple instance
                    // refresh the index the same time and step on each other. In case of version change, then
                    // one es-indexer should create the index before the other instance got a chance to download index
                    // Not a perfect solution, but we do not have a global lock in place (need share db or share instance)
                    log.info("Async refresh ARDC vocabularies due to version diff");
                    CompletableFuture<Void> f = vocabService.populateVocabsDataAsync(random.nextInt(30));
                    f.thenRun(this::refreshCaches);
                }
                else {
                    log.info("Refresh ARDC vocabularies due to version diff");
                    vocabService.populateVocabsData();
                    refreshCaches();
                }
            } else {
                log.info("ARDC vocabularies data version same, download skipped");
            }
        }
        catch(IndexNotFoundException inf) {
            // Index deleted for some reason, re-populate it.
            vocabService.populateVocabsData();
            refreshCaches();
        }
    }

    protected boolean checkVersionDiff() throws IOException {
        for (ArdcCurrentPaths path : ArdcCurrentPaths.values()) {
            if (path == ArdcCurrentPaths.PARAMETER_VOCAB) {
                String docVer = getDocVersion(vocabService.getParameterVocabs());
                if (!ardcVocabService.isVersionEquals(ArdcCurrentPaths.PARAMETER_VOCAB, docVer)) {
                    return true;
                }
            }
            else if (path == ArdcCurrentPaths.PLATFORM_VOCAB) {
                String docVer = getDocVersion(vocabService.getPlatformVocabs());
                if (!ardcVocabService.isVersionEquals(ArdcCurrentPaths.PLATFORM_VOCAB, docVer)) {
                    return true;
                }
            }
            else if (path == ArdcCurrentPaths.ORGANISATION_VOCAB) {
                String docVer = getDocVersion(vocabService.getOrganisationVocabs());
                if (!ardcVocabService.isVersionEquals(ArdcCurrentPaths.ORGANISATION_VOCAB, docVer)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void refreshCaches() {
        try {
            log.info("Clearing existing caches...");
            vocabService.clearParameterVocabCache();
            vocabService.clearPlatformVocabCache();
            vocabService.clearOrganisationVocabCache();

            log.info("Updating vocabularies caches...");
            vocabService.getParameterVocabs();
            vocabService.getPlatformVocabs();
            vocabService.getOrganisationVocabs();
        } catch (IOException e) {
            log.error("Error refreshing caches: ", e);
        }
    }
}
