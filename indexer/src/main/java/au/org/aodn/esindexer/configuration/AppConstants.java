package au.org.aodn.esindexer.configuration;

public interface AppConstants {
    String PORTAL_RECORDS_MAPPING_JSON_FILE = "portal_records_index_schema.json";

    // use the updated schema and see what will happen soon
    String VOCABS_INDEX_MAPPING_SCHEMA_FILE = "vocabs_index_schema.json";

    // key-value store of the indexer settings, one document per setting
    String INDEXER_CONFIG_MAPPING_JSON_FILE = "indexer_config_schema.json";
}
