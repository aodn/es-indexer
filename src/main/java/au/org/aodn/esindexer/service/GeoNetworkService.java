package au.org.aodn.esindexer.service;

import org.springframework.http.ResponseEntity;

public interface GeoNetworkService {
    void setServer(String server);
    String getServer();

    void setIndexName(String i);
    String getIndexName();

    String searchRecordBy(String uuid);
    Iterable<String> getAllMetadataRecords();
    /**
     * This function can avoid elastic outsync and achieve what we need here as the only use case is
     * check if there is only 1 document in elastic.
     *
     * Orginally, we define long getMetadataRecordsCount(); but this is not reliable implemented.
     *
     * The total record return by elastic can be outdated if you query immediately after insert or delete,
     * you can call reindex but that require you to have privilege permission aka user/password to geonetwork.
     * Given the only use case here can be re-write with different function, this method is removed.
     *
     * @param c
     * @return
     */
    boolean isMetadataRecordsCountLessThan(int c);
}
