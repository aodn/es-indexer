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
        // getChildRecords() was never truncated to one, unlike the old getParentRecord() -
        // this locks that in. Only this method's own multi-child behavior is tested here;
        // it's what makes a record show up under "Sub Records" on every one of its parents.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("children", List.of(
                record("ec424e4f-0f55-41a5-a3f2-726bc4541947", "Benthic cover data", "abstract"),
                record("0a65be6d-1c76-49ac-a151-80acf123612c", "Global benthic cover data", "abstract"),
                record("9efa25cd-4da4-47b5-9385-45e3cbd11705", "Cryptobenthic fish", "abstract")
        ));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(data);

        List<LinkModel> childLinks = linksWithRel(links, RelationType.CHILD);
        assertEquals(3, childLinks.size(), "All children should be kept");
        assertTrue(childLinks.stream().anyMatch(l -> l.getHref().equals("uuid:ec424e4f-0f55-41a5-a3f2-726bc4541947")));
        assertTrue(childLinks.stream().anyMatch(l -> l.getHref().equals("uuid:0a65be6d-1c76-49ac-a151-80acf123612c")));
        assertTrue(childLinks.stream().anyMatch(l -> l.getHref().equals("uuid:9efa25cd-4da4-47b5-9385-45e3cbd11705")));
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

    private static final String UPSTREAM = "https://catalogue-imos.aodn.org.au/geonetwork";

    @Test
    public void testGenerateAssociatedRecords_withChildrenOnlyInUpstream_linksToUpstreamGeonetwork() {
        // af5d0ff9-bb9c-4b7c-a63c-854a630b6984, the AUV records are filtered out by the harvester
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("children", List.of(
                record("8cdcdcad-399b-4bed-8cb2-29c486b6b124", "NRMN Sub-Facility", "abstract")
        ));
        Map<String, Object> upstream = new LinkedHashMap<>();
        upstream.put("children", List.of(
                record("0f65b7ae-1f6f-4a55-b804-1c991f791e1a", "AUV Iver", "abstract"),
                record("8cdcdcad-399b-4bed-8cb2-29c486b6b124", "NRMN Sub-Facility", "abstract"),
                record("8dfa2b64-4eed-491c-ba5e-645ea9409d4d", "AUV Nimbus", "abstract")
        ));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(local, upstream, UPSTREAM);

        List<LinkModel> childLinks = linksWithRel(links, RelationType.CHILD);
        assertEquals(List.of(
                "uuid:8cdcdcad-399b-4bed-8cb2-29c486b6b124",
                UPSTREAM + "/srv/eng/catalog.search#/metadata/0f65b7ae-1f6f-4a55-b804-1c991f791e1a",
                UPSTREAM + "/srv/eng/catalog.search#/metadata/8dfa2b64-4eed-491c-ba5e-645ea9409d4d"
        ), childLinks.stream().map(LinkModel::getHref).toList(), "Record in both should not duplicate");
        assertEquals("{\"title\":\"AUV Iver\",\"recordAbstract\":\"abstract\"}", childLinks.get(1).getTitle());
        assertEquals("application/json", childLinks.get(1).getType());
    }

    @Test
    public void testGenerateAssociatedRecords_withUpstreamOnlyRelation_addedPerRelation() {
        Map<String, Object> upstream = new LinkedHashMap<>();
        upstream.put("parent", List.of(record("c78801d0-bffe-11dc-a463-00188b4c0af8", "IMOS", "abstract")));
        upstream.put("siblings", List.of(record("95c09bad-1847-48f0-9ed7-1ba36e7abb8d", "Low Cost Wave Buoys", "abstract")));

        List<LinkModel> links = AssociatedRecordsUtil.generateAssociatedRecords(Map.of(), upstream, UPSTREAM);

        assertEquals(UPSTREAM + "/srv/eng/catalog.search#/metadata/c78801d0-bffe-11dc-a463-00188b4c0af8",
                linksWithRel(links, RelationType.PARENT).get(0).getHref());
        assertEquals(UPSTREAM + "/srv/eng/catalog.search#/metadata/95c09bad-1847-48f0-9ed7-1ba36e7abb8d",
                linksWithRel(links, RelationType.SIBLING).get(0).getHref());
        assertTrue(linksWithRel(links, RelationType.CHILD).isEmpty());
    }

    @Test
    public void testGenerateAssociatedRecords_withEmptyUpstream_sameAsLocalOnly() {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("siblings", List.of(record("0ede6b3d-8635-472f-b91c-56a758b4e091", "Sibling", "abstract")));

        assertEquals(
                AssociatedRecordsUtil.generateAssociatedRecords(local),
                AssociatedRecordsUtil.generateAssociatedRecords(local, Map.of(), UPSTREAM)
        );
    }

    @Test
    public void testGenerateAssociatedRecords_withoutHarvestSource_ignoresUpstream() {
        Map<String, Object> upstream = new LinkedHashMap<>();
        upstream.put("children", List.of(record("0f65b7ae-1f6f-4a55-b804-1c991f791e1a", "AUV Iver", "abstract")));

        assertTrue(AssociatedRecordsUtil.generateAssociatedRecords(Map.of(), upstream, null).isEmpty());
    }
}
