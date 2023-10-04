package au.org.aodn.esindexer.service;

import jakarta.xml.bind.JAXB;
import org.json.JSONObject;
import org.json.XML;

import java.util.List;

public interface GeoNetworkResourceService {
    JSONObject searchMetadataRecordByUUIDFromGNRecordsIndex(String uuid);
    String searchMetadataRecordByUUIDFromGN4(String uuid);
    int getMetadataRecordsCount();
    List<String> getAllMetadataRecords();
}
