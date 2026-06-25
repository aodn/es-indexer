package au.org.aodn.esindexer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the acronym-building logic (Steps 2-8): no ES, no network, no Spring.
 * Each test feeds canned Organisation vocab concepts into {@link AcronymService#buildAcronymList}
 * and asserts the exact "short => long" rules it produces.
 */
class AcronymServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Step 3 + 5 - acronym on the left, display label on the right, with the "(...)" tail removed.
     * in:  display_label "Australian Antarctic Division (AAD)", hidden ["AAD"]
     * out: ["aad => australian antarctic division"]
     */
    @Test
    void buildsRuleFromDisplayLabel() {
        var rules = service().buildAcronymList(vocab(
                concept("Australian Antarctic Division (AAD)", "AAD")));

        assertEquals(List.of("aad => australian antarctic division"), rules);
    }

    /**
     * Step 3 - when there is no display label, the full name comes from the prefLabel.
     * in:  label "Bureau of Meteorology" (no display_label), hidden ["BOM"]
     * out: ["bom => bureau of meteorology"]
     */
    @Test
    void fallsBackToPrefLabel() {
        var rules = service().buildAcronymList(vocab(
                conceptUsingPrefLabel("Bureau of Meteorology", "BOM")));

        assertEquals(List.of("bom => bureau of meteorology"), rules);
    }

    /**
     * Step 3 - a single lowercase letter still counts as an acronym (DoD, MoU).
     * in:  display_label "Department of Defence", hidden ["DoD"]
     * out: ["dod => department of defence"]
     */
    @Test
    void keepsSingleLowercaseAcronym() {
        var rules = service().buildAcronymList(vocab(
                concept("Department of Defence", "DoD")));

        assertEquals(List.of("dod => department of defence"), rules);
    }

    /**
     * Step 3 - ordinary words and product codes are not acronyms, so the concept yields no rule.
     * in:  hidden ["Australian", "Data61"]   (long lowercase run / embedded digits)
     * out: []
     */
    @Test
    void rejectsNonAcronymLabels() {
        var rules = service().buildAcronymList(vocab(
                concept("Some Organisation", "Australian", "Data61")));

        assertTrue(rules.isEmpty(), "no acronym-like label, so no rule; got " + rules);
    }

    /**
     * Step 3 - several acronyms for one full name are joined on the left with ", ".
     * in:  display_label "Australian Antarctic Division", hidden ["AAD", "ANTDIV"]
     * out: ["aad, antdiv => australian antarctic division"]
     */
    @Test
    void joinsMultipleAcronyms() {
        var rules = service().buildAcronymList(vocab(
                concept("Australian Antarctic Division", "AAD", "ANTDIV")));

        assertEquals(List.of("aad, antdiv => australian antarctic division"), rules);
    }

    /**
     * Step 4 - data/product codes in SPECIAL_CASES are removed; with none left the rule is dropped.
     * in:  display_label "Carbon Dioxide", hidden ["CO2"]
     * out: []
     */
    @Test
    void dropsSpecialCaseCodes() {
        var rules = service().buildAcronymList(vocab(
                concept("Carbon Dioxide", "CO2")));

        assertTrue(rules.isEmpty(), "co2 should be dropped; got " + rules);
    }

    /**
     * Step 6 - manual rules from config are appended even when the vocab gives nothing.
     * in:  vocab [], manual ["nrmn => national reef monitoring network"]
     * out: ["nrmn => national reef monitoring network"]
     */
    @Test
    void appendsManualRules() {
        var rules = service("nrmn => national reef monitoring network").buildAcronymList(vocab());

        assertEquals(List.of("nrmn => national reef monitoring network"), rules);
    }

    /**
     * Step 6 - blank manual entries are trimmed away.
     * in:  manual ["  ", "", "nrmn => national reef monitoring network"]
     * out: ["nrmn => national reef monitoring network"]
     */
    @Test
    void dropsBlankManualRules() {
        var rules = service("  ", "", "nrmn => national reef monitoring network").buildAcronymList(vocab());

        assertEquals(List.of("nrmn => national reef monitoring network"), rules);
    }

    /**
     * Step 7 - the same line from both vocab and manual is de-duplicated to a single rule.
     * in:  vocab "aa => aurora australis", manual "aa => aurora australis"
     * out: ["aa => aurora australis"]
     */
    @Test
    void collapsesDuplicateLines() {
        var rules = service("aa => aurora australis").buildAcronymList(vocab(
                concept("Aurora Australis", "AA")));

        assertEquals(List.of("aa => aurora australis"), rules);
    }

    /**
     * Step 7 - manual never overwrites the vocab: the same acronym with a different full name keeps both.
     * in:  vocab "nrmn => northern rivers music network", manual "nrmn => national reef monitoring network"
     * out: ["nrmn => national reef monitoring network", "nrmn => northern rivers music network"]
     */
    @Test
    void keepsBothExpansionsOfSameAcronym() {
        var rules = service("nrmn => national reef monitoring network").buildAcronymList(vocab(
                concept("Northern Rivers Music Network", "NRMN")));

        assertEquals(List.of(
                "nrmn => national reef monitoring network",
                "nrmn => northern rivers music network"), rules);
    }

    /**
     * Step 2 + 8 - nested "narrower" children are walked too, and the result is sorted A -> Z.
     * in:  root "BOM" with child "AAD"
     * out: ["aad => australian antarctic division", "bom => bureau of meteorology"]
     */
    @Test
    void flattensChildrenAndSorts() {
        var bom = concept("Bureau of Meteorology", "BOM");
        bom.withArray("narrower").add(concept("Australian Antarctic Division", "AAD"));

        var rules = service().buildAcronymList(vocab(bom));

        assertEquals(List.of(
                "aad => australian antarctic division",
                "bom => bureau of meteorology"), rules);
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** A service with the given manual rules; the ES client and vocab service are unused here. */
    private static AcronymService service(String... manualRules) {
        return new AcronymService("test-set", List.of(manualRules), null, null);
    }

    /** A vocab concept whose full name comes from its display label. */
    private static ObjectNode concept(String displayLabel, String... hiddenLabels) {
        return conceptNode("display_label", displayLabel, hiddenLabels);
    }

    /** A vocab concept with no display label, so the full name falls back to its prefLabel. */
    private static ObjectNode conceptUsingPrefLabel(String prefLabel, String... hiddenLabels) {
        return conceptNode("label", prefLabel, hiddenLabels);
    }

    private static ObjectNode conceptNode(String nameField, String name, String... hiddenLabels) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(nameField, name);
        var labels = node.putArray("hidden_labels");
        for (String label : hiddenLabels) {
            labels.add(label);
        }
        return node;
    }

    private static List<JsonNode> vocab(JsonNode... concepts) {
        return List.of(concepts);
    }
}
