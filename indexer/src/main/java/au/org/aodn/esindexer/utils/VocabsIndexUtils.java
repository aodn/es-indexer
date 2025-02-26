package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.model.ArdcCurrentPaths;
import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.esindexer.service.VocabService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.List;

@Slf4j
public class VocabsIndexUtils {
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

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledRefreshVocabsData() throws IOException {
        log.info("Refreshing ARDC vocabularies data");
        boolean anyDiff = false;

        try {
            for (ArdcCurrentPaths path : ArdcCurrentPaths.values()) {
                if (path == ArdcCurrentPaths.PARAMETER_VOCAB) {
                    String docVer = getDocVersion(vocabService.getParameterVocabs());
                    if (!ardcVocabService.isVersionEquals(ArdcCurrentPaths.PARAMETER_VOCAB, docVer)) {
                        anyDiff = true;
                        break;
                    }
                }
                else if (path == ArdcCurrentPaths.PLATFORM_VOCAB) {
                    String docVer = getDocVersion(vocabService.getPlatformVocabs());
                    if (!ardcVocabService.isVersionEquals(ArdcCurrentPaths.PLATFORM_VOCAB, docVer)) {
                        anyDiff = true;
                        break;
                    }
                }
                else if (path == ArdcCurrentPaths.ORGANISATION_VOCAB) {
                    String docVer = getDocVersion(vocabService.getOrganisationVocabs());
                    if (!ardcVocabService.isVersionEquals(ArdcCurrentPaths.ORGANISATION_VOCAB, docVer)) {
                        anyDiff = true;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            // Any exception reload the vocab
            anyDiff = true;
        }

        if(anyDiff) {
            vocabService.populateVocabsData();
            refreshCaches();
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
