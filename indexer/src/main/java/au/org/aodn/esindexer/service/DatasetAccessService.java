package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.Dataset;

import java.time.LocalDate;

public interface DatasetAccessService {
    Dataset getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate);
    boolean doesDataExist(String uuid, LocalDate startDate, LocalDate endDate);
    String getServiceUrl();
    void setServiceUrl(String url);
}
