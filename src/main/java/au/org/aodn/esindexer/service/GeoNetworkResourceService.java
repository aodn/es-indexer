package au.org.aodn.esindexer.service;

import org.json.JSONObject;
import java.util.List;

public interface GeoNetworkResourceService {
    JSONObject searchMetadataRecordByUUIDFromGNRecordsIndex(String uuid);
    String searchGN4RecordBy(String uuid);
    int getMetadataRecordsCount();
    List<String> getAllMetadataRecords();
}
