package au.org.aodn.esindexer.configuration;

public interface AppConstants {
    String PORTAL_RECORDS_MAPPING_JSON_FILE = "portal_records_index_schema.json";

    String RECOMMENDED_LINK_REL_TYPE = "self";

    String FORMAT_XML = "xml";
    String FORMAT_ISO19115_3_2018 = "iso19115-3.2018";

    String VOCABS_INDEX_MAPPING_SCHEMA_FILE = "vocabs_index_schema.json";

    String ARDC_VOCAB_API_BASE = "https://vocabs.ardc.edu.au/repository/api/lda/aodn";
    String AODN_DISCOVERY_PARAMETER_VOCABS_KEY = "parameter_vocabs";
    String AODN_PLATFORM_VOCABS_KEY = "platform_vocabs";
    String AODN_ORGANISATION_VOCABS_KEY = "organisation_vocabs";
}
