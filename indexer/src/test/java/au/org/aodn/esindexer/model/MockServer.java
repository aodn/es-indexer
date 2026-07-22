package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@Getter
@Builder
public class MockServer {
    protected MockRestServiceServer server;
    protected RestTemplate restTemplate;

    /**
     * Default DAS behaviour: most UUIDs have no notebook / CO metadata. Indexing calls both
     * {@code /metadata/{uuid}} and {@code /notebook_url}, so this allows manyTimes (not once()).
     * Register after bean creation, or after a bare {@link MockRestServiceServer#reset()} when
     * restoring the shared-suite default.
     */
    public void expectDefaultNotFound() {
        server.expect(manyTimes(), anything())
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
    }

    /**
     * Clear all expectations and restore {@link #expectDefaultNotFound()}.
     * Prefer this over bare {@code getServer().reset()} so later tests still get the DAS 404 default.
     * Tests that need non-404 responses should {@code getServer().reset()}, register their own
     * expects, then call this in a finally / {@code @AfterAll} to restore the default.
     */
    public void resetToDefault() {
        server.reset();
        expectDefaultNotFound();
    }
}
