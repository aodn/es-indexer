package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.model.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ArdcVocabServiceImpl implements ArdcVocabService {

    protected Logger log = LoggerFactory.getLogger(ArdcVocabServiceImpl.class);

    @Value("${ardcvocabs.baseUrl:https://vocabs.ardc.edu.au/repository/api/lda/aodn}")
    protected String vocabApiBase;

    protected RestTemplate restTemplate;

    protected Function<JsonNode, String> label = (node) -> node.has("prefLabel") ? node.get("prefLabel").get("_value").asText() : null;
    protected Function<JsonNode, String> about = (node) -> node.has("_about") ? node.get("_about").asText() : null;
    protected Function<JsonNode, String> definition = (node) -> node.has("definition") ? node.get("definition").asText() : null;
    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    public ArdcVocabServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    protected VocabModel buildVocabByResourceUri(String vocabUri, String vocabApiBase, VocabApiPaths vocabApiPaths) {
        String resourceDetailsApi = vocabUri.contains("_classes")
                ? vocabApiPaths.getVocabCategoryDetailsApiPath()
                : vocabApiPaths.getVocabDetailsApiPath();

        String detailsUrl = String.format(vocabApiBase + resourceDetailsApi, vocabUri);

        try {
            log.debug("Query api -> {}", detailsUrl);
            ObjectNode detailsObj = restTemplate.getForObject(detailsUrl, ObjectNode.class);
            if(isNodeValid.apply(detailsObj, "result") && isNodeValid.apply(detailsObj.get("result"), "primaryTopic")) {
                JsonNode target = detailsObj.get("result").get("primaryTopic");

                VocabModel vocab = VocabModel
                        .builder()
                        .label(label.apply(target))
                        .definition(definition.apply(target))
                        .about(vocabUri)
                        .build();

                List<VocabModel> narrowerNodes = new ArrayList<>();
                if (isNodeValid.apply(target, "narrower")) {
                    for (JsonNode j : target.get("narrower")) {
                        if (!about.apply(j).isEmpty()) {
                            // recursive call
                            VocabModel narrowerNode = buildVocabByResourceUri(about.apply(j), vocabApiBase, vocabApiPaths);
                            if (narrowerNode != null) {
                                narrowerNodes.add(narrowerNode);
                            }
                        }
                    }
                }

                if (!narrowerNodes.isEmpty()) {
                    vocab.setNarrower(narrowerNodes);
                }

                return vocab;
            }
        } catch(Exception e) {
            log.error("Item not found in resource {}", detailsUrl);
        }
        return null;
    }

    protected <T> VocabModel buildVocabModel(T currentNode, String vocabApiBase, VocabApiPaths vocabApiPaths) {
        String resourceUri = null;

        if (currentNode instanceof ObjectNode objectNode) {
            resourceUri = objectNode.has("_about") ? about.apply(objectNode) : objectNode.asText();
        } else if (currentNode instanceof TextNode textNode) {
            resourceUri = textNode.asText();
        } else if (currentNode instanceof VocabModel vocabNode) {
            String about = vocabNode.getAbout();
            if (about != null && !about.isEmpty()) {
                resourceUri = about;
            }
        }

        if (resourceUri == null) {
            throw new IllegalArgumentException("Unsupported node type: " + currentNode.getClass().getName());
        }

        return buildVocabByResourceUri(resourceUri, vocabApiBase, vocabApiPaths);
    }

    protected Map<String, List<VocabModel>> getVocabLeafNodes(String vocabApiBase, VocabApiPaths vocabApiPaths) {
        Map<String, List<VocabModel>> results = new HashMap<>();
        String url = String.format(vocabApiBase + vocabApiPaths.getVocabApiPath());

        while (url != null && !url.isEmpty()) {
            try {
                log.debug("getVocabLeafNodes -> {}", url);
                ObjectNode r = restTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (isNodeValid.apply(node, "items")) {
                        for (JsonNode j : node.get("items")) {
                            // Now we need to construct link to detail resources
                            String dl = String.format(vocabApiBase + vocabApiPaths.getVocabDetailsApiPath(), about.apply(j));
                            try {
                                log.debug("getVocabLeafNodes -> {}", dl);
                                ObjectNode d = restTemplate.getForObject(dl, ObjectNode.class);

                                if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                    JsonNode target = d.get("result").get("primaryTopic");

                                    VocabModel vocab = VocabModel
                                            .builder()
                                            .label(label.apply(target))
                                            .definition(definition.apply(target))
                                            .about(about.apply(target))
                                            .build();

                                    List<VocabModel> vocabNarrower = new ArrayList<>();
                                    if(target.has("narrower") && !target.get("narrower").isEmpty()) {
                                        for(JsonNode currentNode : target.get("narrower")) {
                                            VocabModel narrowerNode = buildVocabModel(currentNode, vocabApiBase, vocabApiPaths);
                                            if (narrowerNode != null) {
                                                vocabNarrower.add(narrowerNode);
                                            }
                                        }
                                    }
                                    if (!vocabNarrower.isEmpty()) {
                                        vocab.setNarrower(vocabNarrower);
                                    }

                                    if (target.has("broadMatch") && !target.get("broadMatch").isEmpty()) {
                                        for(JsonNode bm : target.get("broadMatch")) {
                                            results.computeIfAbsent(bm.asText(), k -> new ArrayList<>()).add(vocab);
                                        }
                                    }

                                    if (!target.has("broadMatch") && target.has("relatedMatch") && !target.get("relatedMatch").isEmpty()) {
                                        // when the conditions above are true, a vocab doesn't have root node (top-level), it is headless, and it becomes a head node (root node)
                                        // sample: http://vocab.aodn.org.au/def/organisation/entity/133
                                        // each of the vocab's narrower nodes (leaf nodes) now becomes currentInternalNode (second-level)
                                        // they are all, basically jump 1 level up in the tree structure.
                                        if (vocab.getNarrower() != null && !vocab.getNarrower().isEmpty()) {
                                            List<VocabModel> completedInternalNodes = new ArrayList<>();
                                            vocab.getNarrower().forEach(currentInternalNode -> {
                                                // rebuild currentInternalNode (no linked leaf nodes) to completedInternalNode (with linked leaf nodes)
                                                VocabModel completedInternalNode = buildVocabModel(currentInternalNode, vocabApiBase, vocabApiPaths);
                                                if (completedInternalNode != null) {
                                                    // each internal node now will have linked narrower nodes (if available)
                                                    completedInternalNodes.add(completedInternalNode);
                                                }
                                            });
                                            // update the vocab with completed internal nodes ad their associating leaf nodes.
                                            vocab.setNarrower(completedInternalNodes);
                                        }
                                        results.computeIfAbsent("headlessNodes", k -> new ArrayList<>()).add(vocab);
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

    @Override
    public List<VocabModel> getVocabTreeFromArdcByType(VocabApiPaths vocabApiPaths) {
        Map<String, List<VocabModel>> vocabLeafNodes = getVocabLeafNodes(vocabApiBase, vocabApiPaths);
        String url = String.format(vocabApiBase + vocabApiPaths.getVocabCategoryApiPath());
        List<VocabModel> vocabCategoryNodes = new ArrayList<>();
        while (url != null && !url.isEmpty()) {
            log.debug("Query api -> {}", url);
            try {
                ObjectNode r = restTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");
                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
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
                                        .build();

                                // process internal nodes of vocab category
                                Map<String, List<VocabModel>> internalVocabCategoryNodes = new HashMap<>();
                                if (j.has("narrower") && !j.get("narrower").isEmpty()) {
                                    j.get("narrower").forEach(currentNode -> {
                                        VocabModel internalNode = buildVocabModel(currentNode, vocabApiBase, vocabApiPaths);
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

                    if (!node.isEmpty() && node.has("next")) {
                        url = node.get("next").asText();
                    } else {
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

        List<VocabModel> headlessNodes = vocabLeafNodes.getOrDefault("headlessNodes", Collections.emptyList());
        if (!headlessNodes.isEmpty()) {
            vocabCategoryNodes.addAll(headlessNodes);
        }

        return vocabCategoryNodes;
    }
}
