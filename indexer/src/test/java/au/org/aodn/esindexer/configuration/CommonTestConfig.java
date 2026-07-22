package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.model.MockServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CommonTestConfig {

    @Bean
    public MockServer createMockServer() {
        RestTemplate template = new RestTemplate(new SimpleClientHttpRequestFactory());

        // ignoreExpectOrder so tests can register path-specific expectations without FIFO
        // conflicts against the default catch-all on MockServer.expectDefaultNotFound().
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(template)
                .ignoreExpectOrder(true)
                .build();

        MockServer mockServer = MockServer.builder()
                .server(server)
                .restTemplate(template)
                .build();

        // Default DAS GETs → 404. Tests that need non-404 responses should getServer().reset()
        // then register their own expects, and restore via mockServer.resetToDefault() afterwards.
        mockServer.expectDefaultNotFound();
        return mockServer;
    }
}
