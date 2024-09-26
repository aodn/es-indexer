package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.service.VocabService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;


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

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() throws IOException {
        // Check if the initialiseVocabsIndex flag is enabled
        if (initialiseVocabsIndex) {
            if (isTestProfileActive()) {
                log.info("Initialising {} synchronously for test profile", vocabsIndexName);
                vocabService.populateVocabsData();
            } else {
                log.info("Initialising {} asynchronously", vocabsIndexName);
                vocabService.populateVocabsDataAsync();
            }
        }
    }

    private boolean isTestProfileActive() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() throws IOException {
        log.info("Refreshing ARDC vocabularies data");

        // call synchronous populating method, otherwise existing vocab caches will be emptied while new data hasn't been fully processed yet.
        vocabService.populateVocabsData();

        // clear existing caches
        vocabService.clearParameterVocabCache();
        vocabService.clearPlatformVocabCache();
        vocabService.clearOrganisationVocabCache();

        // update the caches
        vocabService.getParameterVocabs();
        vocabService.getPlatformVocabs();
        vocabService.getOrganisationVocabs();
    }
}
