package au.org.aodn.esindexer.service;

import java.util.Map;

public interface GeoNetworkResourceService {
    Map<String, Object> searchMetadataRecordByUUID(String uuid);
}
