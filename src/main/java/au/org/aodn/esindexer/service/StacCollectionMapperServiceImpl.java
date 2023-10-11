package au.org.aodn.esindexer.service;

import au.org.aodn.esindexer.exception.MappingValueException;
import au.org.aodn.esindexer.utils.BBoxUtils;
import au.org.aodn.esindexer.model.StacCollectionModel;
import au.org.aodn.metadata.iso19115_3_2018.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacCollectionMapperServiceImpl implements StacCollectionMapperService {

    @Mapping(target="uuid", source = "source", qualifiedByName = "mapUUID")
    @Mapping(target="title", source = "source", qualifiedByName = "mapTitle" )
    @Mapping(target="description", source = "source", qualifiedByName = "mapDescription")
    @Mapping(target="summaries", source = "source", qualifiedByName = "mapSummaries")
    @Mapping(target="extent.bbox", source = "source", qualifiedByName = "mapExtentBbox")
    public abstract StacCollectionModel mapToSTACCollection(MDMetadataType source);

    private static final Logger logger = LoggerFactory.getLogger(StacCollectionMapperServiceImpl.class);

    @Named("mapUUID")
    String mapUUID(MDMetadataType source) {
        return source.getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue().toString();
    }

    @Named("mapExtentBbox")
    List<List<Double>> mapExtentBbox(MDMetadataType source) throws FactoryException, TransformException {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // We only concern geographicElement here
                List<EXExtentType> ext = i.getExtent()
                        .stream()
                        .filter(f -> f.getAbstractExtent() != null)
                        .filter(f -> f.getAbstractExtent().getValue() != null)
                        .filter(f -> f.getAbstractExtent().getValue() instanceof EXExtentType)
                        .map(f -> (EXExtentType)f.getAbstractExtent().getValue())
                        .filter(f -> f.getGeographicElement() != null)
                        .toList();

                for(EXExtentType e : ext) {
                    try {
                        // TODO: pay attention here
                        List<Object> rawInput = e.getGeographicElement()
                            .stream()
                            .map(AbstractEXGeographicExtentPropertyType::getAbstractEXGeographicExtent)
                            .filter(m -> m.getValue() instanceof EXBoundingPolygonType || m.getValue() instanceof EXGeographicBoundingBoxType)
                            .map(m -> {
                                if (m.getValue() instanceof EXBoundingPolygonType) {
                                    return (EXBoundingPolygonType) m.getValue();
                                } else if (m.getValue() instanceof EXGeographicBoundingBoxType) {
                                    return (EXGeographicBoundingBoxType) m.getValue();
                                }
                                return null; // Handle other cases or return appropriate default value
                            })
                            .filter(Objects::nonNull) // Filter out null values if any
                            .collect(Collectors.toList());
                        if (!rawInput.isEmpty() && rawInput.get(0) instanceof EXBoundingPolygonType) {
                            return BBoxUtils.createBBoxFromEXBoundingPolygonType(rawInput);
                        } else if (!rawInput.isEmpty() && rawInput.get(0) instanceof EXGeographicBoundingBoxType) {
                            return BBoxUtils.createBBoxFromEXGeographicBoundingBoxType(rawInput);
                        }
                    } catch (MappingValueException ex) {
                        logger.warn(ex.getMessage() + " for metadata record: " + this.mapUUID(source));
                    }
                }
            }
        }
        return null;
    }
    /**
     * Custom mapping for description field, name convention is start with map then the field name
     * @param source
     * @return
     */
    @Named("mapDescription")
    String mapDescription(MDMetadataType source) {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);

        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // TODO: Null or empty check
                return i.getAbstract().getCharacterString().getValue().toString();
            }
        }
        return "";
    }

    @Named("mapSummaries")
    List<Map<String, Object>> mapSummaries(MDMetadataType source) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if (!items.isEmpty()) {
            for (MDDataIdentificationType i : items) {
                // score
                // TODO: Custom calculation for score for ranking
                summaries.add(Map.of(
                        "score", 0
                ));

                // status
                // mdb:identificationInfo/mri:MD_DataIdentification/mri:status/mcc:MD_ProgressCode/@codeListValue
                for (MDProgressCodePropertyType s : i.getStatus()) {
                    summaries.add(Map.of(
                            "status", s.getMDProgressCode().getCodeListValue()
                    ));
                }


                // mdb:dateInfo/cit:CI_Date/cit:date/gco:DateTime/#text
                // TODO: these backward compatible code make the code ugly, need to refactor
                try {
                    AbstractCitationType ac = i.getCitation().getAbstractCitation().getValue();
                    if(ac instanceof CICitationType2 type2) {
                        type2.getDate().forEach(f -> {
                            if (f.getCIDate().getDateType().getCIDateTypeCode().getCodeListValue().equals("creation")) {
                                summaries.add(Map.of(
                                        "creation", f.getCIDate().getDate().getDateTime().toXMLFormat()
                                ));
                            }
                        });
                    }
                    if(ac instanceof CICitationType type1) {
                        // Backward compatible
                        type1.getDate().forEach(f -> {
                            if (f.getCIDate().getDateType().getCIDateTypeCode().getCodeListValue().equals("creation")) {
                                summaries.add(Map.of(
                                        "creation", f.getCIDate().getDate().getDateTime().toXMLFormat()
                                ));
                            }
                        });
                    }
                } catch (NullPointerException e) {
                    // Do nothing
                    logger.warn("Unable to find creation date for metadata record: " + this.mapUUID(source));
                }
            }
        }
        return summaries;
    }

    /**
     * Custom mapping for title field, name convention is start with map then the field name
     * @param source
     * @return
     */
    @Named("mapTitle")
    String mapTitle(MDMetadataType source) {
        List<MDDataIdentificationType> items = findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // TODO: Null or empty check
                AbstractCitationType ac = i.getCitation().getAbstractCitation().getValue();
                if(ac instanceof CICitationType2 type2) {
                    return type2.getTitle().getCharacterString().getValue().toString();
                }
                else if(ac instanceof CICitationType type1) {
                    // Backward compatible
                    type1.getTitle().getCharacterString().getValue().toString();
                }
            }
        }
        return "";
    }

    List<MDDataIdentificationType> findMDDataIdentificationType(MDMetadataType source) {
        // Read the raw XML to understand the structure.
        return source.getIdentificationInfo()
                .stream()
                .filter(f -> f.getAbstractResourceDescription() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() != null)
                .filter(f -> f.getAbstractResourceDescription().getValue() instanceof MDDataIdentificationType)
                .map(f -> (MDDataIdentificationType)f.getAbstractResourceDescription().getValue())
                .collect(Collectors.toList());
    }
}
