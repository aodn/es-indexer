package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.StacCollectionModel;

import java.util.Map;

public interface RankingService {
    /**
     * The ranking rules, the key is the name stored in summaries.score_breakdown.
     */
    enum Rule {
        /** Number of keywords, aka number of Themes */
        KEYWORDS("keywords"),
        /** Contains statement aka lineage */
        STATEMENT("statement"),
        /** License */
        LICENSE("license"),
        /** Citation */
        CITATION("citation"),
        /** Abstract */
        ABSTRACT("abstract"),
        /** Number of links */
        LINKS("links"),
        /** Overall count of metadata elements */
        METADATA_ELEMENTS("metadata_elements"),
        /** IMOS owned records */
        IMOS_OWNED("imos_owned"),
        /** Has cloud-optimised download service */
        CLOUD_OPTIMISED_DOWNLOAD("cloud_optimised_download"),
        /** Has downloadable links (including WFS download) */
        DOWNLOADABLE_LINKS("downloadable_links"),
        /** Is out-of-date (superseded/deprecated/obsolete) */
        OUT_OF_DATE("out_of_date"),
        /** Is document */
        DOCUMENT("document"),
        /** Is IMOS facility/sub-facility */
        IMOS_FACILITY("imos_facility"),
        /** Is IMOS collection */
        IMOS_COLLECTION("imos_collection");

        public final String key;

        Rule(String key) {
            this.key = key;
        }
    }

    /**
     * @param total - The score, it equals the sum of the breakdown values
     * @param breakdown - The points each rule added, keyed by Rule.key in Rule order, a rule not met has 0
     */
    record CompletenessScore(int total, Map<String, Integer> breakdown) {}

    CompletenessScore evaluateCompleteness(StacCollectionModel stacCollectionModel);
}
