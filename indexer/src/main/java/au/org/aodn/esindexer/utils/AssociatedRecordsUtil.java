package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.geonetwork.service.GeoNetworkServiceImpl;
import au.org.aodn.stac.model.RelationType;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.util.JsonUtil;
import org.springframework.http.MediaType;

import java.util.*;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

public class AssociatedRecordsUtil {

    private record TitleWithAbstract(String title, String recordAbstract) {}

    public static List<LinkModel> generateAssociatedRecords(Map<String, ?> data) {
        return generateAssociatedRecords(data, Collections.emptyMap(), null);
    }
    /**
     * The harvester can filter out records, so our geonetwork does not know them and drops the relationship.
     * The upstream geonetwork the record harvested from still has them, so we add the ones missing in our
     * geonetwork, and point the link to the upstream geonetwork record page, as they do not exist in the portal.
     *
     * @param data - Related records from our geonetwork
     * @param upstream - Related records from the source geonetwork, may be empty
     * @param harvestSourceUri - Base url of the source geonetwork, null if not harvested from a geonetwork
     * @return - Links of parent, siblings and children
     */
    public static List<LinkModel> generateAssociatedRecords(Map<String, ?> data, Map<String, ?> upstream, String harvestSourceUri) {
        var records = new ArrayList<LinkModel>();
        addLinks(records, data, upstream, harvestSourceUri, "parent", RelationType.PARENT);
        addLinks(records, data, upstream, harvestSourceUri, "siblings", RelationType.SIBLING);
        addLinks(records, data, upstream, harvestSourceUri, "children", RelationType.CHILD);
        return records;
    }

    private static void addLinks(List<LinkModel> records,
                                 Map<String, ?> data,
                                 Map<String, ?> upstream,
                                 String harvestSourceUri,
                                 String key,
                                 RelationType relationType) {

        var ids = new HashSet<String>();
        getRecordsByRelationKey(data, key).forEach(record -> {
            var link = buildLink(record, relationType, "uuid:" + record.get("id"));
            if (link != null) {
                records.add(link);
                ids.add(record.get("id").toString());
            }
        });

        if (harvestSourceUri == null) {
            return;
        }
        getRecordsByRelationKey(upstream, key).forEach(record -> {
            if (record.get("id") != null && ids.add(record.get("id").toString())) {
                var link = buildLink(record, relationType,
                        GeoNetworkServiceImpl.getRecordPageUrl(harvestSourceUri, record.get("id").toString()));
                if (link != null) {
                    records.add(link);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static LinkModel buildLink(Map<String, Object> record, RelationType relationType, String href) {

        return safeGet(() -> {
            var titleObject = (LinkedHashMap<String, String>) record.get("title");
            var title = titleObject.get("eng");
            var abstractObject = (LinkedHashMap<String, String>) record.get("description");
            var abstractText = abstractObject.get("eng");
            var titleWithAbstract = new TitleWithAbstract(title, abstractText);
            return LinkModel.builder()
                    .href(href)
                    .rel(relationType.getValue())
                    .title(JsonUtil.toJsonString(titleWithAbstract))
                    .type(MediaType.APPLICATION_JSON.toString())
                    .build();
        }).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getRecordsByRelationKey(Map<String, ?> associatedRecordMap, String key) {
        try {
            return associatedRecordMap != null && associatedRecordMap.containsKey(key) && associatedRecordMap.get(key) != null ?
                    (List<Map<String, Object>>) associatedRecordMap.get(key) :
                    Collections.emptyList();

        } catch (ClassCastException e) {
            return Collections.emptyList();
        }
    }
}
