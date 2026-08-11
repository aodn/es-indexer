package au.org.aodn.ardcvocabs.configuration;

import au.org.aodn.ardcvocabs.service.ArdcVocabService;
import au.org.aodn.ardcvocabs.service.ArdcVocabServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(ArdcVocabService.class)
@EnableRetry  // Enable retry support
public class ArdcAutoConfiguration {

    protected CountDownLatch limit = new CountDownLatch(1);

    // the delay between requests unit as ms
    protected Long requestDelayMs = 1000L;
    // the max number of retry for HTTP 429, 5xx, and connection/read timeouts. No retry apply on other 4xx response like HTTP 404
    protected Integer maxAttempts = 5;
    // the backoff time for retry-after-retryable failures, unit as seconds
    protected Long backoffInitialSeconds = 30L;

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
                limit.await(requestDelayMs, TimeUnit.MILLISECONDS); // 1 seconds – adjust based on observed limits
            } catch (InterruptedException e) {
                log.error("Vocab harvest interrupted.");
            }
            return execution.execute(request, body);
        });

        return new ArdcVocabServiceImpl(template, retryTemplate);
    }

    /**
     * Define the retry template for different cases:
     * Retry (retryablePolicy):
     *   - HTTP 429 (TooManyRequests)
     *   - HTTP 5XX (HttpServerErrorException)
     *   - Connect/read timeout or network failure (ResourceAccessException)
     * Not Retry (nonRetryablePolicy)
     *   - HTTP 404 (NotFound)
     *   - Other HTTP 4xx (Other HttpClientErrorException)
     *   - Programming/payload/unknown failure (fallback to anything else)
     * */
    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .exponentialBackoff(
                        TimeUnit.SECONDS.toMillis(backoffInitialSeconds),
                        2,
                        // time delay for 5 attempts: 30 -> 60 -> 120 -> 240
                        TimeUnit.SECONDS.toMillis(240))
                .retryOn(exception ->
                        exception instanceof HttpClientErrorException.TooManyRequests
                                || exception instanceof HttpServerErrorException
                                || exception instanceof ResourceAccessException)
                .build();
    }
}
