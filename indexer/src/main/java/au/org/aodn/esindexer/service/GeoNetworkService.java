package au.org.aodn.esindexer.service;

import au.org.aodn.stac.model.LinkModel;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface GeoNetworkService {
    void setServer(String server);
    String getServer();

    void setIndexName(String i);
    String getIndexName();

    String searchRecordBy(String uuid);
    String findGroupById(String uuid) throws IOException;

    Optional<LinkModel> getThumbnail(String uuid);
    Optional<LinkModel> getLogo(String uuid);
    /**
     * Return Iterable of records, noted that the item inside can be null, so please check null on each item
     * @return
     */
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

    Map<String, ?> getAssociatedRecords(String uuid);
}
