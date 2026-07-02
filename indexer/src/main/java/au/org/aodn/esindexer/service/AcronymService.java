package au.org.aodn.esindexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import au.org.aodn.esindexer.exception.DocumentNotFoundException;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import co.elastic.clients.elasticsearch.synonyms.SynonymRuleRead;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
 *     -> this service reads it and builds "short => long" rules   (e.g. "aad => australian antarctic division")
 *     -> appends the manual rules from config
 *     -> pushed into the ES synonyms set.
 */
@Slf4j
public class AcronymService {

    private final ElasticsearchClient portalElasticsearchClient;
    private final VocabService vocabService;

    /** Step 4 - acronyms that are data/product codes, not organisations; dropped from every rule. */
    private static final Set<String> SPECIAL_CASES = Set.of("co2", "sst l2p");

    private static final ZoneId AUSTRALIAN_ZONE = ZoneId.of("Australia/Sydney");
    private static final String META_DOC_ID = "acronyms";

    /** GET _synonyms defaults to 10 rules per page; a set is capped at 10000, so ask for them all. */
    private static final int MAX_SYNONYM_RULES = 10_000;

    /** Manual "short => long" rules from config, for acronyms the vocab misses (e.g. nrmn); appended, never overwrites - if the vocab has the same acronym, both expansions are kept. */
    private final List<String> manualAcronyms;

    @Getter
    private final String synonymSetName;

    public AcronymService(
            String synonymSetName,
            List<String> manualAcronyms,
            ElasticsearchClient portalElasticsearchClient,
            VocabService vocabService) {
        this.synonymSetName = synonymSetName;
        this.manualAcronyms = manualAcronyms != null ? manualAcronyms : List.of();
        this.portalElasticsearchClient = portalElasticsearchClient;
        this.vocabService = vocabService;
    }

    /**
     * Push the acronyms into the ES synonyms set (creates or fully replaces it; overwrites, never appends).
     *
     * Source:
     *   Rules come from the Organisation vocab (each acronym mapped to its full name:
     *   hidden label => display label, or prefLabel when there is no display label),
     *   plus the manual rules from config (elasticsearch.acronyms.manual).
     *
     * Triggers:
     *   - automatically, during a full portal_records reindex
     *     (IndexerMetadataServiceImpl runs this just before recreating the index);
     *   - on demand, via POST /api/v1/indexer/index/acronyms  (a live update that needs no reindex).
     *
     * The flow is intentionally split into small, ordered steps so it is easy to follow in the logs.
     * @return the number of rules pushed; 0 means the vocab was empty and the set was left unchanged.
     */
    public int pushAcronymListToElasticsearch() throws IOException {
        log.info("Pushing acronym list into synonyms set '{}'", synonymSetName);

        List<JsonNode> vocabs = fetchVocabs();              // Step 1   - read the Organisation vocab
        List<String> acronymList = buildAcronymList(vocabs); // Steps 2-8 - build list + append manual rules

        // If no rules came from the vocab (e.g. vocabs not indexed yet), keep the existing set untouched.
        if (acronymList.isEmpty()) {
            log.warn("No acronym rules came from the vocab; leaving synonyms set '{}' unchanged", synonymSetName);
            return 0;
        }

        replaceSynonymsSet(acronymList);                    // Step 9   - overwrite the ES synonyms set
        stampLastModified();                                // Step 10  - record when it was last pushed
        return acronymList.size();
    }

    /**
     * Read-only preview: the "short => long" rules that come from vocabs_index, WITHOUT touching the
     * synonyms set. Also reports the vocabs_index source version so you can see how fresh it is.
     * Use this to inspect what {@link #pushAcronymListToElasticsearch()} would push.
     */
    public AcronymPreview previewAcronyms() throws IOException {
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

    /** Sync payload: which set was pushed, how many rules (0 = nothing came from the vocab), and a human message. */
    public record AcronymSyncResult(String synonymSetName, int count, String message) {}

    /** Read-only: the rules currently live in the ES synonyms set (empty if nothing pushed yet). */
    public AcronymCurrent currentAcronyms() throws IOException {
        List<String> rules;
        try {
            rules = portalElasticsearchClient.synonyms().getSynonym(g -> g.id(synonymSetName).size(MAX_SYNONYM_RULES))
                    .synonymsSet().stream()
                    .map(SynonymRuleRead::synonyms)
                    .filter(Objects::nonNull)
                    .sorted(String::compareTo)          // same order as the preview
                    .collect(Collectors.toList());
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                throw e;    // a real ES error - surface it, don't pretend the set is empty
            }
            log.info("Synonyms set '{}' does not exist yet; reporting it as empty", synonymSetName);
            rules = new ArrayList<>();
        }
        String lastModified = readLastModified();
        log.info("Current - synonyms set '{}' holds {} acronym rules (last modified {})",
                synonymSetName, rules.size(), lastModified);
        return new AcronymCurrent(synonymSetName, lastModified, rules.size(), rules);
    }

