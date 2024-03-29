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
}
