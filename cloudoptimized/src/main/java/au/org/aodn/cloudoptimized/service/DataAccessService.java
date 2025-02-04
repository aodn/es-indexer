package au.org.aodn.cloudoptimized.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.stac.model.StacItemModel;

public interface DataAccessService {
    List<StacItemModel> getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate);
    List<TemporalExtent> getTemporalExtentOf(String uuid);
    Optional<String> getNotebookLink(String uuid);
}
