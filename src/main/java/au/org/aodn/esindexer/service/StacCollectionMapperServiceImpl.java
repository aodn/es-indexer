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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Mapper(componentModel = "spring")
public abstract class StacCollectionMapperServiceImpl implements StacCollectionMapperService {

    @Mapping(target="uuid", source = "source", qualifiedByName = "mapUUID")
    @Mapping(target="title", source = "source", qualifiedByName = "mapTitle" )
    @Mapping(target="description", source = "source", qualifiedByName = "mapDescription")
    @Mapping(target="extent.bbox", source = "source", qualifiedByName = "mapExtentBbox")
    public abstract StacCollectionModel mapToSTACCollection(MDMetadataType source);

    private static final Logger logger = LoggerFactory.getLogger(StacCollectionMapperServiceImpl.class);

    @Named("mapUUID")
    UUID mapUUID(MDMetadataType source) {
        return UUID.fromString(source.getMetadataIdentifier().getMDIdentifier().getCode().getCharacterString().getValue().toString());
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
                        .collect(Collectors.toList());

                for(EXExtentType e : ext) {
                    try {
                        BBoxUtils.createBBoxFromEXBoundingPolygonType(
                                e.getGeographicElement()
                                        .stream()
                                        .map(m -> m.getAbstractEXGeographicExtent())
                                        .filter(m -> m.getValue() instanceof EXBoundingPolygonType)
                                        .map(m -> (EXBoundingPolygonType)m.getValue())
                                        .collect(Collectors.toList()));
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
