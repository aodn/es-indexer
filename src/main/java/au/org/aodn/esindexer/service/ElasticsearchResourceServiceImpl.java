package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ElasticsearchResourceServiceImpl implements ElasticsearchResourceService {

    @Autowired
    ElasticsearchClient portalElasticsearchClient;

//    public boolean createIndexFromMappingJSONFile(String mappingJSONFile, String indexName) throws IOException {
//        InputStream input = this.getClass()
//                .getResourceAsStream(mappingJSONFile);
//
//        CreateIndexRequest req = CreateIndexRequest.of(b -> b
//                .index(indexName)
//                .withJson(input)
//        );
//        return portalElasticsearchClient.indices().create(req).acknowledged();
//    }
}
