package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.StacCollectionModel;

import java.util.List;
import java.util.Set;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

/**
 * Helpers to spot the IMOS Facility and Sub-Facility records. The agreed way to identify one is an IMOS owned record (dataset_group is "IMOS" alone) carrying a facility level scope code and lacking the portal:IMOS geonetwork category.
 * The category is set by the GN4 101 harvester on the records, so its presence marks a portal collection record, and its absence, together with the scope code, marks a facility / sub-facility record.
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
     * An IMOS owned record is one grouped under "IMOS" alone. The grouping is held in dataset_group, which derives from the upstream harvest group
     * @param datasetGroup - The dataset groups of the record, see SummariesModel.datasetGroup, null tolerated
     * @return - True if the record belongs to IMOS only
     */
    public static boolean isImosOwned(List<String> datasetGroup) {
        return datasetGroup != null
                && datasetGroup.size() == 1
                && "IMOS".equalsIgnoreCase(datasetGroup.get(0));
    }

    /**
     * An IMOS collection record represents a Portal collection. For example record 2223b7f2-4bac-4ff1-9b1e-aae9ac58deef: https://portal-edge.aodn.org.au/details/2223b7f2-4bac-4ff1-9b1e-aae9ac58deef?tab=summary
     * IMOS collection records are generally well curated and high quality, so they should be prioritised in the ranking.
     * @param categories - The geonetwork categories of the record, see SummariesModel.categories, null tolerated
     * @return - True if geonetwork assigned the portal:IMOS category to the record
     */
    public static boolean isImosCollectionRecord(List<String> categories) {
        return categories != null && categories.stream().anyMatch(PORTAL_IMOS_CATEGORY::equalsIgnoreCase);
    }

    /**
     * An IMOS facility (or sub-facility) record describes the facility itself rather than a dataset. For example record 3e575769-201b-4928-a15d-11ec7e5a7bdd: https://portal-edge.aodn.org.au/details/3e575769-201b-4928-a15d-11ec7e5a7bdd?tab=summary
     * Such records are expected not dataset, which is why they are deprioritised than dataset records.
     * @param model - The mapped record
     * @return - True if the record is IMOS owned, lacks the portal:IMOS category, and carries a Facility / Sub-Facility scope code
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
