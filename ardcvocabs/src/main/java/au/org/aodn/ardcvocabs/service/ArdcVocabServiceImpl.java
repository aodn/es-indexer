package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.exception.ExtractingPathVersionsException;
import au.org.aodn.ardcvocabs.exception.InvalidVersionFormatException;
import au.org.aodn.ardcvocabs.exception.VocabHarvestIncompleteException;
import au.org.aodn.ardcvocabs.model.ArdcCurrentPaths;
import au.org.aodn.ardcvocabs.model.Name;
import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ArdcVocabServiceImpl implements ArdcVocabService {

    @Value("${ardcvocabs.baseUrl:https://vocabs.ardc.edu.au/repository/api/lda/aodn}")
    protected String vocabApiBase;

    protected RestTemplate restTemplate;
    protected RetryTemplate retryTemplate;
    protected int maxConsecutiveItemFailures;

    /**
     * State scoped to one ARDC vocabulary harvest.
     */
    protected static class HarvestRun {
        // the completed vocabulary models, keyed by their ARDC resource URI.
        // Used to avoid fetching and building the same resource more than once during a harvest
        protected final Map<String, VocabModel> memo = new HashMap<>();
        // Original ARDC JSON nodes, keyed by resource URI.
        protected final Map<String, JsonNode> detailTargets = new HashMap<>();
        // Resource URIs currently being processed by the recursive model builder.
        protected final Set<String> inProgress = new HashSet<>();
        //Number of consecutive retryable requests that remained unsuccessful after all retry attempts were exhausted
        protected int consecutiveFailures;
        // Resets the consecutive-failure count after a successful request.
        protected void recordSuccess() {
            consecutiveFailures = 0;
        }
        // Records an exhausted retryable failure.
        protected int recordFailure() {
            return ++consecutiveFailures;
        }
    }

    protected static final String VERSION_REGEX = "/(version-\\d+-\\d+)(?:/|$)";

    protected Map<Name, String> getVersionedArdcPath(ArdcCurrentPaths currentPath) {
        return getVersionedArdcPath(currentPath, null);
    }

    protected Map<Name, String> getVersionedArdcPath(ArdcCurrentPaths currentPath, HarvestRun run) {
        try {
            // Fetch current contents
            ObjectNode categoryCurrentContent = fetchCurrentContents(currentPath.getCategoryCurrent(), run);
            ObjectNode vocabCurrentContent = fetchCurrentContents(currentPath.getVocabCurrent(), run);
            validateContentNotNull(currentPath, categoryCurrentContent, vocabCurrentContent);

            // Extract versions
            String categoryVersion = extractVersionFromCurrentContent(categoryCurrentContent);
            String vocabVersion = extractVersionFromCurrentContent(vocabCurrentContent);
            validateVersionsNotNull(currentPath, categoryVersion, vocabVersion);

            log.info("Fetched ARDC category version for {}: {}", currentPath.name(), categoryVersion);
            log.info("Fetched ARDC vocab version for {}: {}", currentPath.name(), vocabVersion);

            // Build and store resolved paths
            return buildResolvedPaths(currentPath, categoryVersion, vocabVersion);

        } catch (VocabHarvestIncompleteException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error initialising versions for {}: {}", currentPath.name(), e.getMessage(), e);
            throw new ExtractingPathVersionsException(String.format("Error initialising versions for %s: %s", currentPath.name(), e.getMessage()));
        }
    }

    protected void validateContentNotNull(ArdcCurrentPaths currentPath, ObjectNode categoryContent, ObjectNode vocabContent) {
        if (categoryContent == null || vocabContent == null) {
            throw new ExtractingPathVersionsException(String.format("Failed to fetch HTML content for %s", currentPath.name()));
        }
    }

    protected void validateVersionsNotNull(ArdcCurrentPaths currentPath, String categoryVersion, String vocabVersion) {
        if (categoryVersion == null || vocabVersion == null) {
            throw new ExtractingPathVersionsException(String.format("Version extraction returned null for %s", currentPath.name()));
        }
    }

    protected ObjectNode fetchCurrentContents(String url, HarvestRun run) {
        try {
            ObjectNode result = retryTemplate.execute(context -> restTemplate.getForObject(url, ObjectNode.class));
            recordSuccess(run);
            return result;
        }
        catch (RestClientException e) {
            log.error("Failed to fetch HTML content from URL {}: {}", url, e.getMessage());
            throw e;
        }
        catch (Exception e) {
            log.error("Unexpected error while fetching HTML content from URL {}: {}", url, e.getMessage(), e);
            throw e;
        }
    }

    protected Map<Name, String> buildResolvedPaths(ArdcCurrentPaths currentPaths, String categoryVersion, String vocabVersion) {
        Map<Name, String> resolvedPaths = new HashMap<>();
        for (VocabApiPaths vocabApiPath : VocabApiPaths.values()) {
            if (currentPaths.name().equals(vocabApiPath.name())) {
                resolvedPaths.put(Name.version, categoryVersion + "/" + vocabVersion);
                resolvedPaths.put(Name.categoryApi, String.format(vocabApiPath.getCategoryApiTemplate(), categoryVersion));
                resolvedPaths.put(Name.categoryDetailsApi, String.format(vocabApiPath.getCategoryDetailsTemplate(), categoryVersion, "%s"));
                resolvedPaths.put(Name.vocabApi, String.format(vocabApiPath.getVocabApiTemplate(), vocabVersion));
                resolvedPaths.put(Name.vocabDetailsApi, String.format(vocabApiPath.getVocabDetailsTemplate(), vocabVersion, "%s"));
            }
        }
        return resolvedPaths;
    }

    protected String extractVersionFromCurrentContent(ObjectNode currentContent) {
        if (currentContent != null && !currentContent.isEmpty()) {
            JsonNode node = currentContent.get("result");
            if (!about.apply(node).isEmpty()) {
                Pattern pattern = Pattern.compile(VERSION_REGEX);
                Matcher matcher = pattern.matcher(about.apply(node));

                if (matcher.find()) {
                    String version = matcher.group(1);
                    log.info("Valid Version Found: {}", version);
                    return version;
                } else {
                    throw new InvalidVersionFormatException(String.format("Version does not match the required format: %s", about.apply(node)));
                }
            }
        } else {
            log.warn("Current content is empty or null.");
        }
        return null;
    }

    protected Function<JsonNode, String> extractSingleText(String key) {
        return (node) -> {
            JsonNode labelNode = node.get(key);
            if (labelNode != null) {
                if (labelNode.has("_value")) {
                    return labelNode.get("_value").asText();
                }
                if (labelNode instanceof TextNode) {
                    return labelNode.asText();
                }
            }
            return null;
        };
    }
    protected Function<JsonNode, List<String>> extractMultipleTexts(String key) {
        return (node) -> {
            JsonNode labelNode = node.get(key);
            if (labelNode != null && labelNode.isArray()) {
                return StreamSupport.stream(labelNode.spliterator(), false)
                        .filter(Objects::nonNull)
                        .map(i -> i.get("_value").asText())
                        .collect(Collectors.toList());
            }
            return null;
        };
    }

    // Reusing the utility methods for specific labels
    protected Function<JsonNode, String> label = extractSingleText("prefLabel");
    protected Function<JsonNode, String> displayLabel = extractSingleText("displayLabel");
    protected Function<JsonNode, List<String>> hiddenLabels = extractMultipleTexts("hiddenLabel");
    protected Function<JsonNode, List<String>> altLabels = extractMultipleTexts("altLabel");
    protected Function<JsonNode, String> about = extractSingleText("_about");
    protected Function<JsonNode, String> definition = extractSingleText("definition");
    protected Function<JsonNode, Boolean> isLatestLabel = (node) -> !(node.has("isReplacedBy") || (node.has("scopeNote") && extractSingleText("scopeNote").apply(node).toLowerCase().contains("no longer exists")));
    protected Function<JsonNode, Boolean> isReplacedBy = (node) -> node.has("isReplacedBy") && node.has("scopeNote") && extractSingleText("scopeNote").apply(node).toLowerCase().contains("replaced by");

    private String extractReplacedVocabUri(String scopeNote) {
        String regex = "Replaced by (https?://[\\w./-]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(scopeNote);

        if (matcher.find()) {
            String result = matcher.group(1);
            if (result.endsWith(".")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        }

        return null;
    }

    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    public ArdcVocabServiceImpl(RestTemplate restTemplate, RetryTemplate retryTemplate) {
        this(restTemplate, retryTemplate, 5);
    }

    public ArdcVocabServiceImpl(
            RestTemplate restTemplate,
            RetryTemplate retryTemplate,
            int maxConsecutiveItemFailures) {
        this.restTemplate = restTemplate;
        this.retryTemplate = retryTemplate;
        this.maxConsecutiveItemFailures = Math.max(1, maxConsecutiveItemFailures);
    }

    protected VocabModel buildVocabByResourceUri(String vocabUri, String vocabApiBase, Map<Name, String> pointers) {
        return buildVocabByResourceUri(vocabUri, vocabApiBase, pointers, new HarvestRun());
    }

    protected VocabModel buildVocabByResourceUri(
            String vocabUri,
            String vocabApiBase,
            Map<Name, String> pointers,
            HarvestRun run) {
        VocabModel memoized = run.memo.get(vocabUri);
        if (memoized != null) {
            return memoized;
        }
        if (run.inProgress.contains(vocabUri)) {
            log.warn("Cycle detected while building ARDC vocabulary resource {}", vocabUri);
            return null;
        }

        String resourceDetailsApi = vocabUri.contains("_classes")
                ? pointers.get(Name.categoryDetailsApi)
                : pointers.get(Name.vocabDetailsApi);

        String detailsUrl = String.format(vocabApiBase + resourceDetailsApi, vocabUri);

        try {
            log.debug("Query api -> {}", detailsUrl);
            ObjectNode detailsObj = retryTemplate.execute(context -> restTemplate.getForObject(detailsUrl, ObjectNode.class));
            recordSuccess(run);
            if(isNodeValid.apply(detailsObj, "result") && isNodeValid.apply(detailsObj.get("result"), "primaryTopic")) {
                JsonNode target = detailsObj.get("result").get("primaryTopic");
                run.detailTargets.put(vocabUri, target);
                return buildVocabFromTarget(vocabUri, target, vocabApiBase, pointers, run);
            }
            log.warn("Invalid ARDC vocabulary payload at {}; skipping item", detailsUrl);
        } catch (VocabHarvestIncompleteException e) {
            throw e;
        } catch (Exception e) {
            if (isNotFound(e)) {
                log.warn("ARDC vocabulary resource not found at {}; skipping item", detailsUrl);
                return null;
            }
            if (isRetryableFailure(e)) {
                run.recordFailure();
                throw incomplete(detailsUrl, run, e);
            }
            if (e instanceof RestClientException) {
                throw incomplete(detailsUrl, run, e);
            }
            log.warn("Invalid ARDC vocabulary resource at {}; skipping item", detailsUrl, e);
        }
        return null;
    }

    protected VocabModel buildVocabFromTarget(
            String vocabUri,
            JsonNode target,
            String vocabApiBase,
            Map<Name, String> pointers,
            HarvestRun run) {
        VocabModel memoized = run.memo.get(vocabUri);
        if (memoized != null) {
            return memoized;
        }
        if (!run.inProgress.add(vocabUri)) {
            log.warn("Cycle detected while building ARDC vocabulary resource {}", vocabUri);
            return null;
        }

        try {
            VocabModel vocab = VocabModel
                    .builder()
                    .label(label.apply(target))
                    .definition(definition.apply(target))
                    .about(vocabUri)
                    .version(pointers.get(Name.version))
                    .displayLabel(displayLabel.apply(target))
                    .hiddenLabels(hiddenLabels.apply(target))
                    .altLabels(altLabels.apply(target))
                    .isLatestLabel(isLatestLabel.apply(target))
                    .build();

            if (!vocab.getIsLatestLabel() && isReplacedBy.apply(target)) {
                vocab.setReplacedBy(extractReplacedVocabUri(extractSingleText("scopeNote").apply(target)));
            }

            List<VocabModel> narrowerNodes = new ArrayList<>();
            if (isNodeValid.apply(target, "narrower")) {
                for (JsonNode narrower : target.get("narrower")) {
                    String narrowerUri = narrower.isTextual() ? narrower.asText() : about.apply(narrower);
                    if (narrowerUri != null && !narrowerUri.isEmpty()) {
                        VocabModel narrowerNode = buildVocabByResourceUri(narrowerUri, vocabApiBase, pointers, run);
                        if (narrowerNode != null) {
                            narrowerNodes.add(narrowerNode);
                        }
                    }
                }
            }

            if (!narrowerNodes.isEmpty()) {
                vocab.setNarrower(narrowerNodes);
            }

            run.memo.put(vocabUri, vocab);
            run.detailTargets.put(vocabUri, target);
            return vocab;
        } finally {
            run.inProgress.remove(vocabUri);
        }
    }

    protected <T> VocabModel buildVocabModel(T currentNode, String vocabApiBase, Map<Name, String> pointers) {
        return buildVocabModel(currentNode, vocabApiBase, pointers, new HarvestRun());
    }

    protected <T> VocabModel buildVocabModel(
            T currentNode,
            String vocabApiBase,
            Map<Name, String> pointers,
            HarvestRun run) {
        String resourceUri = null;

        if (currentNode instanceof ObjectNode objectNode) {
            resourceUri = objectNode.has("_about") ? about.apply(objectNode) : objectNode.asText();
        }
        else if (currentNode instanceof TextNode textNode) {
            resourceUri = textNode.asText();
        }
        else if (currentNode instanceof VocabModel vocabNode) {
            String about = vocabNode.getAbout();
            if (about != null && !about.isEmpty()) {
                resourceUri = about;
            }
        }

        if (resourceUri == null) {
            throw new IllegalArgumentException("Unsupported or empty vocabulary node");
        }

        return buildVocabByResourceUri(resourceUri, vocabApiBase, pointers, run);
    }

    protected void recordSuccess(HarvestRun run) {
        if (run != null) {
            run.recordSuccess();
        }
    }

    protected boolean isRetryableFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpClientErrorException.TooManyRequests
                    || current instanceof HttpServerErrorException
                    || current instanceof ResourceAccessException) {
                return true;
            }
        }
        return false;
    }

    protected boolean isNotFound(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpClientErrorException.NotFound) {
                return true;
            }
        }
        return false;
    }

    protected VocabHarvestIncompleteException incomplete(String url, HarvestRun run, Throwable cause) {
        return new VocabHarvestIncompleteException(
                url,
                run.consecutiveFailures,
                maxConsecutiveItemFailures,
                cause);
    }

    protected Map<String, List<VocabModel>> getVocabLeafNodes(
            String vocabApiBase,
            Map<Name, String> pointers,
            HarvestRun run) {
        Map<String, List<VocabModel>> results = new HashMap<>();
        String url = String.format(vocabApiBase + pointers.get(Name.vocabApi));

        while (url != null && !url.isEmpty()) {
            String pageUrl = url;
            try {
                log.debug("getVocabLeafNodes -> {}", pageUrl);
                ObjectNode response = retryTemplate.execute(
                        context -> restTemplate.getForObject(pageUrl, ObjectNode.class));
                recordSuccess(run);

                if (response == null || response.isEmpty() || !isNodeValid.apply(response, "result")) {
                    throw new IllegalStateException("Invalid vocabulary listing payload");
                }
                JsonNode page = response.get("result");

                if (isNodeValid.apply(page, "items")) {
                    for (JsonNode item : page.get("items")) {
                        processVocabLeafItem(item, vocabApiBase, pointers, results, run);
                    }
                }

                url = page.has("next") ? page.get("next").asText() : null;
            } catch (VocabHarvestIncompleteException e) {
                throw e;
            } catch (Exception e) {
                if (isRetryableFailure(e)) {
                    run.recordFailure();
                }
                log.error("Failed to complete ARDC vocabulary listing page {}", pageUrl, e);
                throw incomplete(pageUrl, run, e);
            }
        }

        return results;
    }

    protected void processVocabLeafItem(
            JsonNode item,
            String vocabApiBase,
            Map<Name, String> pointers,
            Map<String, List<VocabModel>> results,
            HarvestRun run) {
        String vocabUri = about.apply(item);
        if (vocabUri == null || vocabUri.isEmpty()) {
            log.warn("Skipping ARDC vocabulary listing item without a resource URI");
            return;
        }

        String detailsUrl = String.format(vocabApiBase + pointers.get(Name.vocabDetailsApi), vocabUri);
        JsonNode target = run.detailTargets.get(vocabUri);
        VocabModel vocab = run.memo.get(vocabUri);

        if (target == null || vocab == null) {
            ObjectNode details;
            try {
                log.debug("getVocabLeafNodes -> {}", detailsUrl);
                details = retryTemplate.execute(
                        context -> restTemplate.getForObject(detailsUrl, ObjectNode.class));
                recordSuccess(run);
            } catch (Exception e) {
                if (isNotFound(e)) {
                    log.warn("ARDC vocabulary item not found at {}; skipping item", detailsUrl);
                    return;
                }
                if (isRetryableFailure(e)) {
                    int failures = run.recordFailure();
                    if (failures >= maxConsecutiveItemFailures) {
                        throw incomplete(detailsUrl, run, e);
                    }
                    log.warn("Transient failure fetching ARDC vocabulary item {} ({}/{} consecutive failures); skipping item",
                            detailsUrl, failures, maxConsecutiveItemFailures, e);
                    return;
                }
                if (e instanceof RestClientException) {
                    throw incomplete(detailsUrl, run, e);
                }
                log.warn("Invalid ARDC vocabulary item at {}; skipping item", detailsUrl, e);
                return;
            }

            if (!isNodeValid.apply(details, "result")
                    || !isNodeValid.apply(details.get("result"), "primaryTopic")) {
                log.warn("Invalid ARDC vocabulary payload at {}; skipping item", detailsUrl);
                return;
            }

            target = details.get("result").get("primaryTopic");
            run.detailTargets.put(vocabUri, target);
            try {
                vocab = buildVocabFromTarget(vocabUri, target, vocabApiBase, pointers, run);
            } catch (VocabHarvestIncompleteException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Invalid ARDC vocabulary payload at {}; skipping item", detailsUrl, e);
                return;
            }
            if (vocab == null) {
                return;
            }
        }

        if (isNodeValid.apply(target, "broadMatch")) {
            for (JsonNode broadMatch : target.get("broadMatch")) {
                results.computeIfAbsent(broadMatch.asText(), key -> new ArrayList<>()).add(vocab);
            }
        }

        if (!target.has("broadMatch") && isNodeValid.apply(target, "relatedMatch")) {
            // A headless vocab becomes a root and its narrower nodes move up one level.
            if (vocab.getNarrower() != null && !vocab.getNarrower().isEmpty()) {
                List<VocabModel> completedInternalNodes = new ArrayList<>();
                for (VocabModel currentInternalNode : vocab.getNarrower()) {
                    VocabModel completedInternalNode = buildVocabModel(
                            currentInternalNode, vocabApiBase, pointers, run);
                    if (completedInternalNode != null) {
                        completedInternalNodes.add(completedInternalNode);
                    }
                }
                vocab.setNarrower(completedInternalNodes);
            }
            results.computeIfAbsent("headlessNodes", key -> new ArrayList<>()).add(vocab);
        }
    }

    @Override
    public boolean isVersionEquals(ArdcCurrentPaths path, String version) {
        try {
            Map<Name, String> versioned = this.getVersionedArdcPath(path);
            return versioned.get(Name.version).equals(version);
        }
        catch(ExtractingPathVersionsException ex) {
            // If we fail to extract assume the cache have the same version
            // and continue startup
            log.warn("ARDC server not available, assume Elastic have the latest version");
            return true;
        }
    }

    @Override
    public List<VocabModel> getARDCVocabByType(ArdcCurrentPaths path) {
        HarvestRun run = new HarvestRun();
        Map<Name, String> versioned = this.getVersionedArdcPath(path, run);

        Map<String, List<VocabModel>> vocabLeafNodes = getVocabLeafNodes(vocabApiBase, versioned, run);
        String url = String.format(vocabApiBase + versioned.get(Name.categoryApi));
        List<VocabModel> vocabCategoryNodes = new ArrayList<>();
        while (url != null && !url.isEmpty()) {
            String pageUrl = url;
            try {
                ObjectNode r = retryTemplate.execute(context -> restTemplate.getForObject(pageUrl, ObjectNode.class));
                recordSuccess(run);
                if (r == null || r.isEmpty() || !isNodeValid.apply(r, "result")) {
                    throw new IllegalStateException("Invalid category listing payload");
                }
                JsonNode node = r.get("result");
                if (isNodeValid.apply(node, "items")) {
                    for (JsonNode j : node.get("items")) {
                            String labelValue = label.apply(j);
                            String definitionValue = definition.apply(j);
                            String aboutValue = about.apply(j);

                            if (aboutValue != null && !aboutValue.isEmpty() && labelValue != null && !labelValue.isEmpty()) {

                                log.debug("Processing label {}", labelValue);
                                VocabModel vocabCategoryNode = VocabModel.builder()
                                        .label(labelValue)
                                        .definition(definitionValue)
                                        .about(aboutValue)
                                        .version(versioned.get(Name.version))
                                        .build();

                                // process internal nodes of vocab category
                                Map<String, List<VocabModel>> internalVocabCategoryNodes = new HashMap<>();
                                if (j.has("narrower") && !j.get("narrower").isEmpty()) {
                                    j.get("narrower").forEach(currentNode -> {
                                        VocabModel internalNode = buildVocabModel(currentNode, vocabApiBase, versioned, run);
                                        if (internalNode != null) {
                                            List<VocabModel> leafNodes = vocabLeafNodes.getOrDefault(internalNode.getAbout(), Collections.emptyList());
                                            if (!leafNodes.isEmpty()) {
                                                internalNode.setNarrower(leafNodes);
                                            }
                                            // vocabCategoryNode.getAbout() as key because vocabCategoryNode is an upper level node of narrowerNode
                                            internalVocabCategoryNodes.computeIfAbsent(vocabCategoryNode.getAbout(), k -> new ArrayList<>()).add(internalNode);
                                        }
                                    });
                                }

                                // process root nodes of vocab category
                                if (!j.has("broader")) {
                                    List<VocabModel> leafNodes = vocabLeafNodes.getOrDefault(aboutValue, Collections.emptyList());
                                    List<VocabModel> internalNodes = internalVocabCategoryNodes.getOrDefault(aboutValue, Collections.emptyList());

                                    List<VocabModel> allNarrowerNodes = new ArrayList<>();
                                    if (!leafNodes.isEmpty()) {
                                        allNarrowerNodes.addAll(leafNodes);
                                    }
                                    if (!internalNodes.isEmpty()) {
                                        allNarrowerNodes.addAll(internalNodes);
                                    }
                                    if (!allNarrowerNodes.isEmpty()) {
                                        vocabCategoryNode.setNarrower(allNarrowerNodes);
                                    }

                                    // the final returning results will just be root nodes
                                    vocabCategoryNodes.add(vocabCategoryNode);
                                }
                            }
                    }
                }

                url = node.has("next") ? node.get("next").asText() : null;
            } catch (VocabHarvestIncompleteException e) {
                throw e;
            } catch (Exception e) {
                if (isRetryableFailure(e)) {
                    run.recordFailure();
                }
                log.error("Failed to complete ARDC category listing page {}", pageUrl, e);
                throw incomplete(pageUrl, run, e);
            }
        }

        List<VocabModel> headlessNodes = vocabLeafNodes.getOrDefault("headlessNodes", Collections.emptyList());
        if (!headlessNodes.isEmpty()) {
            vocabCategoryNodes.addAll(headlessNodes);
        }

        return vocabCategoryNodes;
    }
}
