package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.LinkModel;
import au.org.aodn.stac.model.RelationType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssociatedRecordsUtilTest {

    // buildLink() casts title/description to LinkedHashMap and swallows the resulting
    // ClassCastException via CommonUtils.safeGet() if a plain HashMap is used instead -
    // this fixture builder must stay LinkedHashMap or a broken link fails silently (null),
    // not loudly, and the test would pass for the wrong reason.
    private static Map<String, Object> record(String id, String title, String description) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);

        Map<String, String> titleMap = new LinkedHashMap<>();
        titleMap.put("eng", title);
        record.put("title", titleMap);

        Map<String, String> descriptionMap = new LinkedHashMap<>();
        descriptionMap.put("eng", description);
        record.put("description", descriptionMap);

        return record;
    }

    private static List<LinkModel> linksWithRel(List<LinkModel> links, RelationType relationType) {
        return links.stream()
                .filter(link -> relationType.getValue().equals(link.getRel()))
                .toList();
    }

    @Test
    public void testGenerateAssociatedRecords_withTwoParents_keepsBoth() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parent", List.of(
                record("8cdcdcad-399b-4bed-8cb2-29c486b6b124", "NRMN Sub-Facility", "facility abstract"),
                record("aeb0afce-7fc7-4d48-91fc-f7b8e730073c", "NESP MaC Project 5.9", "project abstract")
        ));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(data);

        List<LinkModel> parentLinks = linksWithRel(links, RelationType.PARENT);
        assertEquals(2, parentLinks.size(), "Both parents should be kept, not just the first");
        assertTrue(parentLinks.stream().anyMatch(l -> l.getHref().equals("uuid:8cdcdcad-399b-4bed-8cb2-29c486b6b124")));
        assertTrue(parentLinks.stream().anyMatch(l -> l.getHref().equals("uuid:aeb0afce-7fc7-4d48-91fc-f7b8e730073c")));
    }

    @Test
    public void testGenerateAssociatedRecords_withSingleParent_stillWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parent", List.of(
                record("a35d02d7-3bd2-40f8-b982-a0e30b64dc40", "Only Parent", "abstract")
        ));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(data);

        List<LinkModel> parentLinks = linksWithRel(links, RelationType.PARENT);
        assertEquals(1, parentLinks.size());
        assertEquals("uuid:a35d02d7-3bd2-40f8-b982-a0e30b64dc40", parentLinks.get(0).getHref());
    }

    @Test
    public void testGenerateAssociatedRecords_withMultipleChildren_keepsAll() {
        // When a record has two parents, visiting either parent must list this record under "children"
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("children", List.of(
                record("ec424e4f-0f55-41a5-a3f2-726bc4541947", "Benthic cover data", "abstract"),
                record("0a65be6d-1c76-49ac-a151-80acf123612c", "Global benthic cover data", "abstract"),
                record("9efa25cd-4da4-47b5-9385-45e3cbd11705", "Cryptobenthic fish", "abstract")
        ));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(data);

        List<LinkModel> childLinks = linksWithRel(links, RelationType.CHILD);
        assertEquals(3, childLinks.size(), "All children should be kept");
        assertTrue(childLinks.stream().anyMatch(l -> l.getHref().equals("uuid:0a65be6d-1c76-49ac-a151-80acf123612c")));
    }

    @Test
    public void testGenerateAssociatedRecords_withNoParentKey_returnsNoParentLinks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("siblings", List.of(
                record("0ede6b3d-8635-472f-b91c-56a758b4e091", "Sibling", "abstract")
        ));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(data);

        assertTrue(linksWithRel(links, RelationType.PARENT).isEmpty());
        assertEquals(1, linksWithRel(links, RelationType.SIBLING).size());
    }

    @Test
    public void testGenerateAssociatedRecords_withNullData_returnsEmptyList() {
        assertTrue(AssociatedRecordsUtil.generateAssociatedRecords(null).isEmpty());
    }
}
