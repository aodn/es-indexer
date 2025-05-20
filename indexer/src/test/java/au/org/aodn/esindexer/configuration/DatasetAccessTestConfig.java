package au.org.aodn.esindexer.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessServiceImpl;
import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.model.MockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasetAccessTestConfig {
    /**
     *
     * @return A bean with rest template mocked, so we need to
     */
    @Bean
    public DataAccessService createDataAccessService(MockServer serviceServer, ObjectMapper objectMapper){
        return new DataAccessServiceImpl("http://localhost", "/api/v1/das/", serviceServer.getRestTemplate(), null, objectMapper);
    }
}
