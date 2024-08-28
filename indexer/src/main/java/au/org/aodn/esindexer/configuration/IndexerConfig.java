package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.VocabsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableRetry
@EnableAsync
public class IndexerConfig {
    /**
     * We need to create component here because we do not want to run test with real http connection
     * that depends on remote site. The test config need to create an instance of bean for testing
     *
     * @return A bean of VocabsUtils
     */
    @Bean
    @ConditionalOnMissingBean(VocabsUtils.class)
    public VocabsUtils createVocabsUtils() {
        return new VocabsUtils();
    }
    /**
     * This executor is used to limit the number of concurrent call to index metadata so not to flood the
     * geonetwork. This is useful because the geonetwork do not care about re-index call it invoke, hence
     * the elastic of geonetwork may be flooded by its re-index call.
     * @return - An async task executor
     */
    @Bean(name = "asyncIndexMetadata")
    public Executor taskExecutor(
            @Value("${app.indexing.pool.core:5}") Integer coreSize,
            @Value("${app.indexing.pool.max:10}") Integer coreMax) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);     // Number of concurrent threads
        executor.setMaxPoolSize(coreMax);       // Max number of concurrent threads
        executor.setQueueCapacity(5000);        // Size of the queue
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}
