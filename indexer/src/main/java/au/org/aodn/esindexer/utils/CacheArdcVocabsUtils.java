package au.org.aodn.esindexer.utils;

import au.org.aodn.ardcvocabs.model.CategoryVocabModel;
import au.org.aodn.ardcvocabs.service.ArdcVocabsService;
import au.org.aodn.esindexer.configuration.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Component
public class CacheArdcVocabsUtils {
    @Value(AppConstants.AODN_DISCOVERY_PARAMETER_VOCAB_API)
    protected String vocabApi;

    @Autowired
    ArdcVocabsService ardcVocabsService;

    /*
    call it after the bean is created to populate the cache
     */
    @PostConstruct
    @Cacheable(AppConstants.AODN_DISCOVERY_CATEGORIES_CACHE)
    public List<CategoryVocabModel> getCachedData() {
        return fetchData();
    }

    protected List<CategoryVocabModel> fetchData() {
        log.info("Fetching AODN Discovery Parameter Vocabularies");
        return ardcVocabsService.getParameterCategory(vocabApi);
    }

    // refresh every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshCache() {
        log.info("Refreshing AODN Discovery Parameter Vocabularies cache");
        clearCache();
    }

    @CacheEvict(value = AppConstants.AODN_DISCOVERY_CATEGORIES_CACHE, allEntries = true)
    public void clearCache() {
        // Intentionally empty; the annotation does the job
    }
}
