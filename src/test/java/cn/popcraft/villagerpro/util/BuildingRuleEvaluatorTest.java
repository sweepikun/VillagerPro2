package cn.popcraft.villagerpro.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingRuleEvaluatorTest {
    @Test
    void selectsHighestFullySatisfiedBuildingLevel() {
        Map<Integer, Map<String, Integer>> requirements = new LinkedHashMap<>();
        requirements.put(1, Map.of("barrels", 4, "planks", 16, "roof_columns", 6));
        requirements.put(2, Map.of("barrels", 8, "planks", 32, "roof_columns", 8));
        requirements.put(3, Map.of("barrels", 12, "planks", 48, "roof_columns", 9));

        assertEquals(0, BuildingRuleEvaluator.calculateLevel(
                Map.of("barrels", 3, "planks", 64, "roof_columns", 9), requirements, 3));
        assertEquals(1, BuildingRuleEvaluator.calculateLevel(
                Map.of("barrels", 7, "planks", 32, "roof_columns", 8), requirements, 3));
        assertEquals(2, BuildingRuleEvaluator.calculateLevel(
                Map.of("barrels", 12, "planks", 47, "roof_columns", 9), requirements, 3));
        assertEquals(3, BuildingRuleEvaluator.calculateLevel(
                Map.of("barrels", 12, "planks", 48, "roof_columns", 9), requirements, 3));
    }

    @Test
    void reportsOnlyMissingAmounts() {
        Map<String, Integer> missing = BuildingRuleEvaluator.missingRequirements(
                Map.of("beds", 1, "brewing_stands", 2, "roof_columns", 4),
                Map.of("beds", 2, "brewing_stands", 1, "bookshelves", 4, "roof_columns", 6));

        assertEquals(Map.of("beds", 1, "bookshelves", 4, "roof_columns", 2), missing);
    }
}
