package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.StacCollectionModel;

import java.util.List;
import java.util.Set;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

/**
 * Helpers to spot the IMOS Facility and Sub-Facility records. The agreed way to identify it is a collection level scope code combined with the absence of the portal:IMOS geonetwork category.
 * The category is set by the GN4 101 harvester on the records. Combine the scope code, it used to decide a record is a portal collection record, or a facility/sub-facility record.
 */
public class FacilityRecordUtils {
    /**
     * The geonetwork category flagging an IMOS portal collection record, see SummariesModel.categories
     */
    public static final String PORTAL_IMOS_CATEGORY = "portal:IMOS";
    /**
     * The scope codes used by the Facility / Sub-Facility records, lowercase because the lookup lowercases the record scope code
     */
    protected static final Set<String> FACILITY_SCOPE_CODES = Set.of("collectionhardware", "series", "collectionsession");

    /**
     * @param datasetGroup - The dataset groups of the record, see SummariesModel.datasetGroup, null tolerated
     * @return - True if the record belongs to IMOS only
     */
    public static boolean isImosOwned(List<String> datasetGroup) {
        return datasetGroup != null
                && datasetGroup.size() == 1
                && "IMOS".equalsIgnoreCase(datasetGroup.get(0));
    }

    /**
     * @param categories - The geonetwork categories of the record, see SummariesModel.categories, null tolerated
     * @return - True if geonetwork assigned the portal:IMOS category to the record
     */
    public static boolean isImosCollectionRecord(List<String> categories) {
        return categories != null && categories.stream().anyMatch(PORTAL_IMOS_CATEGORY::equalsIgnoreCase);
    }

    /**
     * @param model - The mapped record
     * @return - True if the record is an IMOS owned Facility or Sub-Facility record with no data attached
     */
    public static boolean isImosFacilityRecord(StacCollectionModel model) {
        return isImosOwned(safeGet(() -> model.getSummaries().getDatasetGroup()).orElse(null))
                && !isImosCollectionRecord(safeGet(() -> model.getSummaries().getCategories()).orElse(null))
                && safeGet(() -> model.getSummaries().getScope().get("code"))
                        .map(String::toLowerCase)
                        .filter(FACILITY_SCOPE_CODES::contains)
                        .isPresent();
    }
}
