package cn.popcraft.villagerpro.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BuildingRuleEvaluator {
    private BuildingRuleEvaluator() {
    }

    public static int calculateLevel(Map<String, Integer> metrics,
                                     Map<Integer, Map<String, Integer>> requirements,
                                     int maxLevel) {
        for (int level = maxLevel; level >= 1; level--) {
            Map<String, Integer> levelRequirements = requirements.get(level);
            if (levelRequirements != null && meets(metrics, levelRequirements)) {
                return level;
            }
        }
        return 0;
    }

    public static Map<String, Integer> missingRequirements(Map<String, Integer> metrics,
                                                            Map<String, Integer> requirements) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> requirement : requirements.entrySet()) {
            int actual = metrics.getOrDefault(requirement.getKey(), 0);
            if (actual < requirement.getValue()) {
                missing.put(requirement.getKey(), requirement.getValue() - actual);
            }
        }
        return missing;
    }

    private static boolean meets(Map<String, Integer> metrics, Map<String, Integer> requirements) {
        return requirements.entrySet().stream()
                .allMatch(entry -> metrics.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }
}
