package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.StacCollectionModel;

public interface RankingService {
    public Integer evaluateCompleteness(StacCollectionModel stacCollectionModel);
}
