package au.org.aodn.esindexer.configuration;

import au.org.aodn.cloudoptimized.service.DataAccessService;
import au.org.aodn.esindexer.service.ElasticSearchIndexService;
import au.org.aodn.esindexer.service.IndexCloudOptimizedService;
import au.org.aodn.esindexer.service.IndexCloudOptimizedServiceImpl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudOptimizedConfig {

    @Bean
    public IndexCloudOptimizedService createIndexCloudOptimizedService(
            @Value("${elasticsearch.cloud_optimized_index.name}") String indexName,
            @Qualifier("portalElasticsearchClient") ElasticsearchClient elasticsearchClient,
            ObjectMapper indexerObjectMapper,
            DataAccessService dataAccessService,
            ElasticSearchIndexService elasticSearchIndexService) {

        return new IndexCloudOptimizedServiceImpl(indexName, elasticsearchClient, indexerObjectMapper, dataAccessService, elasticSearchIndexService);
    }
}
