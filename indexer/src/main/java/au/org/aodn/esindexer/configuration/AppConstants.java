package au.org.aodn.esindexer.configuration;

import java.util.Arrays;
import java.util.List;

public interface AppConstants {
    String PORTAL_RECORDS_MAPPING_JSON_FILE = "portal_records_index_schema.json";

    String RECOMMENDED_LINK_REL_TYPE = "self";

    String FORMAT_XML = "xml";
    String FORMAT_ISO19115_3_2018 = "iso19115-3.2018";

    List<String> STOP_WORDS = Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he", "in", "is",
            "it", "its", "of", "on", "that", "the", "to", "was", "were", "will", "with"
    );

    String AODN_DISCOVERY_PARAMETER_VOCAB_API = "https://vocabs.ardc.edu.au/repository/api/lda/aodn";
    String AODN_DISCOVERY_CATEGORIES_CACHE = "parameter_categories";
    String AODN_DISCOVERY_PARAMETER_VOCABULARIES_MAPPING_JSON_FILE = "aodn_discovery_parameter_vocabularies_index.json";
    String GCMD_KEYWORDS_API = "https://gcmd.earthdata.nasa.gov/kms/tree/concept_scheme/all";
}
