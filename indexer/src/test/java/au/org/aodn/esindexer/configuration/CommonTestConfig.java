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
        return MockServer.builder()
                .server(MockRestServiceServer.createServer(template))
                .restTemplate(template)
                .build();
    }
}
