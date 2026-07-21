package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.model.MockServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@Configuration
public class CommonTestConfig {

    @Bean
    public MockServer createMockServer() {
        RestTemplate template = new RestTemplate(new SimpleClientHttpRequestFactory());

        // ignoreExpectOrder so tests can register path-specific expectations without FIFO
        // conflicts against this default catch-all.
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(template)
                .ignoreExpectOrder(true)
                .build();

        // Default DAS behaviour: most UUIDs have no notebook / CO metadata. Indexing now calls
        // both /metadata/{uuid} and /notebook_url, so this must allow manyTimes (not once()).
        // Tests that need non-404 responses should server.reset() then register their own expects.
        server.expect(manyTimes(), anything())
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        return MockServer.builder()
                .server(server)
                .restTemplate(template)
                .build();
    }
}
