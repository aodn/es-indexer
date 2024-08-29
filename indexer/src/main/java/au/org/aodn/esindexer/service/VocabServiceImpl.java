package au.org.aodn.esindexer.service;

import au.org.aodn.ardcvocabs.configuration.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabDto;
import au.org.aodn.ardcvocabs.model.VocabModel;
import au.org.aodn.esindexer.configuration.AppConstants;
import au.org.aodn.esindexer.exception.DocumentNotFoundException;
import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@Service
// create and inject a stub proxy to self due to the circular reference http://bit.ly/4aFvYtt
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class VocabServiceImpl implements VocabService {

    @Value(AppConstants.ARDC_VOCAB_API_BASE)
    protected String vocabApiBase;

    @Autowired
    protected RestTemplate indexerRestTemplate;

    @Autowired
    protected ElasticsearchClient portalElasticsearchClient;

    @Autowired
    ElasticSearchIndexService elasticSearchIndexService;

    @Value("${elasticsearch.vocabs_index.name}")
    String vocabsIndexName;

    @Autowired
    ObjectMapper indexerObjectMapper;

    // self-injection to avoid self-invocation problems when calling the cachable method within the same bean
    @Lazy
    @Autowired
    VocabService self;

    protected Function<JsonNode, String> label = (node) -> node.get("prefLabel").get("_value").asText();
    protected Function<JsonNode, String> about = (node) -> node.has("_about") ? node.get("_about").asText() : null;
    protected Function<JsonNode, String> definition = (node) -> node.has("definition") ? node.get("definition").asText() : null;
    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    protected VocabModel buildVocabByResourceUri(String vocabUri, String vocabApiBase, String resourceDetailsApi) {
        String detailsUrl = String.format(vocabApiBase + resourceDetailsApi, vocabUri);
        try {
            log.debug("Query api -> {}", detailsUrl);
            ObjectNode detailsObj = indexerRestTemplate.getForObject(detailsUrl, ObjectNode.class);
            if(isNodeValid.apply(detailsObj, "result") && isNodeValid.apply(detailsObj.get("result"), "primaryTopic")) {
                JsonNode target = detailsObj.get("result").get("primaryTopic");
                return VocabModel
                        .builder()
                        .label(label.apply(target).toLowerCase())
                        .definition(definition.apply(target))
                        .about(vocabUri)
                        .build();
            }
        } catch(Exception e) {
            log.error("Item not found in resource {}", detailsUrl);
        }
        return null;
    }

    protected VocabModel buildVocabModel(JsonNode currentNode, JsonNode outerNode) {
        if (currentNode instanceof ObjectNode objectNode) {
            if (objectNode.has("prefLabel") && objectNode.has("_about")) {
                return VocabModel.builder()
                        .about(about.apply(currentNode))
                        .label(label.apply(currentNode).toLowerCase())
                        .build();
            }
        } else if (currentNode instanceof TextNode textNode) {
            if (textNode.asText().contains("parameter_classes")) {
                return VocabModel.builder()
                        .about(textNode.asText())
                        .label(Objects.requireNonNull(findLabelByAbout(outerNode, textNode.asText())).toLowerCase())
                        .build();
            }
        }
        return null;
    }

    protected String findLabelByAbout(JsonNode node, String c) {
        for (JsonNode item : node.get("items")) {
            if (about.apply(item).contains(c)) {
                return label.apply(item);
            }
        }
        return null;
    }

    protected Map<String, List<VocabModel>> getVocabLeafNodes(String vocabApiBase, VocabApiPaths vocabApiPaths) {
        Map<String, List<VocabModel>> results = new HashMap<>();
        String url = String.format(vocabApiBase + vocabApiPaths.getVocabApiPath());
        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = indexerRestTemplate.getForObject(url, ObjectNode.class);
                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (isNodeValid.apply(node, "items")) {
                        for (JsonNode j : node.get("items")) {
                            // Now we need to construct link to detail resources
                            String dl = String.format(vocabApiBase + vocabApiPaths.getVocabDetailsApiPath(), about.apply(j));
                            try {
                                log.debug("Query api -> {}", dl);
                                ObjectNode d = indexerRestTemplate.getForObject(dl, ObjectNode.class);

                                if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                    JsonNode target = d.get("result").get("primaryTopic");

                                    VocabModel vocab = VocabModel
                                            .builder()
                                            .label(label.apply(target).toLowerCase())
                                            .definition(definition.apply(target))
                                            .about(about.apply(target))
                                            .build();

                                    if (vocabApiPaths.equals(VocabApiPaths.PLATFORM_VOCAB) || vocabApiPaths.equals(VocabApiPaths.ORGANISATION_VOCAB)) {
                                        List<VocabModel> vocabNarrower = new ArrayList<>();
                                        if(target.has("narrower") && !target.get("narrower").isEmpty()) {
                                            for(JsonNode narrower : target.get("narrower")) {
                                                if (narrower.has("_about")) {
                                                    VocabModel narrowerNode = buildVocabByResourceUri(about.apply(narrower), vocabApiBase, vocabApiPaths.getVocabDetailsApiPath());
                                                    if (narrowerNode != null) {
                                                        vocabNarrower.add(narrowerNode);
                                                    }
                                                }
                                            }
                                        }
                                        if (!vocabNarrower.isEmpty()) {
                                            vocab.setNarrower(vocabNarrower);
                                        }
                                    }

                                    if (target.has("broadMatch") && !target.get("broadMatch").isEmpty()) {
                                        for(JsonNode bm : target.get("broadMatch")) {
                                            results.computeIfAbsent(bm.asText(), k -> new ArrayList<>()).add(vocab);
                                        }
                                    }
                                }
                            }
                            catch(Exception e) {
                                log.error("Item not found in resource {}", dl);
                            }
                        }
                    }

                    if (!node.isEmpty() && node.has("next")) {
                        url = node.get("next").asText();
                    }
                    else {
                        url = null;
                    }
                }
                else {
                    url = null;
                }
            } catch (RestClientException e) {
                log.error("Fail connect {}, vocab return likely outdated", url);
                url = null;
            }
        }
        return results;
    }

    protected List<VocabModel> getVocabsByType(String vocabApiBase, VocabApiPaths vocabApiPaths) {
        Map<String, List<VocabModel>> leafNodes = getVocabLeafNodes(vocabApiBase, vocabApiPaths);
        String url = String.format(vocabApiBase + vocabApiPaths.getCategoryVocabApiPath());
        List<VocabModel> results = new ArrayList<>();
        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = indexerRestTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {
                            log.debug("Processing label {}", label.apply(j));

                            VocabModel vocab = VocabModel
                                    .builder()
                                    .label(label.apply(j).toLowerCase())
                                    .definition(definition.apply(j))
                                    .about(about.apply(j))
                                    .build();

                            List<VocabModel> broader = new ArrayList<>();
                            List<VocabModel> narrower = new ArrayList<>();

                            if (vocabApiPaths.equals(VocabApiPaths.PARAMETER_VOCAB)) {
                                // the logic remains the same with original implementation of processing parameter vocabularies
                                if (j.has("broader") && !j.get("broader").isEmpty()) {
                                    for (JsonNode b : j.get("broader")) {
                                        broader.add(buildVocabModel(b, node));
                                    }
                                }

                                if (j.has("narrower") && !j.get("narrower").isEmpty()) {
                                    for (JsonNode b : j.get("narrower")) {
                                        VocabModel c = buildVocabModel(b, node);
                                        if(c != null && leafNodes.containsKey(c.getAbout())) {
                                            c.setNarrower(leafNodes.get(c.getAbout()));
                                            narrower.add(c);
                                        }
                                    }
                                }
                            }

                            if (vocabApiPaths.equals(VocabApiPaths.PLATFORM_VOCAB)) {
                                if (leafNodes.containsKey(about.apply(j))) {
                                    narrower = leafNodes.get(about.apply(j));
                                }
                            }

                            if (vocabApiPaths.equals(VocabApiPaths.ORGANISATION_VOCAB)) {
                                if (j.has("narrower") && !j.get("narrower").isEmpty()) {
                                    for (JsonNode b : j.get("narrower")) {
                                        if (b.has("_about")) {
                                            VocabModel narrowerNode = buildVocabByResourceUri(about.apply(b), vocabApiBase, vocabApiPaths.getVocabDetailsApiPath());
                                            if (narrowerNode != null) {
                                                narrower.add(narrowerNode);
                                            }
                                        }
                                    }
                                } else {
                                    if (leafNodes.containsKey(about.apply(j))) {
                                        narrower = leafNodes.get(about.apply(j));
                                    }
                                }

                                // TODO: if vocab has narrower, e.g local government & vocab has broader e.g nsw councils
                                // what to do before adding them to results? e.g nsw councils shouldn't be added to root level of results list.
                            }

                            if (!broader.isEmpty()) {
                                vocab.setNarrower(broader);
                            }

                            if (!narrower.isEmpty()) {
                                vocab.setNarrower(narrower);
                            }

                            results.add(vocab);
                        }
                    }

                    if (!node.isEmpty() && node.has("next")) {
                        url = node.get("next").asText();
                    }
                    else {
                        url = null;
                    }
                }
                else {
                    url = null;
                }
            } catch (RestClientException e) {
                log.error("Fail connect {}, parameter vocab return likely outdated", url);
                url = null;
            }
        }
        return results;
    }

    protected boolean themesMatchConcept(List<ThemesModel> themes, ConceptModel thatConcept) {
        for (ThemesModel theme : themes) {
            for (ConceptModel thisConcept : theme.getConcepts()) {
                /*
                comparing by combined values (id and url) of the concept object
                this will prevent cases where bottom-level vocabs are the same in text, but their parent vocabs are different
                e.g "car -> parts" vs "bike -> parts" ("parts" is the same but different parent)
                 */
                if (thisConcept.equals(thatConcept)) {
                    /* thisConcept is the extracted from the themes of the record...theme.getConcepts()
                    thatConcept is the object created by iterating over the parameter_vocabs cache...ConceptModel thatConcept = ConceptModel.builder()
                    using overriding equals method to compare the two objects, this is not checking instanceof ConceptModel class
                     */
                    return true;
                }
            }
        }
        return false;
    }

    /*
    this method for analysing the vocabularies of a record aka bottom-level vocabs (found in the themes section)
    and returning the second-level vocabularies that match (1 level up from the bottom-level vocabularies)
     */
    public List<String> getVocabLabelsByThemes(List<ThemesModel> themes, String vocabType) throws IOException {
        List<String> results = new ArrayList<>();
        // Iterate over the top-level vocabularies
        List<JsonNode> vocabs = switch (vocabType) {
            case AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_KEY -> self.getParameterVocabsFromEs();
            case AppConstants.AODN_PLATFORM_VOCABS_KEY -> self.getPlatformVocabsFromEs();
            case AppConstants.AODN_ORGANISATION_VOCABS_KEY -> self.getOrganisationVocabsFromEs();
            default -> new ArrayList<>();
        };

        if (!vocabs.isEmpty()) {
            for (JsonNode topLevelVocab : vocabs) {
                if (topLevelVocab != null && topLevelVocab.has("narrower") && !topLevelVocab.get("narrower").isEmpty()) {
                    for (JsonNode secondLevelVocab : topLevelVocab.get("narrower")) {
                        if (secondLevelVocab != null && secondLevelVocab.has("label") && secondLevelVocab.has("narrower") && !secondLevelVocab.get("narrower").isEmpty()) {
                            String secondLevelVocabLabel = secondLevelVocab.get("label").asText();
                            for (JsonNode bottomLevelVocab : secondLevelVocab.get("narrower")) {
                                if (bottomLevelVocab != null && bottomLevelVocab.has("label") && bottomLevelVocab.has("about")) {
                                    // map the original values to a ConceptModel object for doing comparison
                                    ConceptModel bottomConcept = ConceptModel.builder()
                                            .id(bottomLevelVocab.get("label").asText())
                                            .url(bottomLevelVocab.get("about").asText())
                                            .build();

                                    // Compare with themes' concepts
                                    if (themesMatchConcept(themes, bottomConcept)) {
                                        results.add(secondLevelVocabLabel.toLowerCase());
                                        break; // To avoid duplicates because under the same second-level vocab there can be multiple bottom-level vocabs that pass the condition
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    public List<VocabModel> getParameterVocabsFromArdc(String vocabApiBase) {
        return getVocabsByType(vocabApiBase, VocabApiPaths.PARAMETER_VOCAB);
    }

    public List<VocabModel> getPlatformVocabsFromArdc(String vocabApiBase) {
        return getVocabsByType(vocabApiBase, VocabApiPaths.PLATFORM_VOCAB);
    }

    public List<VocabModel> getOrganisationVocabsFromArdc(String vocabApiBase) {
        return getVocabsByType(vocabApiBase, VocabApiPaths.ORGANISATION_VOCAB);
    }

    protected List<JsonNode> groupVocabsByKey(String key) throws IOException {
        List<JsonNode> vocabs = new ArrayList<>();
        log.info("Fetching {} vocabularies from {}", key, vocabsIndexName);
        try {
            long totalHits = elasticSearchIndexService.getDocumentsCount(vocabsIndexName);
            if (totalHits == 0) {
                throw new DocumentNotFoundException("No documents found in " + vocabsIndexName);
            } else {
                SearchResponse<JsonNode> response = portalElasticsearchClient.search(s -> s
                        .index(vocabsIndexName)
                        .size((int) totalHits), JsonNode.class
                );
                response.hits().hits().stream()
                        .map(Hit::source)
                        .map(hitSource -> hitSource != null ? hitSource.get(key) : null)
                        .filter(Objects::nonNull)
                        .forEach(vocabs::add);
            }
        } catch (ElasticsearchException | IOException e) {
            throw new IOException("Failed to get documents from " + vocabsIndexName + " | " + e.getMessage());
        }
        return vocabs;
    }

    @Cacheable(AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_KEY)
    public List<JsonNode> getParameterVocabsFromEs() throws IOException {
        return groupVocabsByKey("parameter_vocab");
    }

    @Cacheable(AppConstants.AODN_PLATFORM_VOCABS_KEY)
    public List<JsonNode> getPlatformVocabsFromEs() throws IOException {
        return groupVocabsByKey("platform_vocab");
    }

    @Cacheable(AppConstants.AODN_ORGANISATION_VOCABS_KEY)
    public List<JsonNode> getOrganisationVocabsFromEs() throws IOException {
        return groupVocabsByKey("organisation_vocab");
    }

    @CacheEvict(value = AppConstants.AODN_DISCOVERY_PARAMETER_VOCABS_KEY, allEntries = true)
    public void clearParameterVocabsCache() {
        // Intentionally empty; the annotation does the job
    }

    @CacheEvict(value = AppConstants.AODN_PLATFORM_VOCABS_KEY, allEntries = true)
    public void clearPlatformVocabsCache() {
        // Intentionally empty; the annotation does the job
    }

    @CacheEvict(value = AppConstants.AODN_ORGANISATION_VOCABS_KEY, allEntries = true)
    public void clearOrganisationVocabsCache() {
        // Intentionally empty; the annotation does the job
    }

    protected void indexAllVocabs(List<VocabModel> parameterVocabs,
                                List<VocabModel> platformVocabs,
                                List<VocabModel> organisationVocabs) throws IOException {

        List<VocabDto> vocabDtos = new ArrayList<>();

        // parameter vocabs
        for (VocabModel parameterVocab : parameterVocabs) {
            VocabDto vocabDto = VocabDto.builder().parameterVocabModel(parameterVocab).build();
            vocabDtos.add(vocabDto);
        }

        // platform vocabs
        for (VocabModel platformVocab : platformVocabs) {
            VocabDto vocabDto = VocabDto.builder().platformVocabModel(platformVocab).build();
            vocabDtos.add(vocabDto);
        }

        // organisation vocabs
        for (VocabModel organisationVocab : organisationVocabs) {
            VocabDto vocabDto = VocabDto.builder().organisationVocabModel(organisationVocab).build();
            vocabDtos.add(vocabDto);
        }

        // recreate index from mapping JSON file
        elasticSearchIndexService.createIndexFromMappingJSONFile(AppConstants.VOCABS_INDEX_MAPPING_SCHEMA_FILE, vocabsIndexName);
        log.info("Indexing all vocabs to {}", vocabsIndexName);

        bulkIndexVocabs(vocabDtos);
    }

    protected void bulkIndexVocabs(List<VocabDto> vocabs) throws IOException {
        // count portal index documents, or create index if not found from defined mapping JSON file
        BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

        for (VocabDto vocab : vocabs) {
            try {
                // convert vocab values to binary data
                log.debug("Ingested json is {}", indexerObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vocab));
                // send bulk request to Elasticsearch
                bulkRequest.operations(op -> op
                        .index(idx -> idx
                                .index(vocabsIndexName)
                                .document(vocab)
                        )
                );
            } catch (JsonProcessingException e) {
                log.error("Failed to ingest parameterVocabs to {}", vocabsIndexName);
                throw new RuntimeException(e);
            }
        }

        BulkResponse result = portalElasticsearchClient.bulk(bulkRequest.build());

        // Flush after insert, otherwise you need to wait for next auto-refresh. It is
        // especially a problem with autotest, where assert happens very fast.
        portalElasticsearchClient.indices().refresh();

        // Log errors, if any
        if (result.errors()) {
            log.error("Bulk had errors");
            for (BulkResponseItem item: result.items()) {
                if (item.error() != null) {
                    log.error("{} {}", item.error().reason(), item.error().causedBy());
                }
            }
        } else {
            log.info("Finished bulk indexing items to index: {}", vocabsIndexName);
        }
        log.info("Total documents in index: {} is {}", vocabsIndexName, elasticSearchIndexService.getDocumentsCount(vocabsIndexName));
    }

    public void populateVocabsData() throws IOException {
        log.info("Fetching parameter vocabs from ARDC");
        List<VocabModel> parameterVocabs = getParameterVocabsFromArdc(vocabApiBase);
//        List<VocabModel> parameterVocabs = new ArrayList<>();
        log.info("Fetching platform vocabs from ARDC");
        List<VocabModel> platformVocabs = getPlatformVocabsFromArdc(vocabApiBase);
//        List<VocabModel> platformVocabs = new ArrayList<>();
        log.info("Fetching organisation vocabs from ARDC");
        List<VocabModel> organisationVocabs = getOrganisationVocabsFromArdc(vocabApiBase);
//        List<VocabModel> organisationVocabs = new ArrayList<>();
        log.info("Indexing fetched vocabs to {}", vocabsIndexName);
        indexAllVocabs(parameterVocabs, platformVocabs, organisationVocabs);
    }
}
