package au.org.aodn.esindexer.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.service.DataAccessServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DatasetAccessConfig {

    @Bean
    @ConditionalOnMissingBean(DataAccessService.class)
    public DataAccessServiceImpl createDataAccessService(
            @Value("${dataaccess.host:http://localhost:5000}") String serverUrl,
            @Value("${dataaccess.baseUrl:/api/v1/das/}") String baseUrl,
            @Autowired RestTemplate template){

        return new DataAccessServiceImpl(serverUrl, baseUrl, template);
    }
}
