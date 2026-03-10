package au.org.aodn.esindexer.configuration;

public interface AppConstants {
    String PORTAL_RECORDS_MAPPING_JSON_FILE = "portal_records_index_schema.json";

    // use the updated schema and see what will happen soon
//    String DATASET_INDEX_MAPPING_JSON_FILE = "data_index_schema.json";
    String DATASET_INDEX_MAPPING_JSON_FILE = "data_index_schema.json";
    String VOCABS_INDEX_MAPPING_SCHEMA_FILE = "vocabs_index_schema.json";

    // GCMD citation prefix, used to exclude bibliographic references from concept descriptions (version suffix varies)
    String GCMD_CITATION_PREFIX = "Olsen, L.M., G. Major, K. Shein, J. Scialdone, S. Ritz, T. Stevens, M. Morahan, A. Aleman, R. Vogel, S. Leicester, H. Weir, M. Meaux, S. Grebas, C.Solomon, M. Holland, T. Northcutt, R. A. Restrepo, R. Bilodeau, 2013. NASA/Global Change Master Directory (GCMD) Earth Science Keywords.";
}
