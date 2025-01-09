package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.Datum;
import au.org.aodn.esindexer.model.TemporalExtent;

import java.time.LocalDate;
import java.util.List;

public interface DataAccessService {
    List<Datum> getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate);
    List<TemporalExtent> getTemporalExtentOf(String uuid);
}