    /** Current payload: the set name, when it was last pushed (Australian time, null if never), count, rules. */
    public record AcronymCurrent(String synonymSetName, String lastModified, int count, List<String> rules) {}

    /** Meta doc holding the last push time; ES tracks no modified date for a synonyms set, so we store our own. */
    record AcronymMeta(String lastModified) {}

    /** Step 10 - record "now" (Australian time, to the second) as the last push time; failure never breaks the push. */
    private void stampLastModified() {
        String now = ZonedDateTime.now(AUSTRALIAN_ZONE)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        try {
            portalElasticsearchClient.index(i -> i
                    .index(metaIndex())
                    .id(META_DOC_ID)
                    .document(new AcronymMeta(now)));
            log.info("Step 10 - stamped synonyms set '{}' last modified {}", synonymSetName, now);
        } catch (Exception e) {
            log.warn("Step 10 - could not stamp last-modified for '{}': {}", synonymSetName, e.getMessage());
        }
    }

    /** The last push time (Australian time), or null if it was never pushed / the meta doc is missing. */
    private String readLastModified() {
        try {
            var response = portalElasticsearchClient.get(g -> g.index(metaIndex()).id(META_DOC_ID), AcronymMeta.class);
            return response.found() && response.source() != null ? response.source().lastModified() : null;
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {    // 404 = never pushed; other errors are already surfaced by the rules read
                log.warn("Could not read last-modified for '{}': {}", synonymSetName, e.getMessage());
            }
            return null;
        } catch (IOException e) {
            log.warn("Could not read last-modified for '{}': {}", synonymSetName, e.getMessage());
            return null;
        }
    }

    private String metaIndex() {
        return synonymSetName + "-meta";
    }

    // ----------------------------------------------------------------------------------------------
    // Steps (run top-to-bottom; each one is small and self-contained)
    // ----------------------------------------------------------------------------------------------

    /**
     * Step 1 - read the Organisation vocab trees from vocabs_index.
     * in:  (reads the cached vocabs_index)
     * out: list of Organisation root nodes; empty when vocabs_index is not populated yet.
     * A real ES error propagates (callers surface it; the reindex guards its own call).
     */
    private List<JsonNode> fetchVocabs() throws IOException {
        try {
            List<JsonNode> organisationVocabs = vocabService.getOrganisationVocabs();
            log.info("Step 1 - read {} organisation vocab roots from vocabs_index", organisationVocabs.size());
            return organisationVocabs;
        } catch (DocumentNotFoundException e) {
            log.warn("Step 1 - vocabs_index not populated yet, no acronyms will come from it: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Steps 2-8 - the pipeline: build rules from the vocab, append the manual rules, dedupe and sort.
     * in:  Organisation vocab trees (+ manual rules from config)
     * out: ["aad => australian antarctic division", "bom => bureau of meteorology", ...]
     * Package-private so AcronymServiceTest can feed it canned vocab without ES or the network.
     */
    List<String> buildAcronymList(List<JsonNode> vocabTrees) {
        List<JsonNode> concepts = flattenConcepts(vocabTrees);                  // Step 2 - flatten trees -> concepts
        log.info("Step 2 - {} concepts from {} roots", concepts.size(), vocabTrees.size());

        List<String> rules = concepts.stream()                                  // Step 3 - one rule per concept
                .map(AcronymService::buildSynonymRule)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        log.info("Step 3 - {} rules built from the vocab", rules.size());

        rules = dropSpecialCases(rules);                                        // Step 4 - drop CO2, SST L2P
        log.info("Step 4 - {} rules after dropping special cases", rules.size());

        rules = stripParens(rules);                                            // Step 5 - remove "(...)"
        log.info("Step 5 - {} rules after removing brackets", rules.size());

        rules.addAll(manualRules());                                           // Step 6 - append manual rules
        log.info("Step 6 - {} rules after appending {} manual rules", rules.size(), manualRules().size());

        rules = new ArrayList<>(new LinkedHashSet<>(rules));                    // Step 7 - drop duplicate lines
        log.info("Step 7 - {} rules after de-duplication", rules.size());

        rules.sort(String::compareTo);                                         // Step 8 - sort A -> Z
        log.info("Step 8 - {} final rules (sorted)", rules.size());

        return rules;
    }

    /** Step 6 - the manual rules from config, trimmed; blanks dropped. */
    private List<String> manualRules() {
        return manualAcronyms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(rule -> !rule.isEmpty())
                .collect(Collectors.toList());
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
     * Step 4 - remove data/product codes (CO2, SST L2P) from the left side; drop the rule if none remain.
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
     * Step 5 - drop the "(...)" tail from the full name so it reads cleanly.
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
     * Step 9 - overwrite the ES synonyms set with the rules (full replace, never append).
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
        log.info("Step 9 - replaced synonyms set '{}' with {} rules", synonymSetName, synonymRules.size());
    }

    /** Read a string field from a concept, or null when it is missing/blank. */
    private static String textOf(JsonNode concept, String field) {
        JsonNode value = concept.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
