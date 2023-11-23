package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.StacCollectionModel;

public interface EvaluateCompletenessService {
    public Integer evaluate(StacCollectionModel stacCollectionModel);
}
