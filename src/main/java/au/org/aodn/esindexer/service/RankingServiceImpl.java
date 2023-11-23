package au.org.aodn.esindexer.service;

import org.springframework.stereotype.Service;

import au.org.aodn.esindexer.model.StacCollectionModel;

@Service
public class RankingServiceImpl implements RankingService {
    public Integer evaluateCompleteness(StacCollectionModel stacCollectionModel) {
        return 0;
    }
}
