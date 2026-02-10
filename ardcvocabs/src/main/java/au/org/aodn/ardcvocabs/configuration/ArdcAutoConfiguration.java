package au.org.aodn.ardcvocabs.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(ArdcVocabService.class)
@EnableRetry  // Enable retry support
public class ArdcAutoConfiguration {

    protected CountDownLatch limit = new CountDownLatch(1);

    @Bean
    public ArdcVocabService createArdcVocabsService(RetryTemplate retryTemplate) {
        log.info("Create ArdcVocabsService");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // e.g., 5 seconds
        requestFactory.setReadTimeout(10000);      // e.g., 10 seconds

        RestTemplate template = new RestTemplate(requestFactory);

        // Set default User-Agent, pretend user browser to avoid being blocked by remote server, but there are still rate limit
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(
                    HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
            );
            return execution.execute(request, body);
        });

        // Add delay before every request (most effective simple fix)
        template.getInterceptors().add((request, body, execution) -> {
            try {
                limit.await(2, TimeUnit.SECONDS); // 1.2 seconds – adjust based on observed limits
            } catch (InterruptedException e) {
                // Ignore
            }
            return execution.execute(request, body);
        });

        return new ArdcVocabServiceImpl(template, retryTemplate);
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
