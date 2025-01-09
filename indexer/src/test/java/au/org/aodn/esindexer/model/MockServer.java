package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@Getter
@Builder
public class MockServer {
    protected MockRestServiceServer server;
    protected RestTemplate restTemplate;
}
