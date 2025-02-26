package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.model.ArdcCurrentPaths;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class VocabIndexScheduler {
    protected VocabService vocabService;
    protected ArdcVocabService ardcVocabService;

    @Value("${elasticsearch.vocabs_index.name}")
    String vocabsIndexName;

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
    // Avoid this run automatically in test
    @Profile("!test")
    @PostConstruct
    public void init() throws IOException {
        if(checkVersionDiff()) {
            log.info("Refreshing ARDC vocabularies data due to version diff");
            CompletableFuture<Void> f = vocabService.populateVocabsDataAsync();
            f.thenRun(this::refreshCaches);
        }
        else {
            log.info("ARDC vocabularies data version same, ignore refresh");
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() throws IOException {
        if(checkVersionDiff()) {
            log.info("Refreshing ARDC vocabularies data due to version diff");
            vocabService.populateVocabsData();
            refreshCaches();
        }
        else {
            log.info("ARDC vocabularies data version same, ignore refresh");
        }
    }

    protected boolean checkVersionDiff() {
        try {
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
        } catch (IOException e) {
            // Any exception reload the vocab
            return false;
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
