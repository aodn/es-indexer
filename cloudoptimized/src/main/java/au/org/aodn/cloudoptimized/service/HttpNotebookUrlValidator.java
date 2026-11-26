package au.org.aodn.cloudoptimized.service;

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
public class HttpNotebookUrlValidator implements NotebookUrlValidator {

    // The host should be cloud-optimised repo, and the "/" after the host keeps the rest of the url a path
    protected static final String GITHUB_PREFIX = "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/";
    protected static final String NOTEBOOK_SUFFIX = ".ipynb";

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

        final URI requestUri;
        try {
            requestUri = toAllowedUri(url.trim());
        }
        catch (URISyntaxException e) {
            return NotebookUrlCheck.invalid("not a valid uri");
        }
        if (requestUri == null) {
            return NotebookUrlCheck.invalid("not a cloud-optimised github notebook url");
        }

        if (!networkCheckEnabled) {
            return NotebookUrlCheck.ok();
        }

        try {
            // Void.class, we only care about the status. The default error handler throws on 4xx and 5xx.
            restTemplate.exchange(requestUri, HttpMethod.HEAD, null, Void.class);
            return NotebookUrlCheck.ok();
        }
        catch (HttpStatusCodeException e) {
            return NotebookUrlCheck.invalid("HTTP " + e.getStatusCode().value());
        }
        catch (RestClientException e) {
            return NotebookUrlCheck.invalid("request failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Rebuild the url from the constant prefix, so the scheme and host always come from a constant. Only a github
     * url to a notebook file is allowed, i.e. its path ends with .ipynb and it has no query or fragment.
     *
     * @param url - The trimmed url
     * @return The url to request, or null if it is not a github notebook url
     */
    protected static URI toAllowedUri(String url) throws URISyntaxException {
        if (url.startsWith(GITHUB_PREFIX)) {
            URI uri = new URI(GITHUB_PREFIX + url.substring(GITHUB_PREFIX.length()));
            if (uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getPath() != null
                    && uri.getPath().endsWith(NOTEBOOK_SUFFIX)) {
                return uri;
            }
        }
        return null;
    }
}
