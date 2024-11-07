package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.GeometryUtils;
import au.org.aodn.esindexer.utils.VocabsIndexUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Configuration
@EnableRetry
@EnableAsync
public class IndexerConfig {

    @Value("${app.geometry.enableGridSpatialExtents:false}")
    protected boolean girdSpatialExtents;

    @Value("${app.geometry.coastalPrecision:0.04}")
    protected double coastalPrecision;

    @PostConstruct
    public void init() {
        GeometryUtils.setCoastalPrecision(coastalPrecision);
        GeometryUtils.init();
    }

    @PreDestroy
    public void cleanUp() {
        // Clean up resources
    }
    /**
     * We need to create component here because we do not want to run test with real http connection
     * that depends on remote site. The test config need to create an instance of bean for testing
     *
     * @return A bean of VocabsUtils
     */
    @Bean
    @ConditionalOnMissingBean(VocabsIndexUtils.class)
    public VocabsIndexUtils createVocabsUtils() {
        return new VocabsIndexUtils();
    }
    /**
     * This executor is used to limit the number of concurrent call to index metadata so not to flood the
     * geonetwork. This is useful because the geonetwork do not care about re-index call it invoke, hence
     * the elastic of geonetwork may be flooded by its re-index call.
     *
     * A small thread size is require to not overload the geonetwork.
     *
     * @return - An async task executor with blocking queue to stop too many request. This is a limited queue
     * and will throw error if new task cannot be added. It is up to the client to implement retry of the
     * same task. Indexer will not help you to queue it.
     */
    @Bean(name = "asyncIndexMetadata")
    public Executor taskExecutor(
            @Value("${app.indexing.pool.core:2}") Integer coreSize,
            @Value("${app.indexing.pool.max:3}") Integer coreMax,
            @Value("${app.indexing.pool.capacity:10}") Integer maxCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);         // Number of concurrent threads
        executor.setMaxPoolSize(coreMax);           // Max number of concurrent threads
        executor.setQueueCapacity(maxCapacity);     // Size of the queue
        executor.setThreadNamePrefix("Async-");
        executor.initialize();

        return executor;
    }
}
