package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.utils.VocabsUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
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
}
