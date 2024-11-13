package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.Datum;
import au.org.aodn.esindexer.model.TemporalExtent;

import java.time.LocalDate;

public interface DataAccessService {
    Datum[] getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate);
    String getServiceUrl();
    void setServiceUrl(String url);


    TemporalExtent getTemporalExtentOf(String uuid);
}
