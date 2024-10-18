package au.org.aodn.esindexer.configuration;

import au.org.aodn.esindexer.service.DatasetAccessServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasetAccessConfig {

    @Bean(name = "DatasetAccessService")
    @ConditionalOnMissingBean(DatasetAccessServiceImpl.class)
    public  DatasetAccessServiceImpl createDatasetAccessService(
        @Value("${dataaccess.host}") String serverUrl
    ){
        return new DatasetAccessServiceImpl(serverUrl);
    }
}
