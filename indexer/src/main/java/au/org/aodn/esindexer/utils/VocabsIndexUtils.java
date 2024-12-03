package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.service.VocabService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;


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


    @PostConstruct
    public void init() throws IOException {
        // Check if the initialiseVocabsIndex flag is enabled
        if (initialiseVocabsIndex) {
            log.info("Initialising {} asynchronously", vocabsIndexName);
            vocabService.populateVocabsDataAsync(ardcVocabService.getResolvedPathCollection());
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() throws IOException {
        log.info("Refreshing ARDC vocabularies data");

        // call synchronous populating method, otherwise existing vocab caches will be emptied while new data hasn't been fully processed yet.
        vocabService.populateVocabsData(ardcVocabService.getResolvedPathCollection());

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
