package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.VocabsUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

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
     *
     * @return - An async task executor with blocking queue to stop too many request.
     */
    @Bean(name = "asyncIndexMetadata")
    public Executor taskExecutor(
            @Value("${app.indexing.pool.core:3}") Integer coreSize,
            @Value("${app.indexing.pool.max:6}") Integer coreMax) {

        return new ThreadPoolExecutor(
                coreSize,
                coreMax,
                0L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy() // Rejection policy
        );
    }
}
