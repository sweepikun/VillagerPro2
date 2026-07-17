package cn.popcraft.villagerpro.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayConfigTest {
    private static YamlConfiguration config;

    @BeforeAll
    static void loadConfig() throws Exception {
        config = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(
                GameplayConfigTest.class.getClassLoader().getResourceAsStream("config.yml"),
                StandardCharsets.UTF_8)) {
            config.load(reader);
        }
    }

    @Test
    void allConfiguredProfessionOutputsAreValidMaterials() {
        ConfigurationSection professions = config.getConfigurationSection("villager.professions");
        assertNotNull(professions);
        for (String profession : professions.getKeys(false)) {
            String path = "villager.professions." + profession;
            for (String item : config.getStringList(path + ".work_items")) {
                assertNotNull(Material.getMaterial(item), profession + " has invalid output " + item);
            }
            assertNotNull(Material.getMaterial(config.getString(path + ".icon")),
                    profession + " has an invalid icon");
            double probability = config.getDouble(path + ".probability");
            assertTrue(probability >= 0.0 && probability <= 1.0,
                    profession + " probability must be between 0 and 1");
        }
    }

    @Test
    void villageGrowthHasACompletePaidProgression() {
        int maxLevel = config.getInt("village.max_level");
        assertTrue(maxLevel > 1);
        assertTrue(config.getInt("village.base_exp_per_level") > 0);
        List<Map<?, ?>> levels = config.getMapList("village.upgrade_costs");
        assertTrue(levels.size() >= maxLevel - 1);
        for (int level = 1; level < maxLevel; level++) {
            Object costsObject = levels.get(level - 1).get("costs");
            assertTrue(costsObject instanceof List<?> && !((List<?>) costsObject).isEmpty(),
                    "village level " + level + " has no upgrade cost");
            for (Object costObject : (List<?>) costsObject) {
                assertTrue(costObject instanceof Map<?, ?>);
                Map<?, ?> cost = (Map<?, ?>) costObject;
                assertTrue(cost.get("amount") instanceof Number
                        && ((Number) cost.get("amount")).doubleValue() > 0);
                String type = String.valueOf(cost.get("type")).toLowerCase();
                assertTrue(List.of("vault", "playerpoints", "item", "itemsadder").contains(type));
                if ("item".equals(type)) {
                    assertNotNull(Material.getMaterial(String.valueOf(cost.get("item"))));
                } else if ("itemsadder".equals(type)) {
                    assertTrue(String.valueOf(cost.get("item")).contains(":"));
                }
            }
        }
    }

    @Test
    void allUpgradeIconsAreValidMaterials() {
        assertIconsValid(config.getConfigurationSection("village_upgrades.available_upgrades"));
        ConfigurationSection professions = config.getConfigurationSection("villager_upgrades");
        assertNotNull(professions);
        for (String profession : professions.getKeys(false)) {
            assertIconsValid(professions.getConfigurationSection(profession));
        }
    }

    @Test
    void everyPurchasableUpgradeDeclaresPositiveRuntimeEffects() {
        ConfigurationSection villageUpgrades = config.getConfigurationSection(
                "village_upgrades.available_upgrades");
        assertNotNull(villageUpgrades);
        for (String upgrade : villageUpgrades.getKeys(false)) {
            assertPositiveNumericEffects(villageUpgrades.getConfigurationSection(
                    upgrade + ".effects"), "village upgrade " + upgrade);
        }

        ConfigurationSection professions = config.getConfigurationSection("villager_upgrades");
        assertNotNull(professions);
        for (String profession : professions.getKeys(false)) {
            ConfigurationSection skills = professions.getConfigurationSection(profession);
            assertNotNull(skills);
            for (String skill : skills.getKeys(false)) {
                assertPositiveNumericEffects(skills.getConfigurationSection(skill + ".effects"),
                        profession + " skill " + skill);
            }
        }
        assertTrue(config.getInt("villager.production_amount_per_level") > 0);
    }

    @Test
    void configuredItemCostsUseValidIdentifiers() {
        validateCosts(config.getMapList("villager.recruit_cost"));
        ConfigurationSection decorations = config.getConfigurationSection("decorations.items");
        assertNotNull(decorations);
        for (String key : decorations.getKeys(false)) {
            validateCosts(config.getMapList("decorations.items." + key + ".cost"));
        }
        validateCosts(config.getMapList("defense.guard.cost"));
    }

    @Test
    void decorationEffectsDeclareTheirRuntimeToggle() {
        for (String type : List.of("street_light", "flower_bed", "bench")) {
            assertTrue(config.getBoolean("decorations.items." + type + "."
                    + switch (type) {
                        case "street_light" -> "world_interaction";
                        case "flower_bed" -> "villager_interaction";
                        default -> "villager_sit";
                    }), type + " must explicitly declare its runtime effect");
        }
    }

    @Test
    void defenseSettingsHaveBoundedAndUsableValues() {
        assertTrue(config.getInt("defense.guard.duration_minutes") > 0);
        assertTrue(config.getInt("defense.guard.max_active_per_village") > 0);
        assertTrue(config.getDouble("defense.guard.max_health") > 0);
        assertTrue(config.getDouble("defense.guard.attack_damage") >= 0);
        assertTrue(config.getDouble("defense.guard.movement_speed") > 0);
        double reduction = config.getDouble("defense.protection.damage_reduction");
        assertTrue(reduction >= 0 && reduction <= 1);
    }

    @Test
    void personalityUsesBoundedProductionBenefits() {
        assertTrue(config.getBoolean("features.personality"));
        assertTrue(config.getBoolean("personality.enabled"));
        for (String value : List.of("loyalty_threshold", "mood_threshold")) {
            assertTrue(config.getInt("personality.production." + value) >= 0
                    && config.getInt("personality.production." + value) <= 100);
        }
        for (String value : List.of("loyalty_bonus", "mood_bonus")) {
            assertTrue(config.getDouble("personality.production." + value) >= 0
                    && config.getDouble("personality.production." + value) <= .25);
        }
    }

    @Test
    void everyFestivalHasARealRewardOrProductionEffect() {
        ConfigurationSection festivals = config.getConfigurationSection(
                "visitors.festival.festivals");
        assertNotNull(festivals);
        java.util.Set<String> displayNames = new java.util.HashSet<>();
        for (String festival : festivals.getKeys(false)) {
            String path = "visitors.festival.festivals." + festival;
            assertTrue(displayNames.add(config.getString(path + ".name")));
            assertTrue(config.getLong(path + ".duration_hours") > 0);
            ConfigurationSection effect = config.getConfigurationSection(path + ".effect");
            assertNotNull(effect);
            boolean useful = false;
            if (effect.contains("production_boost")) {
                assertTrue(effect.getDouble("production_boost") > 1.0);
                useful = true;
            }
            if (effect.contains("item")) {
                assertNotNull(Material.getMaterial(effect.getString("item")));
                assertTrue(effect.getInt("amount") > 0);
                useful = true;
            }
            assertTrue(useful, festival + " has no playable reward or effect");
        }
    }

    @Test
    void travelerDealsUseWarehouseCompatibleItemsAndRewards() {
        List<Map<?, ?>> deals = config.getMapList("visitors.traveler.deals");
        assertFalse(deals.isEmpty());
        for (Map<?, ?> deal : deals) {
            assertNotNull(Material.getMaterial(String.valueOf(deal.get("item"))));
            assertTrue(((Number) deal.get("amount")).intValue() > 0);
            Object rewardObject = deal.get("reward");
            assertTrue(rewardObject instanceof Map<?, ?>);
            Map<?, ?> reward = (Map<?, ?>) rewardObject;
            assertNotNull(Material.getMaterial(String.valueOf(reward.get("item"))));
            assertTrue(((Number) reward.get("amount")).intValue() > 0);
        }
    }

    @Test
    void merchantProductsHavePositiveQuantitiesAndPayablePrices() {
        List<Map<?, ?>> products = config.getMapList("visitors.merchant.items");
        assertFalse(products.isEmpty());
        java.util.Set<String> productIds = new java.util.HashSet<>();
        for (Map<?, ?> product : products) {
            assertTrue(productIds.add(String.valueOf(product.get("id"))));
            Object amount = product.get("amount");
            assertTrue(amount == null || ((Number) amount).intValue() > 0);
            assertTrue(((Number) product.get("stock")).intValue() > 0);
            assertTrue(((Number) product.get("per_player_limit")).intValue() > 0);
            assertTrue(((Number) product.get("per_player_limit")).intValue()
                    <= ((Number) product.get("stock")).intValue());
            Object pricesObject = product.get("price");
            assertTrue(pricesObject instanceof List<?> && !((List<?>) pricesObject).isEmpty());
            for (Object priceObject : (List<?>) pricesObject) {
                assertTrue(priceObject instanceof Map<?, ?>);
                Map<?, ?> price = (Map<?, ?>) priceObject;
                assertTrue(((Number) price.get("amount")).doubleValue() > 0);
                assertTrue(List.of("vault", "playerpoints", "itemsadder", "item")
                        .contains(String.valueOf(price.get("type")).toLowerCase()));
            }
        }
    }

    @Test
    void unfinishedLegacyModuleStaysDisabledByDefault() {
        assertFalse(config.getBoolean("features.legacy"));
        assertFalse(config.getBoolean("legacy.enabled"));
    }

    @Test
    void managedVillagerLifecycleCleanupIsEnabledByDefault() {
        assertTrue(config.getBoolean("compatibility.auto_cleanup_dead_villagers"));
    }

    @Test
    void ecoChainsUseValidRecipesAndEfficiencies() {
        assertTrue(config.getInt("eco_chain.processing_base_batches") > 0);
        assertTrue(config.getDouble("eco_chain.processing_output_bonus_per_level") > 0);
        ConfigurationSection chains = config.getConfigurationSection("eco_chain.profession_chains");
        assertNotNull(chains);
        for (String chain : chains.getKeys(false)) {
            for (Map<?, ?> step : config.getMapList(
                    "eco_chain.profession_chains." + chain + ".chain")) {
                Object produces = step.get("produces");
                if (produces != null) {
                    assertNotNull(Material.getMaterial(String.valueOf(produces)),
                            chain + " has invalid output " + produces);
                }
                Object consumes = step.get("consumes");
                if (consumes != null) {
                    assertNotNull(Material.getMaterial(String.valueOf(consumes)),
                            chain + " has invalid input " + consumes);
                    assertTrue(((Number) step.get("ratio")).intValue() > 0,
                            chain + " needs a positive recipe ratio");
                }
            }
        }

        ConfigurationSection professions = config.getConfigurationSection("eco_chain.new_professions");
        assertNotNull(professions);
        for (String profession : professions.getKeys(false)) {
            double efficiency = professions.getDouble(profession + ".efficiency");
            assertTrue(efficiency >= 0.0 && efficiency <= 1.0,
                    profession + " efficiency must be between 0 and 1");
        }

        ConfigurationSection recipes = config.getConfigurationSection("eco_chain.processing_recipes");
        assertNotNull(recipes);
        java.util.Set<String> allInputs = new java.util.HashSet<>();
        java.util.Set<String> allOutputs = new java.util.HashSet<>();
        for (String recipe : recipes.getKeys(false)) {
            String path = "eco_chain.processing_recipes." + recipe;
            String profession = config.getString(path + ".profession");
            assertTrue(professions.contains(profession), recipe + " has no recruitable processor");
            ConfigurationSection inputs = config.getConfigurationSection(path + ".inputs");
            ConfigurationSection outputs = config.getConfigurationSection(path + ".outputs");
            assertNotNull(inputs);
            assertNotNull(outputs);
            assertFalse(inputs.getKeys(false).isEmpty());
            assertFalse(outputs.getKeys(false).isEmpty());
            for (String item : inputs.getKeys(false)) {
                assertNotNull(Material.getMaterial(item), recipe + " has invalid input " + item);
                assertTrue(inputs.getInt(item) > 0);
                allInputs.add(item);
            }
            for (String item : outputs.getKeys(false)) {
                assertNotNull(Material.getMaterial(item), recipe + " has invalid output " + item);
                assertTrue(outputs.getInt(item) > 0);
                allOutputs.add(item);
            }
            double efficiency = config.getDouble(path + ".efficiency");
            assertTrue(efficiency > 0 && efficiency <= 1);
            assertTrue(config.getInt(path + ".max_batches_per_cycle") > 0);
        }
        assertTrue(allOutputs.stream().anyMatch(allInputs::contains),
                "processing recipes must contain at least one real intermediate product");
    }

    @Test
    void dailyOrderPoolUsesValidPlayableRewards() {
        assertTrue(config.getBoolean("orders.enabled"));
        assertTrue(config.getInt("orders.daily_count") > 0);
        double marketCap = config.getDouble("orders.market_value_cap_multiplier");
        assertTrue(marketCap > 0 && marketCap <= 1);
        ConfigurationSection pool = config.getConfigurationSection("orders.pool");
        assertNotNull(pool);
        assertTrue(pool.getKeys(false).size() >= config.getInt("orders.daily_count"));
        for (String order : pool.getKeys(false)) {
            String path = "orders.pool." + order;
            assertNotNull(Material.getMaterial(config.getString(path + ".item")),
                    order + " has an invalid delivery item");
            assertTrue(config.getInt(path + ".amount") > 0,
                    order + " must require at least one item");
            assertTrue(config.getDouble(path + ".reward_money") > 0
                            || config.getInt(path + ".reward_prosperity") > 0,
                    order + " must provide a real reward");
            String item = config.getString(path + ".item");
            double basePrice = config.getDouble("market.items." + item + ".base_price");
            assertTrue(basePrice > 0, order + " cannot be valued by the market");
            double cappedUnitValue = basePrice * config.getDouble("market.sell_factor") * marketCap;
            double replacementUnitCost = basePrice * config.getDouble("market.buy_factor");
            assertTrue(cappedUnitValue < replacementUnitCost,
                    order + " can be bought and submitted for guaranteed profit");
        }
    }

    @Test
    void specializationsNeedsAndWorkstationsArePlayableConfigurations() {
        ConfigurationSection specializations = config.getConfigurationSection(
                "specializations.professions");
        assertNotNull(specializations);
        for (String profession : specializations.getKeys(false)) {
            ConfigurationSection branches = specializations.getConfigurationSection(profession);
            assertNotNull(branches);
            assertTrue(branches.getKeys(false).size() >= 2,
                    profession + " needs mutually exclusive choices");
            for (String branch : branches.getKeys(false)) {
                String path = "specializations.professions." + profession + "." + branch;
                assertNotNull(Material.getMaterial(config.getString(path + ".icon")),
                        branch + " has an invalid specialization icon");
                assertNotNull(config.getConfigurationSection(path + ".effects"),
                        branch + " has no runtime effects");
                String rareItem = config.getString(path + ".effects.rare_item");
                if (rareItem != null) assertNotNull(Material.getMaterial(rareItem));
                ConfigurationSection effects = config.getConfigurationSection(path + ".effects");
                assertNotNull(effects);
                for (String effect : effects.getKeys(false)) {
                    if (effect.contains("chance")) {
                        double chance = effects.getDouble(effect);
                        assertTrue(chance >= 0.0 && chance <= 1.0,
                                branch + " has an out-of-range " + effect);
                    }
                }
            }
        }

        ConfigurationSection materials = config.getConfigurationSection("workstations.materials");
        assertNotNull(materials);
        for (String profession : materials.getKeys(false)) {
            assertNotNull(Material.getMaterial(materials.getString(profession)),
                    profession + " has an invalid workstation material");
        }
        for (int level = 2; level <= config.getInt("workstations.max_level"); level++) {
            String path = "workstations.upgrade_costs." + level;
            assertTrue(config.getDouble(path + ".vault") > 0);
            String item = config.getString(path + ".item");
            if (item != null) {
                assertNotNull(Material.getMaterial(item), "invalid workstation upgrade item");
                assertTrue(config.getInt(path + ".amount") > 0);
            }
        }
        for (String supply : new String[]{"food", "comfort", "health"}) {
            for (String item : config.getStringList("needs.supplies." + supply + ".items")) {
                assertNotNull(Material.getMaterial(item), supply + " has invalid supply " + item);
            }
            assertTrue(config.getDouble("needs.supplies." + supply + ".restore") > 0);
        }
        assertTrue(config.getDouble("needs.decay.hunger_per_hour") >= 0);
        assertTrue(config.getDouble("needs.decay.comfort_per_hour") >= 0);
        assertTrue(config.getDouble("needs.decay.health_per_hour") >= 0);
        double excellent = config.getDouble("needs.efficiency.excellent_threshold");
        double poor = config.getDouble("needs.efficiency.poor_threshold");
        double critical = config.getDouble("needs.efficiency.critical_threshold");
        assertTrue(excellent >= poor && poor >= critical && critical >= 0);
        assertTrue(excellent <= 100);
        assertTrue(config.getDouble("needs.auto_consume_threshold") >= 0
                && config.getDouble("needs.auto_consume_threshold") <= 100);
    }

    @Test
    void functionalBuildingsHaveValidCoresRequirementsAndEffects() {
        assertTrue(config.getBoolean("features.buildings"));
        assertTrue(config.getBoolean("buildings.enabled"));
        ConfigurationSection types = config.getConfigurationSection("buildings.types");
        assertNotNull(types);
        for (String type : List.of("granary", "workshop", "clinic")) {
            String path = "buildings.types." + type;
            assertNotNull(types.getConfigurationSection(type));
            assertNotNull(Material.getMaterial(config.getString(path + ".core")));
            java.util.Map<String, Integer> previousLevel = new java.util.HashMap<>();
            for (int level = 1; level <= config.getInt("buildings.max_level"); level++) {
                ConfigurationSection requirements = config.getConfigurationSection(
                        path + ".levels." + level);
                assertNotNull(requirements, type + " level " + level + " has no structural requirements");
                for (String metric : requirements.getKeys(false)) {
                    assertTrue(List.of("barrels", "planks", "furnaces", "anvils", "beds",
                                    "brewing_stands", "bookshelves", "roof_columns").contains(metric),
                            type + " has unsupported detection metric " + metric);
                    int required = requirements.getInt(metric);
                    assertTrue(required > 0, type + " has non-positive " + metric + " requirement");
                    assertTrue(required >= previousLevel.getOrDefault(metric, 0),
                            type + " level " + level + " reduces its " + metric + " requirement");
                    previousLevel.put(metric, required);
                }
            }
        }
        assertTrue(config.getInt("buildings.types.granary.capacity_per_level") > 0);
        assertTrue(config.getDouble("buildings.types.workshop.production_bonus_per_level") > 0);
        assertTrue(config.getDouble("buildings.types.clinic.decay_reduction_per_level") > 0);
        assertTrue(config.getDouble("buildings.types.clinic.health_restore_bonus_per_level") > 0);
    }

    @Test
    void dynamicMarketHasValidItemsSpreadAndLimits() {
        assertTrue(config.getBoolean("features.market"));
        assertTrue(config.getBoolean("market.enabled"));
        assertTrue(config.getDouble("market.sell_factor") > 0);
        assertTrue(config.getDouble("market.sell_factor") < 1);
        assertTrue(config.getDouble("market.buy_factor") > 1);
        assertTrue(config.getDouble("market.liquidity") > 0);
        assertTrue(config.getInt("market.maximum_transaction_amount") > 0);
        assertTrue(config.getInt("market.daily_volume_base") > 0);
        assertTrue(config.getInt("market.trade_history_retention_days") > 0);
        ConfigurationSection items = config.getConfigurationSection("market.items");
        assertNotNull(items);
        assertTrue(items.getKeys(false).size() >= 8);
        for (String item : items.getKeys(false)) {
            assertNotNull(Material.getMaterial(item), "market has invalid item " + item);
            assertTrue(items.getDouble(item + ".base_price") > 0);
        }
        assertTrue(items.contains("COOKIE"));
        assertTrue(items.contains("PAINTING"));
    }

    @Test
    void villagePoliciesHaveRealBenefitsAndTradeoffs() {
        assertTrue(config.getBoolean("features.policies"));
        assertTrue(config.getBoolean("policies.enabled"));
        assertTrue(config.getLong("policies.duration_hours") > 0);
        assertTrue(config.getDouble("policies.overtime.production_multiplier") > 1);
        assertTrue(config.getDouble("policies.overtime.needs_decay_multiplier") > 1);
        assertTrue(config.getDouble("policies.welfare.needs_decay_multiplier") < 1);
        assertTrue(config.getInt("policies.welfare.consume_threshold_bonus") > 0);
        assertTrue(config.getDouble("policies.welfare.supply_restore_multiplier") > 1);
        assertTrue(config.getDouble("policies.export.market_sell_multiplier") > 1);
        assertTrue(config.getDouble("policies.export.market_buy_multiplier") > 1);
        assertTrue(config.getDouble("policies.export.order_reward_multiplier") < 1);
        assertTrue(config.getDouble("policies.reserve.capacity_multiplier") > 1);
        assertTrue(config.getDouble("policies.reserve.frozen_stock_fraction") > 0);
    }

    @Test
    void crisesRequireValidReliefAndChangeRealGameplayMultipliers() {
        assertTrue(config.getBoolean("features.crises"));
        assertTrue(config.getBoolean("crises.enabled"));
        assertTrue(config.getDouble("crises.trigger_chance_per_roll") > 0);
        assertTrue(config.getDouble("crises.trigger_chance_per_roll") <= 1);
        assertTrue(config.getLong("crises.duration_hours") > 0);
        assertTrue(config.getInt("crises.failure_prosperity_penalty") > 0);
        for (String type : List.of("harvest_failure", "epidemic", "fire", "trade_blockade")) {
            String path = "crises.types." + type;
            String reliefItem = config.getString(path + ".required_item");
            assertNotNull(Material.getMaterial(reliefItem));
            assertTrue(config.contains("market.items." + reliefItem),
                    reliefItem + " crisis relief is not obtainable from the market");
            assertTrue(config.getInt(path + ".required_amount") > 0);
        }
        assertTrue(config.getDouble("crises.types.harvest_failure.production_multiplier") < 1);
        assertTrue(config.getDouble("crises.types.epidemic.production_multiplier") < 1);
        assertTrue(config.getDouble("crises.types.epidemic.needs_decay_multiplier") > 1);
        assertTrue(config.getDouble("crises.types.fire.production_multiplier") < 1);
        assertTrue(config.getDouble("crises.types.trade_blockade.market_sell_multiplier") < 1);
        assertTrue(config.getDouble("crises.types.trade_blockade.market_buy_multiplier") > 1);
    }

    @Test
    void caravanRoutesUseMarketValuedItemsAndHaveTimeRiskAndLoss() {
        assertTrue(config.getBoolean("features.caravans"));
        assertTrue(config.getBoolean("caravans.enabled"));
        assertTrue(config.getInt("caravans.minimum_cargo") > 0);
        assertTrue(config.getInt("caravans.maximum_cargo") >= config.getInt("caravans.minimum_cargo"));
        assertTrue(config.getInt("caravans.max_active_routes") > 0);
        assertTrue(config.getInt("caravans.history_retention_days") > 0);
        ConfigurationSection destinations = config.getConfigurationSection("caravans.destinations");
        assertNotNull(destinations);
        assertTrue(destinations.getKeys(false).size() >= 3);
        for (String destination : destinations.getKeys(false)) {
            String path = "caravans.destinations." + destination;
            List<String> accepted = config.getStringList(path + ".accepted_items");
            assertFalse(accepted.isEmpty());
            for (String item : accepted) {
                assertNotNull(Material.getMaterial(item));
                assertTrue(config.getDouble("market.items." + item + ".base_price") > 0);
            }
            String returnItem = config.getString(path + ".return_item");
            assertNotNull(Material.getMaterial(returnItem));
            assertTrue(config.getDouble("market.items." + returnItem + ".base_price") > 0);
            assertTrue(config.getLong(path + ".duration_minutes") > 0);
            assertTrue(config.getDouble(path + ".risk_chance") > 0);
            assertTrue(config.getDouble(path + ".risk_chance") <= 1);
            assertTrue(config.getDouble(path + ".return_value_multiplier") > 0);
            assertTrue(config.getDouble(path + ".return_value_multiplier") < 1);
        }
    }

    @Test
    void visitorSpawnProbabilityIsBounded() {
        double probability = config.getDouble("visitors.spawn_probability");
        assertTrue(probability >= 0.0 && probability <= 1.0);
    }

    private static void assertIconsValid(ConfigurationSection section) {
        assertNotNull(section);
        for (String key : section.getKeys(false)) {
            String icon = section.getString(key + ".icon");
            assertNotNull(Material.getMaterial(icon), key + " has an invalid icon " + icon);
        }
    }

    private static void assertPositiveNumericEffects(ConfigurationSection effects, String owner) {
        assertNotNull(effects, owner + " has no runtime effects");
        assertFalse(effects.getKeys(false).isEmpty(), owner + " has no runtime effects");
        for (String effect : effects.getKeys(false)) {
            Object value = effects.get(effect);
            assertTrue(value instanceof Number && ((Number) value).doubleValue() > 0,
                    owner + " has invalid effect " + effect);
        }
    }

    private static void validateCosts(List<Map<?, ?>> costs) {
        for (Map<?, ?> cost : costs) {
            String type = String.valueOf(cost.get("type"));
            if ("item".equalsIgnoreCase(type)) {
                String item = String.valueOf(cost.get("item"));
                assertNotNull(Material.getMaterial(item), "invalid vanilla cost item " + item);
            } else if ("itemsadder".equalsIgnoreCase(type)) {
                assertTrue(String.valueOf(cost.get("item")).contains(":"),
                        "ItemsAdder cost needs a namespace");
            }
        }
    }
}
