package au.org.aodn.cloudoptimized.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.cloudoptimized.service.DataAccessServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Slf4j
@AutoConfiguration
@ConditionalOnMissingBean(CloudOptimizedAutoConfiguration.class)
public class CloudOptimizedAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate ardcVocabRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean(DataAccessService.class)
    public DataAccessServiceImpl createDataAccessService(
            @Value("${dataaccess.host:http://localhost:5000}") String serverUrl,
            @Value("${dataaccess.baseUrl:/api/v1/das/}") String baseUrl,
            @Autowired RestTemplate template){

        return new DataAccessServiceImpl(serverUrl, baseUrl, template);
    }
}
