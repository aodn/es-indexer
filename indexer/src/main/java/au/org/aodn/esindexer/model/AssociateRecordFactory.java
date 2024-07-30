package au.org.aodn.esindexer.model;

import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.util.JsonUtil;

import java.util.*;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;

public class AssociateRecordFactory {

    private record RecordTitle(String recordTitle, String recordAbstract) {}

    public static List<LinkModel> buildAssociatedRecords(Map<String, ?> data) {
        var records = new ArrayList<LinkModel>();
        if (data == null || data.isEmpty()) {
            return records;
        }
        var parentsRecordMap = getRecordMaps(data, "parent");
        if (!parentsRecordMap.isEmpty()) {
            var parentLink = buildLink(parentsRecordMap.get(0), RelationType.PARENT);
            if (parentLink != null) {
                records.add(parentLink);
            }
        }
        var siblingRecordMaps = getRecordMaps(data, "brothersAndSisters");
        var childRecordMaps = getRecordMaps(data, "children");

        var siblingLinks = buildLinks(siblingRecordMaps, RelationType.SIBLING);
        if (!siblingLinks.isEmpty()) {
            records.addAll(siblingLinks);
        }

        var childLinks = buildLinks(childRecordMaps, RelationType.CHILD);
        if (!childLinks.isEmpty()) {
            records.addAll(childLinks);
        }

        return records;
    }

    private static List<LinkModel> buildLinks(List<Map<String, Object>> recordMaps, RelationType relationType) {
        var links = new ArrayList<LinkModel>();
        if (recordMaps.isEmpty()) {
            return links;
        }
        for (var recordMap : recordMaps) {
            var link = buildLink(recordMap, relationType);
            if (link != null) {
                links.add(link);
            }
        }
        return links;
    }

    @SuppressWarnings("unchecked")
    private static LinkModel buildLink(Map<String, Object> recordMap, RelationType relationType) {

        return safeGet(() -> {
            var recordSource = (Map<String, Object>)recordMap.get("_source");
            var href = "uuid:" + recordSource.get("uuid").toString();
            var titleObject = (LinkedHashMap<String, String>) recordSource.get("resourceTitleObject");
            var title = titleObject.get("default");
            var abstractObject = (LinkedHashMap<String, String>) recordSource.get("resourceAbstractObject");
            var abstractText = abstractObject.get("default");
            var recordTitle = new RecordTitle(title, abstractText);
            return LinkModel.builder()
                    .href(href)
                    .rel(relationType.getValue())
                    .title(JsonUtil.toJsonString(recordTitle))
                    .type(MediaType.APPLICATION_JSON.getValue())
                    .build();
        }).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getRecordMaps(Map<String, ?> associatedRecordMap, String key) {
        try {
            return (List<Map<String, Object>>) associatedRecordMap.get(key);
        } catch (ClassCastException e) {
            return Collections.emptyList();
        }
    }
}
