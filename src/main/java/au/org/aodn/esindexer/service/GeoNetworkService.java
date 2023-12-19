package au.org.aodn.esindexer.service;

public interface GeoNetworkService {
//    JSONObject searchMetadataBy(String uuid);
    String searchRecordBy(String uuid);
    long getMetadataRecordsCount();
    Iterable<String> getAllMetadataRecords();
}
