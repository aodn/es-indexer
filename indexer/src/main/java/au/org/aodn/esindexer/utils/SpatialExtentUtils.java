package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.*;
import au.org.aodn.stac.model.SpatialExtentModel;

import java.math.BigDecimal;
import java.util.*;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

/**
 * Extract the described spatial extents of a record, used by portal to label extent points on the details map
 */
public class SpatialExtentUtils {

    /**
     * One {description, bbox} entry per gex:EX_Extent that has a gex:description,
     * e.g. "Pelorus Reef - 5479 Currents / Waves" with the point it belongs to
     * @param source - A parsed XML from geonetwork
     * @return - The entries, empty list if the record has none
     */
    public static List<SpatialExtentModel> createSpatialExtentsFrom(MDMetadataType source) {
        List<SpatialExtentModel> result = new ArrayList<>();
        for (EXExtentType extent : findExtents(source)) {
            Optional<String> description = findDescriptionOf(extent);
            Optional<List<BigDecimal>> bbox = findBBoxOf(extent);
            if (description.isPresent() && bbox.isPresent()) {
                result.add(SpatialExtentModel.builder()
                        .description(description.get())
                        .bbox(bbox.get())
                        .build());
            }
        }
        return result;
    }

    // All gex:EX_Extent of the record's mri:extent list, empty list if it has none
    protected static List<EXExtentType> findExtents(MDMetadataType source) {
        List<MDDataIdentificationType> data = MapperUtils.findMDDataIdentificationType(source);
        List<? extends AbstractMDIdentificationType> identifications = data.isEmpty()
                ? MapperUtils.findSVServiceIdentificationType(source)
                : data;
        if (identifications.isEmpty()) {
            return List.of();
        }
        List<EXExtentType> extents = new ArrayList<>();
        for (var wrapper : identifications.get(0).getExtent()) {
            if (wrapper.getAbstractExtent() != null
                    && wrapper.getAbstractExtent().getValue() instanceof EXExtentType extent
                    && extent.getGeographicElement() != null) {
                extents.add(extent);
            }
        }
        return extents;
    }

    // The gex:description text of one extent, empty if absent or blank
    protected static Optional<String> findDescriptionOf(EXExtentType extent) {
        return safeGet(() -> extent.getDescription().getCharacterString().getValue().toString().trim())
                .filter(text -> !text.isEmpty());
    }

    // The bbox around one extent's gex:geographicElement entries, empty if it has none
    protected static Optional<List<BigDecimal>> findBBoxOf(EXExtentType extent) {
        List<AbstractEXGeographicExtentType> geographicElements = new ArrayList<>();
        for (var wrapper : extent.getGeographicElement()) {
            var element = wrapper.getAbstractEXGeographicExtent() == null
                    ? null
                    : wrapper.getAbstractEXGeographicExtent().getValue();
            if (element instanceof EXBoundingPolygonType || element instanceof EXGeographicBoundingBoxType) {
                geographicElements.add((AbstractEXGeographicExtentType) element);
            }
        }
        // createStacBBox rounds coordinates and handles the antimeridian, its first entry is the overall bbox
        List<List<BigDecimal>> bbox = StacUtils.createStacBBox(
                GeometryBase.findPolygonsFrom(GeometryBase.COORDINATE_SYSTEM_CRS84, List.of(geographicElements)));
        return bbox.isEmpty() ? Optional.empty() : Optional.of(bbox.get(0));
    }
}
