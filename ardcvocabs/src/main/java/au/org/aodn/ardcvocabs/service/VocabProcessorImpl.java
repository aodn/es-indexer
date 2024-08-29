package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.configuration.VocabApiPaths;
import au.org.aodn.ardcvocabs.model.VocabModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
public class VocabProcessorImpl implements VocabProcessor {
    protected RestTemplate vocabRestTemplate;
    @Autowired
    public final void setVocabRestTemplate(RestTemplate vocabRestTemplate) {
        this.vocabRestTemplate = vocabRestTemplate;
    }

    protected Function<JsonNode, String> label = (node) -> node.get("prefLabel").get("_value").asText();
    protected Function<JsonNode, String> about = (node) -> node.has("_about") ? node.get("_about").asText() : null;
    protected Function<JsonNode, String> definition = (node) -> node.has("definition") ? node.get("definition").asText() : null;
    protected BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();

    protected VocabModel buildVocabByResourceUri(String vocabUri, String vocabApiBase, String resourceDetailsApi) {
        String detailsUrl = String.format(vocabApiBase + resourceDetailsApi, vocabUri);
        try {
            log.debug("Query api -> {}", detailsUrl);
            ObjectNode detailsObj = vocabRestTemplate.getForObject(detailsUrl, ObjectNode.class);
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
                ObjectNode r = vocabRestTemplate.getForObject(url, ObjectNode.class);
                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (isNodeValid.apply(node, "items")) {
                        for (JsonNode j : node.get("items")) {
                            // Now we need to construct link to detail resources
                            String dl = String.format(vocabApiBase + vocabApiPaths.getVocabDetailsApiPath(), about.apply(j));
                            try {
                                log.debug("Query api -> {}", dl);
                                ObjectNode d = vocabRestTemplate.getForObject(dl, ObjectNode.class);

                                if(isNodeValid.apply(d, "result") && isNodeValid.apply(d.get("result"), "primaryTopic")) {
                                    JsonNode target = d.get("result").get("primaryTopic");

                                    VocabModel vocab = VocabModel
                                            .builder()
                                            .label(label.apply(target).toLowerCase())
                                            .definition(definition.apply(target))
                                            .about(about.apply(target))
                                            .build();

                                    if (vocabApiPaths.equals(VocabApiPaths.PLATFORM_VOCAB) ||
                                            vocabApiPaths.equals(VocabApiPaths.ORGANISATION_VOCAB)) {
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
                ObjectNode r = vocabRestTemplate.getForObject(url, ObjectNode.class);

                if (r != null && !r.isEmpty()) {
                    JsonNode node = r.get("result");

                    if (!node.isEmpty() && node.has("items") && !node.get("items").isEmpty()) {
                        for (JsonNode j : node.get("items")) {
                            log.debug("Processing label {}", label.apply(j));

                            List<VocabModel> broader = new ArrayList<>();
                            List<VocabModel> narrower = new ArrayList<>();

                            if (vocabApiPaths.equals(VocabApiPaths.PARAMETER_VOCAB)) {
                                if (j.has("broader")) {
                                    for (JsonNode b : j.get("broader")) {
                                        broader.add(buildVocabModel(b, node));
                                    }
                                }

                                if (j.has("narrower")) {
                                    for (JsonNode b : j.get("narrower")) {
                                        VocabModel c = buildVocabModel(b, node);
                                        if(c != null && leafNodes.containsKey(c.getAbout())) {
                                            c.setNarrower(leafNodes.get(c.getAbout()));
                                            narrower.add(c);
                                        }
                                    }
                                }
                            } else {
                                if (leafNodes.containsKey(about.apply(j))) {
                                    narrower = leafNodes.get(about.apply(j));
                                }
                            }

                            VocabModel vocab = VocabModel
                                    .builder()
                                    .label(label.apply(j).toLowerCase())
                                    .definition(definition.apply(j))
                                    .about(about.apply(j))
                                    .build();

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

    public List<VocabModel> getParameterVocabs(String vocabApiBase) {
        return getVocabsByType(vocabApiBase, VocabApiPaths.PARAMETER_VOCAB);
    }

    public List<VocabModel> getPlatformVocabs(String vocabApiBase) {
        return getVocabsByType(vocabApiBase, VocabApiPaths.PLATFORM_VOCAB);
    }

    public List<VocabModel> getOrganisationVocabs(String vocabApiBase) {
        return getVocabsByType(vocabApiBase, VocabApiPaths.ORGANISATION_VOCAB);
    }
}
