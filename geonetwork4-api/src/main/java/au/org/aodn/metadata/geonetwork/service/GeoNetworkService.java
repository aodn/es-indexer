package au.org.aodn.metadata.geonetwork.service;

import au.org.aodn.stac.model.LinkModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GeoNetworkService {
    void setServer(String server);
    String getServer();

    void setIndexName(String i);
    String getIndexName();

    String searchRecordBy(String uuid);
    String findGroupById(String uuid) throws IOException;
    /**
     * The categories assigned to the record in geonetwork, the portal:IMOS category is use to identify the IMOS portal collection records.
     * Use geonetwork API /geonetwork/srv/api/records/{uuid}/tags to get this value.
     * Example response:[{"name":"portal:IMOS","id":131,"label":{"swe":"portal:IMOS","dut":"portal:IMOS","fin":"portal:IMOS","rum":"portal:IMOS","pol":"portal:IMOS","aze":"portal:IMOS","nor":"portal:IMOS","tur":"portal:IMOS","dan":"portal:IMOS","geo":"portal:IMOS","rus":"portal:IMOS","ara":"portal:IMOS","por":"portal:IMOS","vie":"portal:IMOS","ger":"portal:IMOS","cat":"portal:IMOS","ita":"portal:IMOS","fre":"portal:IMOS","arm":"portal:IMOS","ukr":"portal:IMOS","eng":"portal:IMOS","wel":"portal:IMOS","spa":"portal:IMOS","chi":"portal:IMOS","slo":"portal:IMOS"}}]
     *
     * @param uuid - The query UUID
     * @return - The category names, empty if the record has none, return a list to support multiple categories (if any)
     */
    List<String> findCategoriesById(String uuid);

    Optional<LinkModel> getThumbnail(String uuid);
    Optional<LinkModel> getLogo(String uuid);
    /**
     * Return Iterable of records, noted that the item inside can be null, so please check null on each item
     * @return
     */
    Iterable<String> getAllMetadataRecords(String beginWithUuid);
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
    /**
     * Ge the count of the docs
     * @return The total number of records
     */
    Long getAllMetadataCounts() throws IOException;

    Map<String, ?> getAssociatedRecords(String uuid);
}
