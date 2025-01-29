package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.exception.ExtractingPathVersionsException;
import au.org.aodn.ardcvocabs.model.PathName;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.exception.IgnoreIndexingVocabsException;
import au.org.aodn.esindexer.service.VocabService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
public class VocabsIndexUtils {
    protected VocabService vocabService;
    protected ArdcVocabService ardcVocabService;

    @Value("${elasticsearch.vocabs_index.name}")
    String vocabsIndexName;

    @Value("${app.initialiseVocabsIndex:true}")
    protected boolean initialiseVocabsIndex;

    @Autowired
    public void setVocabService(VocabService vocabService) {
        this.vocabService = vocabService;
    }

    @Autowired
    public void setArdcVocabService(ArdcVocabService ardcVocabService) {
        this.ardcVocabService = ardcVocabService;
    }

    protected AtomicReference<Map<String, Map<PathName, String>>> storedResolvedPathCollection = new AtomicReference<>();

    @PostConstruct
    public void init() throws IOException {
        // Check if the initialiseVocabsIndex flag is enabled
        if (initialiseVocabsIndex) {
            try {
                log.info("Initialising {} asynchronously", vocabsIndexName);
                storedResolvedPathCollection.set(ardcVocabService.getResolvedPathCollection());
                vocabService.populateVocabsDataAsync(storedResolvedPathCollection.get());
            }
            catch (ExtractingPathVersionsException | IgnoreIndexingVocabsException e) {
                log.warn("Skip initialising vocabs with error: {}", e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() {
        try {
            log.info("Refreshing ARDC vocabularies data");
            Map<String, Map<PathName, String>> latestResolvedPathCollection = ardcVocabService.getResolvedPathCollection();

            if (!latestResolvedPathCollection.equals(storedResolvedPathCollection.get())) {
                log.info("Detected changes in the resolved path collection, updating vocabularies...");
                try {
                    vocabService.populateVocabsData(latestResolvedPathCollection);
                    refreshCaches();
                    // update the head if there are new versions
                    storedResolvedPathCollection.set(latestResolvedPathCollection);
                    log.info("Updated storedResolvedPathCollection with the latest data.");
                }
                catch (IgnoreIndexingVocabsException e) {
                    log.warn("Skip refreshing vocabs: {}", e.getMessage());
                }
            } else {
                log.info("No changes detected in the resolved path collection. Skip updating caches");
            }
        } catch (IOException e) {
            log.error("Error refreshing vocabularies data: ", e);
        }
    }

    private void refreshCaches() {
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
