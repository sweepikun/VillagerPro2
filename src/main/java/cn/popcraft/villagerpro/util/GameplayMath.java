package cn.popcraft.villagerpro.util;

public final class GameplayMath {
    private GameplayMath() {
    }

    public static int productionAmount(int baseAmount, int villagerLevel, int skillBonus) {
        return productionAmount(baseAmount, villagerLevel, 1, skillBonus);
    }

    public static int productionAmount(int baseAmount, int villagerLevel,
                                       int amountPerExtraLevel, int skillBonus) {
        long extraLevels = Math.max(0L, (long) villagerLevel - 1L);
        long amount = Math.max(0L, (long) baseAmount)
                + extraLevels * Math.max(0L, (long) amountPerExtraLevel)
                + Math.max(0L, (long) skillBonus);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, amount));
    }

    public static int applyPercentageBoost(int amount, double bonusPerLevel, int level) {
        return applyMultiplier(amount, 1.0 + bonusPerLevel * Math.max(0, level));
    }

    public static int applyMultiplier(int amount, double multiplier) {
        return Math.max(1, (int) Math.ceil(amount * Math.max(0.0, multiplier)));
    }

    public static double configuredPercentage(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        double normalized = value > 1.0 ? value / 100.0 : value;
        return Double.isFinite(normalized) ? Math.min(1.0, normalized) : 0.0;
    }

    public static double clampProbability(double probability) {
        if (!Double.isFinite(probability)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, probability));
    }

    public static long workIntervalMillis(long baseIntervalTicks,
                                          double skillReductionPerLevel, int skillLevel,
                                          double villageReductionPerLevel, int villageLevel) {
        double multiplier = 1.0
                - skillReductionPerLevel * Math.max(0, skillLevel)
                - villageReductionPerLevel * Math.max(0, villageLevel);
        return workIntervalMillis(baseIntervalTicks, 1.0 - multiplier);
    }

    public static long workIntervalMillis(long baseIntervalTicks, double totalReduction) {
        return Math.max(1000L, (long) (baseIntervalTicks * 50.0
                * Math.max(0.1, 1.0 - Math.max(0.0, totalReduction))));
    }

    public static int storableAmount(int requested, int capacity, int currentStorage) {
        return Math.min(Math.max(0, requested), Math.max(0, capacity - currentStorage));
    }

    public static double damageAfterReduction(double damage, double reduction) {
        if (!Double.isFinite(damage) || damage <= 0) return 0;
        double safeReduction = Double.isFinite(reduction)
                ? Math.max(0, Math.min(1, reduction)) : 0;
        return damage * (1 - safeReduction);
    }

    public static boolean isShelterTime(long worldTime, int triggerWorldTime) {
        long normalizedTime = Math.floorMod(worldTime, 24_000L);
        int normalizedTrigger = Math.floorMod(triggerWorldTime, 24_000);
        return normalizedTime >= normalizedTrigger;
    }

    public static int completeRecipes(int inputAmount, int inputPerRecipe) {
        return Math.max(0, inputAmount) / Math.max(1, inputPerRecipe);
    }

    public static int consumedRecipeInput(int successfulRecipes, int inputPerRecipe) {
        return Math.max(0, successfulRecipes) * Math.max(1, inputPerRecipe);
    }

    public static int extractableAmount(int storedAmount, int reserveAmount) {
        return Math.max(0, storedAmount - Math.max(0, reserveAmount));
    }

    public static int effectiveReserveAmount(int storedAmount, int configuredReserve,
                                             double frozenFraction) {
        int policyReserve = (int) Math.ceil(Math.max(0, storedAmount)
                * Math.max(0, Math.min(1, frozenFraction)));
        return Math.max(Math.max(0, configuredReserve), policyReserve);
    }

    public static double mitigatedMultiplier(double harmfulMultiplier, int buildingLevel,
                                             double mitigationPerLevel) {
        double mitigation = Math.max(0, Math.min(0.90,
                Math.max(0, buildingLevel) * Math.max(0, mitigationPerLevel)));
        return 1.0 + (harmfulMultiplier - 1.0) * (1.0 - mitigation);
    }

    public static int caravanReturnAmount(int cargoAmount, double cargoUnitValue,
                                          double returnUnitValue, double valueMultiplier) {
        if (cargoAmount <= 0 || cargoUnitValue <= 0 || returnUnitValue <= 0 || valueMultiplier <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(
                cargoAmount * cargoUnitValue * valueMultiplier / returnUnitValue));
    }

    public static double caravanRisk(double baseRisk, int villageLevel, double reductionPerLevel) {
        double safeBaseRisk = clampProbability(baseRisk);
        double safeReduction = Double.isFinite(reductionPerLevel)
                ? Math.max(0, reductionPerLevel) : 0;
        double reduced = safeBaseRisk - Math.max(0, villageLevel - 1) * safeReduction;
        return Math.min(0.95, clampProbability(reduced));
    }

    public static int scaleByVillageLevel(int baseValue, int perLevel, int villageLevel) {
        return Math.max(0, baseValue + Math.max(0, villageLevel - 1) * perLevel);
    }

    public static int applyExpectedMultiplier(int amount, double multiplier, double randomUnit) {
        double safeMultiplier = Double.isFinite(multiplier) ? Math.max(0, multiplier) : 0;
        double exact = Math.max(0, amount) * safeMultiplier;
        if (exact >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int result = (int) Math.floor(exact);
        if (Math.max(0, Math.min(1, randomUnit)) < exact - result) result++;
        return Math.max(1, result);
    }

    public static double needsMultiplier(double lowestNeed,
                                         double excellentThreshold, double excellentBonus,
                                         double poorThreshold, double poorPenalty,
                                         double criticalThreshold, double criticalPenalty) {
        if (lowestNeed >= excellentThreshold) return 1.0 + excellentBonus;
        if (lowestNeed < criticalThreshold) return Math.max(0.1, 1.0 - criticalPenalty);
        if (lowestNeed < poorThreshold) return Math.max(0.1, 1.0 - poorPenalty);
        return 1.0;
    }

    public static double personalityProductionMultiplier(int loyalty, int mood,
                                                         int loyaltyThreshold, double loyaltyBonus,
                                                         int moodThreshold, double moodBonus) {
        double multiplier = 1.0;
        if (Math.max(0, loyalty) >= Math.max(0, loyaltyThreshold)) {
            multiplier += finiteNonNegative(loyaltyBonus);
        }
        if (Math.max(0, mood) >= Math.max(0, moodThreshold)) {
            multiplier += finiteNonNegative(moodBonus);
        }
        return multiplier;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    public static double levelMultiplier(int level, double bonusPerExtraLevel) {
        return 1.0 + Math.max(0, level - 1) * Math.max(0, bonusPerExtraLevel);
    }

    public static double chainEfficiency(double baseEfficiency, double additiveBonus,
                                         double needsMultiplier) {
        return Math.max(0, Math.min(1,
                (baseEfficiency + Math.max(0, additiveBonus)) * Math.max(0, needsMultiplier)));
    }

    public static long nonExtendingExpiry(long existingExpiry, long now, long durationMillis) {
        if (existingExpiry > now) return existingExpiry;
        return now + Math.max(0, durationMillis);
    }

    public static LevelProgress applyExperience(int currentLevel, int currentExperience,
                                                int addedExperience, int baseExperience,
                                                int maxLevel) {
        int level = Math.max(1, currentLevel);
        long experience = Math.max(0L, currentExperience) + Math.max(0L, addedExperience);
        int safeBase = Math.max(1, baseExperience);
        int safeMax = Math.max(level, maxLevel);
        while (level < safeMax) {
            long required = (long) level * safeBase;
            if (experience < required) break;
            experience -= required;
            level++;
        }
        return new LevelProgress(level, (int) Math.min(Integer.MAX_VALUE, experience));
    }

    public record LevelProgress(int level, int experience) {
    }
}
