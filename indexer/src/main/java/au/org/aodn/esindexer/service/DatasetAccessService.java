package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.Dataset;

public interface DatasetAccessService {
    Dataset getIndexingDatasetBy(String uuid);
    String getServiceUrl();
    void setServiceUrl(String url);
}
