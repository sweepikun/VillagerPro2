package cn.popcraft.villagerpro.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayMathTest {
    @Test
    void villagerLevelAndProductionSkillIncreaseOutput() {
        int base = GameplayMath.productionAmount(3, 1, 0);
        int upgraded = GameplayMath.productionAmount(3, 3, 2);

        assertEquals(3, base);
        assertEquals(7, upgraded);
        assertTrue(upgraded > base);
    }

    @Test
    void configuredVillagerLevelBonusChangesActualOutput() {
        assertEquals(3, GameplayMath.productionAmount(3, 4, 0, 0));
        assertEquals(9, GameplayMath.productionAmount(3, 4, 2, 0));
    }

    @Test
    void extremeProductionBonusesSaturateInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, GameplayMath.productionAmount(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, GameplayMath.applyExpectedMultiplier(
                Integer.MAX_VALUE, Double.MAX_VALUE, 0));
        assertEquals(1, GameplayMath.applyExpectedMultiplier(10, Double.NaN, 0));
    }

    @Test
    void farmingBoostChangesActualOutput() {
        assertEquals(10, GameplayMath.applyPercentageBoost(10, 0.2, 0));
        assertEquals(12, GameplayMath.applyPercentageBoost(10, 0.2, 1));
        assertEquals(16, GameplayMath.applyPercentageBoost(10, 0.2, 3));
        assertEquals(4, GameplayMath.applyPercentageBoost(3, 0.2, 1));
    }

    @Test
    void workstationPercentageAcceptsWholeOrDecimalConfiguration() {
        assertEquals(0.2, GameplayMath.configuredPercentage(20));
        assertEquals(0.2, GameplayMath.configuredPercentage(0.2));
        assertEquals(1.0, GameplayMath.configuredPercentage(250));
        assertEquals(0.0, GameplayMath.configuredPercentage(Double.POSITIVE_INFINITY));
        assertEquals(4, GameplayMath.applyMultiplier(3, 1.2));
    }

    @Test
    void fishingSkillsReduceRealWorkInterval() {
        long base = GameplayMath.workIntervalMillis(2400, 0.2, 0, 0.1, 0);
        long fastFishingOne = GameplayMath.workIntervalMillis(2400, 0.2, 1, 0.1, 0);
        long combined = GameplayMath.workIntervalMillis(2400, 0.2, 1, 0.1, 1);

        assertEquals(120_000L, base);
        assertEquals(96_000L, fastFishingOne);
        assertEquals(84_000L, combined);
    }

    @Test
    void warehouseStoresOnlyRemainingCapacity() {
        assertEquals(3, GameplayMath.storableAmount(3, 50, 40));
        assertEquals(1, GameplayMath.storableAmount(3, 50, 49));
        assertEquals(0, GameplayMath.storableAmount(3, 50, 50));
    }

    @Test
    void ecoChainConsumesOnlySuccessfulCompleteRecipes() {
        assertEquals(2, GameplayMath.completeRecipes(7, 3));
        assertEquals(6, GameplayMath.consumedRecipeInput(2, 3));
        assertEquals(3, GameplayMath.consumedRecipeInput(1, 3));
        assertEquals(0, GameplayMath.completeRecipes(2, 3));
    }

    @Test
    void warehouseReserveProtectsExtractionAndOrders() {
        assertEquals(48, GameplayMath.extractableAmount(64, 16));
        assertEquals(0, GameplayMath.extractableAmount(12, 16));
        assertEquals(64, GameplayMath.extractableAmount(64, 0));
    }

    @Test
    void reservePolicyFreezesARealShareOfStoredItems() {
        assertEquals(20, GameplayMath.effectiveReserveAmount(100, 20, .15));
        assertEquals(15, GameplayMath.effectiveReserveAmount(100, 4, .15));
        assertEquals(10, GameplayMath.effectiveReserveAmount(10, 0, 2.0));
    }

    @Test
    void functionalBuildingsReduceButDoNotEraseCrisisEffects() {
        assertEquals(.65, GameplayMath.mitigatedMultiplier(.65, 0, .15), .0001);
        assertEquals(.7025, GameplayMath.mitigatedMultiplier(.65, 1, .15), .0001);
        assertEquals(1.425, GameplayMath.mitigatedMultiplier(1.5, 1, .15), .0001);
        assertTrue(GameplayMath.mitigatedMultiplier(.65, 3, .15) < 1.0);
    }

    @Test
    void caravansExchangeByValueAndVillageLevelReducesRisk() {
        assertEquals(6, GameplayMath.caravanReturnAmount(24, 8, 25, .80));
        assertEquals(0, GameplayMath.caravanReturnAmount(1, 1, 25, .75));
        assertEquals(.10, GameplayMath.caravanRisk(.10, 1, .02), .0001);
        assertEquals(.04, GameplayMath.caravanRisk(.10, 4, .02), .0001);
        assertEquals(0, GameplayMath.caravanRisk(.10, 20, .02), .0001);
    }

    @Test
    void dailyOrdersScaleWithVillageLevel() {
        assertEquals(32, GameplayMath.scaleByVillageLevel(32, 8, 1));
        assertEquals(48, GameplayMath.scaleByVillageLevel(32, 8, 3));
        assertEquals(32, GameplayMath.scaleByVillageLevel(32, 8, 0));
    }

    @Test
    void needsChangeProductionWithoutStoppingIt() {
        assertEquals(1.10, GameplayMath.needsMultiplier(90, 75, .10, 40, .20, 20, .40));
        assertEquals(1.00, GameplayMath.needsMultiplier(60, 75, .10, 40, .20, 20, .40));
        assertEquals(0.80, GameplayMath.needsMultiplier(30, 75, .10, 40, .20, 20, .40));
        assertEquals(0.60, GameplayMath.needsMultiplier(10, 75, .10, 40, .20, 20, .40));
    }

    @Test
    void personalityRewardsMeaningfullyIncreaseProduction() {
        assertEquals(1.0, GameplayMath.personalityProductionMultiplier(
                79, 79, 80, .05, 80, .05));
        assertEquals(1.05, GameplayMath.personalityProductionMultiplier(
                80, 79, 80, .05, 80, .05));
        assertEquals(1.10, GameplayMath.personalityProductionMultiplier(
                100, 100, 80, .05, 80, .05));
        assertEquals(1.0, GameplayMath.personalityProductionMultiplier(
                100, 100, 80, -.5, 80, Double.NaN));
    }

    @Test
    void workstationLevelsAndExpectedRoundingAffectOutput() {
        assertEquals(1.0, GameplayMath.levelMultiplier(1, .15));
        assertEquals(1.3, GameplayMath.levelMultiplier(3, .15));
        assertEquals(3, GameplayMath.applyExpectedMultiplier(3, 1.1, .9));
        assertEquals(4, GameplayMath.applyExpectedMultiplier(3, 1.1, .1));
    }

    @Test
    void processorLevelsKeepIncreasingOutputAfterBatchCap() {
        assertEquals(1.0, GameplayMath.levelMultiplier(1, .05));
        assertEquals(1.2, GameplayMath.levelMultiplier(5, .05));
        assertEquals(6, GameplayMath.applyExpectedMultiplier(5,
                GameplayMath.levelMultiplier(5, .05), .9));
    }

    @Test
    void accumulatedExperienceSettlesEveryEarnedLevel() {
        GameplayMath.LevelProgress progress = GameplayMath.applyExperience(
                1, 90, 520, 100, 10);

        assertEquals(4, progress.level());
        assertEquals(10, progress.experience());
        assertEquals(new GameplayMath.LevelProgress(2, 500),
                GameplayMath.applyExperience(1, 0, 600, 100, 2));
    }

    @Test
    void repeatedFestivalClaimsCannotExtendAnActiveBoost() {
        long now = 1_000L;
        long activeExpiry = 10_000L;

        assertEquals(activeExpiry,
                GameplayMath.nonExtendingExpiry(activeExpiry, now, 50_000L));
        assertEquals(51_000L,
                GameplayMath.nonExtendingExpiry(now, now, 50_000L));
    }

    @Test
    void combinedIntervalReductionHasAPlayableFloor() {
        assertEquals(90_000L, GameplayMath.workIntervalMillis(2400, .25));
        assertEquals(12_000L, GameplayMath.workIntervalMillis(2400, 2.0));
    }

    @Test
    void consumerNeedsAndWorkstationChangeChainEfficiency() {
        assertEquals(0.8, GameplayMath.chainEfficiency(0.8, 0, 1), 0.0001);
        assertEquals(0.64, GameplayMath.chainEfficiency(0.8, 0, 0.8), 0.0001);
        assertEquals(1.0, GameplayMath.chainEfficiency(0.8, 0.3, 1.1), 0.0001);
    }

    @Test
    void configuredDefenseReductionIsClampedAndApplied() {
        assertEquals(5.0, GameplayMath.damageAfterReduction(10, .50));
        assertEquals(0.0, GameplayMath.damageAfterReduction(10, 2.0));
        assertEquals(10.0, GameplayMath.damageAfterReduction(10, -.5));
        assertEquals(0.0, GameplayMath.damageAfterReduction(Double.NaN, .5));
    }

    @Test
    void probabilitiesAreAlwaysKeptInThePlayableRange() {
        assertEquals(0.0, GameplayMath.clampProbability(-.1));
        assertEquals(.35, GameplayMath.clampProbability(.35));
        assertEquals(1.0, GameplayMath.clampProbability(1.2));
        assertEquals(0.0, GameplayMath.clampProbability(Double.NaN));
        assertEquals(0.0, GameplayMath.clampProbability(Double.POSITIVE_INFINITY));
    }

    @Test
    void automaticShelterRemainsActiveForTheWholeNight() {
        assertFalse(GameplayMath.isShelterTime(12_999, 13_000));
        assertTrue(GameplayMath.isShelterTime(13_000, 13_000));
        assertTrue(GameplayMath.isShelterTime(22_000, 13_000));
        assertFalse(GameplayMath.isShelterTime(24_000, 13_000));
    }
}
