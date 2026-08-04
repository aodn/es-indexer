package au.org.aodn.ardcvocabs.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The concept_semantic field is built per narrower (level-2) concept, not from the top-level
 * (level-1) category, so that each level-2 concept gets its own semantic representation.
 */
public class VocabDtoTest {

    protected ObjectMapper mapper = new ObjectMapper();

    protected VocabModel leaf(String label) {
        return VocabModel.builder().label(label).build();
    }

    /**
     * Chemical (level 1)
     *   |- Nutrient (level 2, full label set)
     *   |    |- Nitrate (leaf, itself has a level-4 child that must be ignored)
     *   |    |- Phosphate (leaf)
     *   |- Carbon (level 2, label + definition only)
     *        |- DIC (leaf)
     */
    protected VocabModel buildTopLevelVocab() {
        VocabModel nitrate = VocabModel.builder()
                .label("Nitrate")
                .narrower(List.of(leaf("Nitrate as NOx")))
                .build();

        VocabModel nutrient = VocabModel.builder()
                .label("Nutrient")
                .displayLabel("Nutrient (chemical)")
                .altLabels(List.of("Nutrients"))
                .hiddenLabels(List.of("nutrint"))
                .definition("Dissolved inorganic nutrients in seawater")
                .narrower(List.of(nitrate, leaf("Phosphate")))
                .build();

        VocabModel carbon = VocabModel.builder()
                .label("Carbon")
                .definition("Carbon species in seawater")
                .narrower(List.of(leaf("DIC")))
                .build();

        return VocabModel.builder()
                .label("Chemical")
                .definition("Chemical category")
                .narrower(List.of(nutrient, carbon))
                .build();
    }

    @Test
    public void verifyConceptSemanticIsBuiltPerNarrowerConcept() {
        VocabDto dto = VocabDto.builder().parameterVocabModel(buildTopLevelVocab()).build();

        assertEquals(
                List.of(
                        "Nutrient. Nutrient (chemical). Nutrients. nutrint. Dissolved inorganic nutrients in seawater. Nitrate. Phosphate",
                        "Carbon. Carbon species in seawater. DIC"
                ),
                dto.getConceptSemantic(),
                "One entry per level-2 concept, each with its own labels, definition and leaf labels"
        );
    }

    @Test
    public void verifyTopLevelLabelIsNotIncluded() {
        VocabDto dto = VocabDto.builder().parameterVocabModel(buildTopLevelVocab()).build();

        dto.getConceptSemantic().forEach(conceptText ->
                assertFalse(conceptText.contains("Chemical"),
                        "Level-1 category text must not leak into the level-2 entries: " + conceptText));
    }

    @Test
    public void verifyOnlyDirectLeafLabelsAreIncluded() {
        VocabDto dto = VocabDto.builder().parameterVocabModel(buildTopLevelVocab()).build();

        assertFalse(dto.getConceptSemantic().get(0).contains("Nitrate as NOx"),
                "Descendants below the leaf level must not be included");
    }

    @Test
    public void verifyFlatVocabFallsBackToItsOwnConceptText() {
        VocabModel flatVocab = VocabModel.builder()
                .label("Australian Institute of Marine Science")
                .altLabels(List.of("AIMS"))
                .definition("A marine research agency")
                .build();

        VocabDto dto = VocabDto.builder().organisationVocabModel(flatVocab).build();

        assertEquals(
                List.of("Australian Institute of Marine Science. AIMS. A marine research agency"),
                dto.getConceptSemantic(),
                "A vocab with no narrower concepts must still be semantically searchable"
        );
    }

    @Test
    public void verifyNarrowerConceptsWithoutAnyTextAreSkipped() {
        VocabModel topLevel = VocabModel.builder()
                .label("Platform")
                .narrower(List.of(VocabModel.builder().build(), leaf("Mooring")))
                .build();

        VocabDto dto = VocabDto.builder().platformVocabModel(topLevel).build();

        assertEquals(List.of("Mooring"), dto.getConceptSemantic());
    }

    @Test
    public void verifyConceptSemanticIsNullWhenNoVocabModelIsSet() {
        assertNull(VocabDto.builder().build().getConceptSemantic());
    }

    @Test
    public void verifyConceptSemanticIsSerialisedAsAnArray() {
        VocabDto dto = VocabDto.builder().parameterVocabModel(buildTopLevelVocab()).build();

        JsonNode node = mapper.valueToTree(dto).get("concept_semantic");

        assertNotNull(node, "concept_semantic must be serialised into the indexed doc");
        assertTrue(node.isArray(), "concept_semantic must be an array so ES embeds each level-2 concept separately");
        assertEquals(2, node.size());
    }

    @Test
    public void verifyConceptSemanticIsOmittedWhenNull() {
        assertNull(mapper.valueToTree(VocabDto.builder().build()).get("concept_semantic"),
                "NON_NULL keeps an empty concept_semantic out of the indexed doc");
    }
}
