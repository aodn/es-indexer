package au.org.aodn.esindexer.utils;

import au.org.aodn.cloudoptimized.model.RelationType;
import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.util.JsonUtil;
import org.springframework.http.MediaType;

import java.util.*;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

public class AssociatedRecordsUtil {

    private record TitleWithAbstract(String title, String recordAbstract) {}

    public static List<LinkModel> generateAssociatedRecords(Map<String, ?> data) {
        var records = new ArrayList<LinkModel>();
        if (data == null || data.isEmpty()) {
            return records;
        }
        var parentRecord = getParentRecord(data);
        if (!parentRecord.isEmpty()) {
            var parentLink = buildLink(parentRecord, RelationType.PARENT);
            if (parentLink != null) {
                records.add(parentLink);
            }
        }
        getSiblingRecords(data).forEach(record -> {
            var siblingLink = buildLink(record, RelationType.SIBLING);
            if (siblingLink != null) {
                records.add(siblingLink);
            }
        });
        getChildRecords(data).forEach(record -> {
            var childLink = buildLink(record, RelationType.CHILD);
            if (childLink != null) {
                records.add(childLink);
            }
        });

        return records;
    }

    @SuppressWarnings("unchecked")
    private static LinkModel buildLink(Map<String, Object> record, RelationType relationType) {

        return safeGet(() -> {
            var href = "uuid:" + record.get("id").toString();
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
            return associatedRecordMap.containsKey(key) && associatedRecordMap.get(key) != null ?
                    (List<Map<String, Object>>) associatedRecordMap.get(key) :
                    Collections.emptyList();

        } catch (ClassCastException e) {
            return Collections.emptyList();
        }
    }

    // should only have 1 parent
    private static Map<String, Object> getParentRecord(Map<String, ?> associatedRecordMap) {
        var records = getRecordsByRelationKey(associatedRecordMap, "parent");
        if (!records.isEmpty()) {
            return records.get(0);
        }
        return Collections.emptyMap();
    }

    private static List<Map<String, Object>> getSiblingRecords(Map<String, ?> associatedRecordMap) {
        return getRecordsByRelationKey(associatedRecordMap, "siblings");
    }

    private static List<Map<String, Object>> getChildRecords(Map<String, ?> associatedRecordMap) {
        return getRecordsByRelationKey(associatedRecordMap, "children");
    }
}
