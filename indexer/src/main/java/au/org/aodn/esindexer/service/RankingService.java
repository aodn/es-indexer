package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.StacCollectionModel;

public interface RankingService {
    public Integer evaluateCompleteness(StacCollectionModel stacCollectionModel);
}
