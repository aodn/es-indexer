package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.service.DataAccessServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasetAccessConfig {

    @Bean(name = "DataAccessService")
    public DataAccessServiceImpl createDataAccessService(
        @Value("${dataaccess.host:defaultForTesting}") String serverUrl
    ){
        return new DataAccessServiceImpl(serverUrl);
    }
}
