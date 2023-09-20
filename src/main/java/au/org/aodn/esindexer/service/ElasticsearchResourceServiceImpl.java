package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchResourceServiceImpl implements ElasticsearchResourceService {
    @Autowired
    ElasticsearchClient portalElasticsearchClient;

}
