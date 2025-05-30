package au.org.aodn.ardcvocabs.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(ArdcVocabService.class)
@EnableRetry  // Enable retry support
public class ArdcAutoConfiguration {

    @Bean
    public ArdcVocabService createArdcVocabsService(RetryTemplate retryTemplate) {
        log.info("Create ArdcVocabsService");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // e.g., 5 seconds
        requestFactory.setReadTimeout(10000);      // e.g., 10 seconds

        return new ArdcVocabServiceImpl(new RestTemplate(requestFactory), retryTemplate);
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configure retry policy (3 attempts)
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Configure backoff policy (exponential backoff starting at 1 second, doubling each time)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second
        backOffPolicy.setMultiplier(2); // 2x each retry
        backOffPolicy.setMaxInterval(5000); // max 5 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
