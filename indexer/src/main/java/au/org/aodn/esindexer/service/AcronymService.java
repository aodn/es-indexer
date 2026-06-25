package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns the acronym synonyms use-case: turn the ARDC Organisation vocabulary into ES synonyms.
 *
 * Flow:
 *   ARDC Organisation vocabulary (https://vocabs.ardc.edu.au/)
 *     -> cached in vocabs_index (populated upstream by the ardcvocabs module)
 *     -> this service reads it and builds "short => long" rules
 *     -> pushed into the ES synonyms set.
 */
@Slf4j
public class AcronymService {

    private final ElasticsearchClient portalElasticsearchClient;
    private final VocabService vocabService;

    /** Step 5 - acronyms that are data/product codes, not organisations; dropped from every rule. */
    private static final Set<String> SPECIAL_CASES = Set.of("co2", "sst l2p");

    @Getter
    private final String synonymSetName;

    public AcronymService(
            String synonymSetName,
            ElasticsearchClient portalElasticsearchClient,
            VocabService vocabService) {
        this.synonymSetName = synonymSetName;
        this.portalElasticsearchClient = portalElasticsearchClient;
        this.vocabService = vocabService;
    }

    /**
     * Push the acronyms into the ES synonyms set (creates or fully replaces it; overwrites, never appends).
     *
     * Source:
     *   Every rule comes only from the Organisation vocab, mapping each acronym to its full name:
     *   hidden label => display label   (or prefLabel when there is no display label).
     *
     * Triggers:
     *   - automatically, during a full portal_records reindex
     *     (IndexerMetadataServiceImpl runs this just before recreating the index);
     *   - on demand, via POST /api/v1/indexer/index/acronyms  (a live update that needs no reindex).
     *
     * The flow is intentionally split into small, ordered steps so it is easy to follow in the logs.
     */
    public void pushAcronymListToElasticsearch() throws IOException {
        log.info("Pushing acronym list into synonyms set '{}'", synonymSetName);

        List<JsonNode> vocabs = fetchVocabs();              // Step 1   - read the Organisation vocab
        List<String> acronymList = buildAcronymList(vocabs); // Steps 2-7 - build the cleaned, sorted list

        // If no rules came from the vocab (e.g. vocabs not indexed yet), keep the existing set untouched.
        if (acronymList.isEmpty()) {
            log.warn("No acronym rules came from the vocab; leaving synonyms set '{}' unchanged", synonymSetName);
            return;
        }

        replaceSynonymsSet(acronymList);                    // Step 8   - overwrite the ES synonyms set
    }

    /**
     * Read-only preview: the "short => long" rules that come from vocabs_index, WITHOUT touching the
     * synonyms set. Also reports the vocabs_index source version so you can see how fresh it is.
     * Use this to inspect what {@link #pushAcronymListToElasticsearch()} would push.
     */
    public AcronymPreview previewAcronyms() {
        List<JsonNode> vocabs = fetchVocabs();
        String version = vocabVersion(vocabs);
        List<String> acronymList = buildAcronymList(vocabs);
        log.info("Preview - {} acronym rules come from vocabs_index version '{}' (NOT pushed)",
                acronymList.size(), version);
        return new AcronymPreview(version, acronymList.size(), acronymList);
    }

    /** The vocabs_index source version, e.g. "version-2-5/version-2-5" (empty if unknown). */
    private static String vocabVersion(List<JsonNode> vocabs) {
        return (!vocabs.isEmpty() && vocabs.get(0).has("version"))
                ? vocabs.get(0).get("version").asText()
                : "";
    }

    /** Preview payload: the vocabs_index version it came from, the rule count, and the rules. */
    public record AcronymPreview(String vocabVersion, int count, List<String> rules) {}

    // ----------------------------------------------------------------------------------------------
    // Steps (run top-to-bottom; each one is small and self-contained)
    // ----------------------------------------------------------------------------------------------

    /**
     * Step 1 - read the Organisation vocab trees from vocabs_index.
     * in:  (reads the cached vocabs_index)
     * out: list of Organisation root nodes; empty if the index is missing/unavailable.
     */
    private List<JsonNode> fetchVocabs() {
        try {
            List<JsonNode> organisationVocabs = vocabService.getOrganisationVocabs();
            log.info("Step 1 - read {} organisation vocab roots from vocabs_index", organisationVocabs.size());
            return organisationVocabs;
        } catch (Exception e) {
            // vocabs_index may be empty/unavailable here; never let this step break a reindex.
            log.warn("Step 1 - could not read vocabs_index, no acronyms will come from it: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Steps 2-7 - the pipeline: turn the vocab trees into the final, sorted rule list.
     * in:  Organisation vocab trees
     * out: ["aad => australian antarctic division", "bom => bureau of meteorology", ...]
     */
    private List<String> buildAcronymList(List<JsonNode> vocabTrees) {
        List<JsonNode> concepts = flattenConcepts(vocabTrees);                  // Step 2 - flatten trees -> concepts
        log.info("Step 2 - {} concepts from {} roots", concepts.size(), vocabTrees.size());

        List<String> rules = concepts.stream()                                  // Step 3 - one rule per concept
                .map(AcronymService::buildSynonymRule)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        log.info("Step 3 - {} rules built", rules.size());

        rules = new ArrayList<>(new LinkedHashSet<>(rules));                    // Step 4 - drop duplicate lines
        log.info("Step 4 - {} rules after de-duplication", rules.size());

        rules = dropSpecialCases(rules);                                        // Step 5 - drop CO2, SST L2P
        log.info("Step 5 - {} rules after dropping special cases", rules.size());

        rules = stripParens(rules);                                            // Step 6 - remove "(...)"
        log.info("Step 6 - {} rules after removing brackets", rules.size());

        rules.sort(String::compareTo);                                         // Step 7 - sort A -> Z
        log.info("Step 7 - {} final rules (sorted)", rules.size());

        return rules;
    }

    /**
     * Step 2 - flatten: walk each root and its nested "narrower" children into one flat list of concepts.
     * in:  [ root { narrower: [ child { narrower: [grandchild] } ] } ]
     * out: [ root, child, grandchild ]   (so the steps below see every concept, not just the 31 roots).
     */
    private static List<JsonNode> flattenConcepts(List<JsonNode> roots) {
        List<JsonNode> concepts = new ArrayList<>();
        roots.forEach(root -> collectConcept(root, concepts));
        return concepts;
    }

    private static void collectConcept(JsonNode concept, List<JsonNode> concepts) {
        if (concept == null) {
            return;
        }
        concepts.add(concept);
        JsonNode children = concept.get("narrower");
        if (children != null && children.isArray()) {
            children.forEach(child -> collectConcept(child, concepts));
        }
    }

    /**
     * Step 3 - turn ONE concept into a rule: acronyms on the left, full name on the right.
     * in:  { display_label: "Australian Antarctic Division (AAD)", hidden_labels: ["AAD"] }
     * out: "aad => australian antarctic division (aad)"   (null when the concept has no usable acronym).
     */
    private static String buildSynonymRule(JsonNode concept) {
        String fullName = normaliseLabel(fullForm(concept));     // the long form (right side)
        if (fullName == null) {
            return null;
        }
        JsonNode hiddenLabels = concept.get("hidden_labels");
        if (hiddenLabels == null || !hiddenLabels.isArray()) {
            return null;
        }
        List<String> acronyms = new ArrayList<>();               // only acronym-like labels go on the left
        for (JsonNode hiddenLabel : hiddenLabels) {
            if (hiddenLabel == null || hiddenLabel.isNull()) {
                continue;
            }
            if (!isAcronym(hiddenLabel.asText())) {              // skip full-name variants, keep AAD/DoD
                continue;
            }
            String acronym = normaliseLabel(hiddenLabel.asText());
            if (acronym != null && !acronym.equals(fullName) && !acronyms.contains(acronym)) {
                acronyms.add(acronym);
            }
        }
        return acronyms.isEmpty() ? null : String.join(", ", acronyms) + " => " + fullName;
    }

    /**
     * Step 3 helper - pick the full name and make it rule-safe (a comma would break synonym syntax).
     * in:  { display_label: "Department of Agriculture, Fisheries (DAFF)" }
     * out: "Department of Agriculture Fisheries (DAFF)"   (falls back to prefLabel when no display_label).
     */
    private static String fullForm(JsonNode concept) {
        String displayLabel = textOf(concept, "display_label");
        String name = (displayLabel != null && !displayLabel.trim().isEmpty())
                ? displayLabel
                : textOf(concept, "label");                      // ARDC prefLabel
        if (name == null) {
            return null;
        }
        return name.replace(",", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Step 3 helper - is this label an acronym? Keep BOM/AAD/DoD/MoU, reject ordinary words and codes.
     * in:  "AAD" -> true,  "DoD" -> true,  "Data61" -> false,  "Australian" -> false
     * out: true when short (<=12), has a capital, and has no run of 2+ lowercase letters.
     */
    private static boolean isAcronym(String label) {
        if (label == null) {
            return false;
        }
        String text = label.trim();
        if (text.isEmpty() || text.length() > 12) {
            return false;
        }
        boolean hasLetter = text.chars().anyMatch(Character::isLetter);
        boolean hasCapital = text.chars().anyMatch(Character::isUpperCase);
        if (!hasLetter || !hasCapital) {
            return false;
        }
        int longestLowercaseRun = 0, currentRun = 0;
        for (char c : text.toCharArray()) {
            currentRun = Character.isLowerCase(c) ? currentRun + 1 : 0;
            longestLowercaseRun = Math.max(longestLowercaseRun, currentRun);
        }
        return longestLowercaseRun < 2;                          // all-caps (0) or a single lowercase like DoD (1)
    }

    /**
     * Step 3 helper - tidy a label for use in a rule: lowercase + trim, or null if it cannot be used.
     * in:  "  AAD " -> "aad",   "a, b" -> null   (',' and '=>' are reserved in synonym syntax).
     */
    private static String normaliseLabel(String label) {
        if (label == null) {
            return null;
        }
        String text = label.trim().toLowerCase();
        return (text.isEmpty() || text.contains(",") || text.contains("=>")) ? null : text;
    }

    /**
     * Step 5 - remove data/product codes (CO2, SST L2P) from the left side; drop the rule if none remain.
     * in:  "co2 => carbon dioxide"            -> (removed)
     * in:  "bom, sst l2p => bureau of met..." -> "bom => bureau of met..."
     */
    private static List<String> dropSpecialCases(List<String> rules) {
        List<String> kept = new ArrayList<>();
        for (String rule : rules) {
            int arrowAt = rule.indexOf(" => ");
            String acronymsPart = rule.substring(0, arrowAt);
            String fullNamePart = rule.substring(arrowAt);       // keeps " => full name"
            List<String> acronyms = new ArrayList<>();
            for (String acronym : acronymsPart.split(",")) {
                acronym = acronym.trim();
                if (!acronym.isEmpty() && !SPECIAL_CASES.contains(acronym)) {
                    acronyms.add(acronym);
                }
            }
            if (!acronyms.isEmpty()) {
                kept.add(String.join(", ", acronyms) + fullNamePart);
            }
        }
        return kept;
    }

    /**
     * Step 6 - drop the "(...)" tail from the full name so it reads cleanly.
     * in:  "aad => australian antarctic division (aad)"
     * out: "aad => australian antarctic division"
     */
    private static List<String> stripParens(List<String> rules) {
        List<String> cleaned = new ArrayList<>();
        for (String rule : rules) {
            int arrowAt = rule.indexOf(" => ");
            String acronymsPart = rule.substring(0, arrowAt);
            String fullName = rule.substring(arrowAt + 4)
                    .replaceAll("\\s*\\([^)]*\\)", "")               // remove "(...)"
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!fullName.isEmpty()) {
                cleaned.add(acronymsPart + " => " + fullName);
            }
        }
        return cleaned;
    }

    /**
     * Step 8 - overwrite the ES synonyms set with the rules (full replace, never append).
     * in:  ["aad => australian antarctic division", "bom => bureau of meteorology"]
     * out: synonyms set now holds exactly those rules (live; the search analyser reloads automatically).
     */
    private void replaceSynonymsSet(List<String> rules) throws IOException {
        List<SynonymRule> synonymRules = rules.stream()
                .map(rule -> SynonymRule.of(r -> r.synonyms(rule)))
                .collect(Collectors.toList());
        portalElasticsearchClient.synonyms().putSynonym(b -> b
                .id(synonymSetName)
                .synonymsSet(synonymRules));
        log.info("Step 8 - replaced synonyms set '{}' with {} rules", synonymSetName, synonymRules.size());
    }

    /** Read a string field from a concept, or null when it is missing/blank. */
    private static String textOf(JsonNode concept, String field) {
        JsonNode value = concept.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
