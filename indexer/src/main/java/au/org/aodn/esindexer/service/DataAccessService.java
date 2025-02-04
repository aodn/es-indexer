package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.model.TemporalExtent;
import au.org.aodn.stac.model.StacItemModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DataAccessService {
    List<StacItemModel> getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate);
    List<TemporalExtent> getTemporalExtentOf(String uuid);
    Optional<String> getNotebookLink(String uuid);
}
