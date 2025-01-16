package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.exception.ExtractingPathVersionsException;
import au.org.aodn.ardcvocabs.model.PathName;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.service.VocabService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.Map;


@Slf4j
public class VocabsIndexUtils {
    @Value("${elasticsearch.vocabs_index.name}")
    String vocabsIndexName;

    @Value("${app.initialiseVocabsIndex:true}")
    private boolean initialiseVocabsIndex;

    protected VocabService vocabService;
    @Autowired
    public void setVocabService(VocabService vocabService) {
        this.vocabService = vocabService;
    }

    protected ArdcVocabService ardcVocabService;
    @Autowired
    public void setArdcVocabService(ArdcVocabService ardcVocabService) {
        this.ardcVocabService = ardcVocabService;
    }

    /*
    The storedResolvedPathCollection is shared between the @PostConstruct method and the @Scheduled method.
    If the scheduledRefreshVocabsData method runs while init is still processing, there could be concurrency issues.
    To mitigate this, synchronize access to this shared resource with volatile keyword to ensure proper visibility.
    The volatile modifier guarantees that any thread that reads a field will see the most recently written value
     */
    private volatile Map<String, Map<PathName, String>> storedResolvedPathCollection;

    @PostConstruct
    public void init() throws IOException {
        // Check if the initialiseVocabsIndex flag is enabled
        if (initialiseVocabsIndex) {
            try {
                log.info("Initialising {} asynchronously", vocabsIndexName);
                storedResolvedPathCollection = ardcVocabService.getResolvedPathCollection();
                vocabService.populateVocabsDataAsync(storedResolvedPathCollection);
            } catch (ExtractingPathVersionsException e) {
                log.warn("Skip initialising vocabs with error: {}", e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() {
        try {
            log.info("Refreshing ARDC vocabularies data");
            Map<String, Map<PathName, String>> latestResolvedPathCollection = ardcVocabService.getResolvedPathCollection();

            if (!latestResolvedPathCollection.equals(storedResolvedPathCollection)) {
                log.info("Detected changes in the resolved path collection, updating vocabularies...");
                vocabService.populateVocabsData(latestResolvedPathCollection);
                refreshCaches();

                // update the head if there are new versions
                synchronized (this) {
                    storedResolvedPathCollection = latestResolvedPathCollection;
                    log.info("Updated storedResolvedPathCollection with the latest data.");
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
