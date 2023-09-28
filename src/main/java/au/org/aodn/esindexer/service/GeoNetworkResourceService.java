package au.org.aodn.esindexer.service;

import org.json.JSONObject;
import java.util.List;

public interface GeoNetworkResourceService {
    JSONObject searchMetadataRecordByUUIDFromGNRecordsIndex(String uuid);
    JSONObject searchMetadataRecordByUUIDFromGN4(String uuid);
    int getMetadataRecordsCount();
    List<JSONObject> getAllMetadataRecords();
}
