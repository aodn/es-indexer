package au.org.aodn.cloudoptimized.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import au.org.aodn.cloudoptimized.model.MetadataEntity;
import au.org.aodn.cloudoptimized.model.MetadataFields;
import au.org.aodn.cloudoptimized.model.TemporalExtent;
import au.org.aodn.stac.model.StacItemModel;

public interface DataAccessService {

    default List<MetadataFields> getFields(MetadataEntity entity) {
        return entity.getDepth() != null ?
                List.of(MetadataFields.TIME, MetadataFields.DEPTH, MetadataFields.LONGITUDE, MetadataFields.LATITUDE) :
                List.of(MetadataFields.TIME, MetadataFields.LONGITUDE, MetadataFields.LATITUDE);
    }

    List<StacItemModel> getIndexingDatasetBy(String uuid, LocalDate startDate, LocalDate endDate, List<MetadataFields> fields);
    List<TemporalExtent> getTemporalExtentOf(String uuid);
    Optional<String> getNotebookLink(String uuid);
    MetadataEntity getMetadataByUuid(String uuid);
    List<MetadataEntity> getAllMetadata();
}
