package au.org.aodn.esindexer.service;

import org.json.JSONObject;

import java.util.Map;

public interface GeoNetworkResourceService {
    JSONObject searchMetadataRecordByUUID(String uuid);
    int getMetadataRecordsCount();
}
