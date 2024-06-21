package au.org.aodn.researchvocabs.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.BiFunction;

public class ResearchVocabsUtils {
    public static BiFunction<JsonNode, String, Boolean> isNodeValid = (node, item) -> node != null && !node.isEmpty() && node.has(item) && !node.get(item).isEmpty();
}
