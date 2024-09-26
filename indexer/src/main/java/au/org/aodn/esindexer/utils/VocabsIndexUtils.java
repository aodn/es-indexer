package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.service.VocabService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;


@Slf4j
@Profile("!test") // exclude this class when using 'test' profile
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

    @PostConstruct
    public void init() {
        // this could take a few minutes to complete, in development, you can skip it with -Dapp.initialiseVocabsIndex=false
        // you can call /api/v1/indexer/ext/vocabs/populate endpoint to manually refresh the vocabs index, without waiting for the scheduled task
        if (initialiseVocabsIndex) {
            log.info("Initialising {}", vocabsIndexName);
            // non-blocking async method for populating vocabs index data when starting the app
            log.info("Starting async vocabs data fetching process...");
            CompletableFuture.runAsync(() -> {
                vocabService.populateVocabsData();
            });
            log.info("Vocabs data fetching process started in the background.");
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() throws IOException {
        log.info("Refreshing ARDC vocabularies data");
        // clear existing caches
        vocabService.clearParameterVocabCache();
        vocabService.clearPlatformVocabCache();
        vocabService.clearOrganisationVocabCache();
        // populate latest vocabs
        vocabService.populateVocabsData();
        // update the caches
        vocabService.getParameterVocabs();
        vocabService.getPlatformVocabs();
        vocabService.getOrganisationVocabs();
    }
}
