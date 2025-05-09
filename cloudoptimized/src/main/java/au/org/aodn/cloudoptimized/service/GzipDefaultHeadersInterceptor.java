package au.org.aodn.cloudoptimized.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNullApi;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class GzipDefaultHeadersInterceptor implements ClientHttpRequestInterceptor {

    private final HttpHeaders defaultHeaders;

    public GzipDefaultHeadersInterceptor(String apiKey) {
        this.defaultHeaders = new HttpHeaders();
        defaultHeaders.add(HttpHeaders.CONTENT_TYPE, "application/json");
        defaultHeaders.add(HttpHeaders.ACCEPT, "application/json");
        defaultHeaders.add(HttpHeaders.ACCEPT_ENCODING, "gzip"); // Request GZIP-compressed responses
        defaultHeaders.add("X-API-Key", apiKey);
        // Add more default headers as needed
        // Note: Content-Encoding and Accept-Encoding are set dynamically in intercept
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        // Add default headers
        HttpHeaders headers = request.getHeaders();
        headers.addAll(defaultHeaders);

        // Execute request (no compression of request body)
        ClientHttpResponse response = execution.execute(request, body);

        // Decompress response if GZIP-encoded
        if ("gzip".equalsIgnoreCase(response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING))) {
            return new GzipClientHttpResponseWrapper(response);
        }
        return response;
    }

    // Wrapper to decompress GZIP response
    private record GzipClientHttpResponseWrapper(ClientHttpResponse delegate) implements ClientHttpResponse {

        @Override
        public InputStream getBody() throws IOException {
            return new GZIPInputStream(delegate.getBody());
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
