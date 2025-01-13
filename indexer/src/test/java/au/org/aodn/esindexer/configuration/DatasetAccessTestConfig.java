package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.model.MockServer;
import au.org.aodn.esindexer.service.DataAccessServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasetAccessTestConfig {
    /**
     *
     * @return A bean with rest template mocked, so we need to
     */
    @Bean
    public DataAccessServiceImpl createDataAccessService(MockServer serviceServer){
        return new DataAccessServiceImpl("http://localhost", "/api/v1/das/", serviceServer.getRestTemplate());
    }

}
