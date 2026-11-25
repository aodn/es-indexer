package au.org.aodn.cloudoptimized.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

/**
 * Check a notebook url with a HEAD request. Only a final 2xx response is valid, anything else, including
 * 429, 5xx, timeout and I/O error, is invalid.
 */
@Slf4j
public class HttpNotebookUrlValidator implements NotebookUrlValidator {

    protected final RestTemplate restTemplate;
    protected final boolean networkCheckEnabled;

    public HttpNotebookUrlValidator(RestTemplate restTemplate, boolean networkCheckEnabled) {
        this.restTemplate = restTemplate;
        this.networkCheckEnabled = networkCheckEnabled;
    }

    public static RestTemplate createRestTemplate(int timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(timeout);

        return new RestTemplate(factory);
    }

    @Override
    public NotebookUrlCheck check(String url) {
        if (url == null || url.isBlank()) {
            return NotebookUrlCheck.invalid("url is empty");
        }

        final URI uri;
        try {
            uri = new URI(url.trim());
        }
        catch (URISyntaxException e) {
            return NotebookUrlCheck.invalid("not a valid uri");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            return NotebookUrlCheck.invalid("not a http(s) url");
        }

        if (!networkCheckEnabled) {
            return NotebookUrlCheck.ok();
        }

        try {
            // Void.class, we only care about the status. The default error handler throws on 4xx and 5xx.
            restTemplate.exchange(uri, HttpMethod.HEAD, null, Void.class);
            return NotebookUrlCheck.ok();
        }
        catch (HttpStatusCodeException e) {
            return NotebookUrlCheck.invalid("HTTP " + e.getStatusCode().value());
        }
        catch (RestClientException e) {
            return NotebookUrlCheck.invalid("request failed: " + e.getClass().getSimpleName());
        }
    }
}
