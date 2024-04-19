package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.StacCollectionModel;

public interface RankingService {
    Integer evaluateCompleteness(StacCollectionModel stacCollectionModel);
}
