package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 生态联动管理器
 * 负责管理职业协作链、生产效率等核心玩法机制
 */
public class EcoChainManager {
    
    private static EcoChainManager instance;
    private final VillagerPro plugin;
    private final Map<String, ProfessionChain> professionChains;
    private final Map<String, NewProfession> newProfessions;
    private final Map<String, List<ProcessingRecipe>> processingRecipes;
    
    public static EcoChainManager getInstance() {
        if (instance == null) {
            instance = new EcoChainManager();
        }
        return instance;
    }
    
    private EcoChainManager() {
        this.plugin = VillagerPro.getInstance();
        this.professionChains = new HashMap<>();
        this.newProfessions = new HashMap<>();
        this.processingRecipes = new HashMap<>();
        loadConfiguration();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfiguration() {
        professionChains.clear();
        newProfessions.clear();
        processingRecipes.clear();
        if (!plugin.getConfig().getBoolean("features.eco_chain", true)) {
            return;
        }

        ConfigurationSection recipesSection = plugin.getConfig()
                .getConfigurationSection("eco_chain.processing_recipes");
        if (recipesSection != null) {
            for (String recipeId : recipesSection.getKeys(false)) {
                ProcessingRecipe recipe = loadProcessingRecipe(
                        recipeId, recipesSection.getConfigurationSection(recipeId));
                if (recipe != null) {
                    processingRecipes.computeIfAbsent(recipe.profession(), key -> new ArrayList<>())
                            .add(recipe);
                }
            }
            processingRecipes.values().forEach(recipes -> recipes.sort(
                    Comparator.comparingInt(ProcessingRecipe::priority).reversed()));
        } else {
            loadDefaultProcessingRecipes();
        }
        
        ConfigurationSection chainsSection = plugin.getConfig().getConfigurationSection("eco_chain.profession_chains");
        if (chainsSection != null) {
            for (String chainName : chainsSection.getKeys(false)) {
                ConfigurationSection chainConfig = chainsSection.getConfigurationSection(chainName);
                if (chainConfig.getBoolean("enabled", true)) {
                    List<ChainStep> steps = loadChainSteps(chainName, chainConfig.getList("chain"));
                    if (!steps.isEmpty()) {
                        professionChains.put(chainName, new ProfessionChain(chainName, steps));
                    }
                }
            }
        }
        
        // 加载新职业配置
        ConfigurationSection newProfSection = plugin.getConfig().getConfigurationSection("eco_chain.new_professions");
        if (newProfSection != null) {
            for (String profName : newProfSection.getKeys(false)) {
                ConfigurationSection profConfig = newProfSection.getConfigurationSection(profName);
                String name = profConfig.getString("name", profName);
                String icon = profConfig.getString("icon", "STONE");
                String requires = profConfig.getString("requires", "");
                String produces = profConfig.getString("produces", "");
                double efficiency = profConfig.getDouble("efficiency", 1.0);
                
                newProfessions.put(profName, new NewProfession(
                        profName, name, icon, requires, produces, efficiency));
            }
        }
        
        int recipeCount = processingRecipes.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("生态联动系统已加载，发现 " + professionChains.size() + " 个协作链，"
                + newProfessions.size() + " 个加工职业，" + recipeCount + " 个仓库加工配方");
    }

    private void loadDefaultProcessingRecipes() {
        addDefaultRecipe(new ProcessingRecipe("pastry_baking", "糕点烘焙", "baker",
                Map.of("BREAD", 3), Map.of("COOKIE", 6), 20, 2, 0.70));
        addDefaultRecipe(new ProcessingRecipe("bread_baking", "面包烘焙", "baker",
                Map.of("WHEAT", 3), Map.of("BREAD", 1), 10, 4, 0.80));
        addDefaultRecipe(new ProcessingRecipe("textile_art", "织物工艺", "weaver",
                Map.of("WHITE_CARPET", 6), Map.of("PAINTING", 1), 20, 2, 0.65));
        addDefaultRecipe(new ProcessingRecipe("carpet_weaving", "地毯织造", "weaver",
                Map.of("WHITE_WOOL", 4), Map.of("WHITE_CARPET", 3), 10, 4, 0.75));
        processingRecipes.values().forEach(recipes -> recipes.sort(
                Comparator.comparingInt(ProcessingRecipe::priority).reversed()));
    }

    private void addDefaultRecipe(ProcessingRecipe recipe) {
        processingRecipes.computeIfAbsent(recipe.profession(), key -> new ArrayList<>()).add(recipe);
    }

    public void reloadConfiguration() {
        loadConfiguration();
    }

    private ProcessingRecipe loadProcessingRecipe(String id, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) return null;
        String profession = section.getString("profession", "").toLowerCase(Locale.ROOT);
        Map<String, Integer> inputs = readMaterialAmounts(section.getConfigurationSection("inputs"));
        Map<String, Integer> outputs = readMaterialAmounts(section.getConfigurationSection("outputs"));
        if (profession.isBlank() || inputs.isEmpty() || outputs.isEmpty()) {
            plugin.getLogger().warning("忽略无效加工配方 " + id + "：需要 profession、inputs 和 outputs");
            return null;
        }
        return new ProcessingRecipe(id, section.getString("name", id), profession,
                inputs, outputs, section.getInt("priority", 0),
                Math.max(1, section.getInt("max_batches_per_cycle", 1)),
                Math.max(0, Math.min(1, section.getDouble("efficiency", 1.0))));
    }

    private Map<String, Integer> readMaterialAmounts(ConfigurationSection section) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (section == null) return values;
        for (String item : section.getKeys(false)) {
            Material material = Material.matchMaterial(item);
            int amount = section.getInt(item);
            if (material == null || amount <= 0) {
                plugin.getLogger().warning("忽略加工配方中的无效物品数量: " + item + "=" + amount);
                continue;
            }
            values.put(material.name(), amount);
        }
        return values;
    }
    
    /**
     * 加载协作链步骤
     */
    private List<ChainStep> loadChainSteps(String chainName, List<?> chainList) {
        List<ChainStep> steps = new ArrayList<>();
        
        for (Object obj : chainList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stepData = (Map<String, Object>) obj;
                
                String profession = (String) stepData.get("profession");
                String produces = (String) stepData.getOrDefault("produces", "");
                String consumes = (String) stepData.getOrDefault("consumes", "");
                int ratio = (Integer) stepData.getOrDefault("ratio", 1);
                
                steps.add(new ChainStep(chainName, profession, produces, consumes, ratio));
            }
        }
        
        return steps;
    }
    
    /**
     * 处理村民工作产出
     */
    public List<ItemStack> processWorkOutput(VillagerData villager, List<ItemStack> baseOutput) {
        if (!isFeatureEnabled() || baseOutput.isEmpty()) {
            return baseOutput;
        }
        if (plugin.getConfig().getBoolean("eco_chain.stored_processing_enabled", true)) {
            return baseOutput;
        }
        
        List<ItemStack> processedOutput = new ArrayList<>(baseOutput);
        
        // 检查是否触发协作链
        for (Map.Entry<String, ProfessionChain> entry : professionChains.entrySet()) {
            String chainName = entry.getKey();
            ProfessionChain chain = entry.getValue();
            
            if (chain.isProducer(villager.getProfession())) {
                processedOutput = processChain(chain, villager, processedOutput);
            }
        }
        
        return processedOutput;
    }

    public boolean hasProcessingRecipes(String profession) {
        return isFeatureEnabled()
                && plugin.getConfig().getBoolean("eco_chain.stored_processing_enabled", true)
                && !processingRecipes.getOrDefault(profession, List.of()).isEmpty();
    }

    public ProcessingResult processStoredRecipe(VillagerData villager, Village village) {
        List<ProcessingRecipe> recipes = processingRecipes.getOrDefault(
                villager.getProfession(), List.of());
        if (!isFeatureEnabled()
                || !plugin.getConfig().getBoolean("eco_chain.stored_processing_enabled", true)
                || recipes.isEmpty()) {
            return new ProcessingResult(false, false, "NO_RECIPE", "", 0, 0,
                    "该职业没有仓库加工配方");
        }

        boolean outputDisabled = false;
        for (ProcessingRecipe recipe : recipes) {
            if (recipe.outputs().keySet().stream().anyMatch(
                    item -> !WarehouseRuleManager.isProductionEnabled(village.getId(), item))) {
                outputDisabled = true;
                continue;
            }

            int possibleBatches = Math.min(recipe.maxBatchesPerCycle(),
                    Math.max(1, plugin.getConfig().getInt("eco_chain.processing_base_batches", 1)
                            + villager.getLevel() - 1));
            for (Map.Entry<String, Integer> input : recipe.inputs().entrySet()) {
                possibleBatches = Math.min(possibleBatches,
                        WarehouseManager.getExtractableAmount(village.getId(), input.getKey())
                                / input.getValue());
            }
            if (possibleBatches <= 0) continue;

            double efficiency = GameplayMath.chainEfficiency(recipe.efficiency(),
                    Math.max(0, WorkstationManager.getProductionMultiplier(villager) - 1.0)
                            + SpecializationManager.getEffect(villager, "chain_efficiency")
                            + getUpstreamSpecializationEffect(village.getId(),
                            villager.getProfession(), "chain_efficiency"),
                    NeedsManager.getProductionMultiplier(villager));
            int successfulBatches = 0;
            for (int batch = 0; batch < possibleBatches; batch++) {
                if (ThreadLocalRandom.current().nextDouble() < efficiency) successfulBatches++;
            }
            String primaryOutput = recipe.outputs().keySet().iterator().next();
            if (successfulBatches <= 0) {
                return new ProcessingResult(true, false, "MISS", primaryOutput, 0, 0,
                        recipe.name() + "本轮加工失败，原料未消耗");
            }

            Map<String, Integer> consumed = multiplyAmounts(recipe.inputs(), successfulBatches, 1.0);
            double processorLevelMultiplier = GameplayMath.levelMultiplier(villager.getLevel(),
                    plugin.getConfig().getDouble(
                            "eco_chain.processing_output_bonus_per_level", 0.05));
            Map<String, Integer> produced = multiplyAmounts(recipe.outputs(), successfulBatches,
                    processorLevelMultiplier
                            * BuildingManager.getProductionMultiplier(village.getId())
                            * PolicyManager.getProductionMultiplier(village.getId())
                            * CrisisManager.getProductionMultiplier(village.getId())
                            * cn.popcraft.villagerpro.gui.VisitorGUIManager
                            .getActiveProductionBoost());
            Map<String, Integer> reserves = new LinkedHashMap<>();
            for (String input : consumed.keySet()) {
                reserves.put(input, WarehouseManager.getProtectedReserveAmount(
                        village.getId(), input));
            }
            int consumedTotal = consumed.values().stream().mapToInt(Integer::intValue).sum();
            int producedTotal = produced.values().stream().mapToInt(Integer::intValue).sum();
            int currentStorage = WarehouseManager.getCurrentStorage(village.getId());
            if (currentStorage - consumedTotal + producedTotal > village.getWarehouseCapacity()) {
                return new ProcessingResult(true, false, "WAREHOUSE_FULL", primaryOutput,
                        producedTotal, 0, recipe.name() + "等待仓库空间");
            }

            try {
                if (!applyStoredRecipe(village.getId(), consumed, produced, reserves)) {
                    return new ProcessingResult(true, false, "MISSING_INPUT", primaryOutput,
                            producedTotal, 0, recipe.name() + "原料在加工前发生变化");
                }
                recordStoredActivities(village.getId(), villager.getProfession(), recipe.id(), consumed, produced);
                return new ProcessingResult(true, true, "SUCCESS", primaryOutput,
                        producedTotal, producedTotal, recipe.name() + "完成 " + successfulBatches + " 批");
            } catch (SQLException exception) {
                plugin.getLogger().warning("执行仓库加工配方失败: " + exception.getMessage());
                return new ProcessingResult(true, false, "DATABASE_ERROR", primaryOutput,
                        producedTotal, 0, "加工数据保存失败");
            }
        }

        return new ProcessingResult(true, false,
                outputDisabled ? "PRODUCTION_DISABLED" : "MISSING_INPUT", "", 0, 0,
                outputDisabled ? "加工产物已停产" : "等待仓库中的加工原料");
    }

    private double getUpstreamSpecializationEffect(int villageId,
                                                    String processorProfession,
                                                    String effectId) {
        double highestEffect = 0.0;
        List<VillagerData> villagers = VillagerManager.getVillagers(villageId);
        for (ProfessionChain chain : professionChains.values()) {
            ChainStep consumer = chain.getConsumerStep();
            if (consumer == null || !processorProfession.equals(consumer.getProfession())) {
                continue;
            }
            for (VillagerData candidate : villagers) {
                if (chain.isProducer(candidate.getProfession())) {
                    highestEffect = Math.max(highestEffect,
                            SpecializationManager.getEffect(candidate, effectId));
                }
            }
        }
        return highestEffect;
    }

    private Map<String, Integer> multiplyAmounts(Map<String, Integer> amounts, int batches,
                                                  double multiplier) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
            int baseAmount = entry.getValue() * batches;
            int actual = multiplier == 1.0 ? baseAmount : GameplayMath.applyExpectedMultiplier(
                    baseAmount, multiplier, ThreadLocalRandom.current().nextDouble());
            result.put(entry.getKey(), Math.max(1, actual));
        }
        return result;
    }

    private boolean applyStoredRecipe(int villageId, Map<String, Integer> consumed,
                                      Map<String, Integer> produced,
                                      Map<String, Integer> reserves) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            return StoredRecipeTransaction.apply(connection, DatabaseManager.getDialect(),
                    villageId, consumed, produced, reserves);
        }
    }

    private void recordStoredActivities(int villageId, String profession, String recipeId,
                                        Map<String, Integer> consumed, Map<String, Integer> produced) {
        String sql = "INSERT INTO chain_activities (village_id, chain_name, step_type, profession, "
                + "item_type, amount, consumed_at, produced_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> input : consumed.entrySet()) {
                bindActivity(statement, villageId, recipeId, "consume", profession,
                        input.getKey(), input.getValue(), true);
                statement.addBatch();
            }
            for (Map.Entry<String, Integer> output : produced.entrySet()) {
                bindActivity(statement, villageId, recipeId, "produce", profession,
                        output.getKey(), output.getValue(), false);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            plugin.getLogger().warning("记录仓库加工活动失败: " + exception.getMessage());
        }
    }

    private void bindActivity(PreparedStatement statement, int villageId, String recipeId,
                              String stepType, String profession, String itemType,
                              int amount, boolean consumed) throws SQLException {
        statement.setInt(1, villageId);
        statement.setString(2, recipeId);
        statement.setString(3, stepType);
        statement.setString(4, profession);
        statement.setString(5, itemType);
        statement.setInt(6, amount);
        if (consumed) {
            statement.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
            statement.setNull(8, java.sql.Types.TIMESTAMP);
        } else {
            statement.setNull(7, java.sql.Types.TIMESTAMP);
            statement.setTimestamp(8, new java.sql.Timestamp(System.currentTimeMillis()));
        }
    }
    
    /**
     * 处理协作链
     */
    private List<ItemStack> processChain(ProfessionChain chain, VillagerData villager, List<ItemStack> output) {
        ChainStep producerStep = chain.getProducerStep(villager.getProfession());
        if (producerStep == null || producerStep.getProduces().isEmpty()) {
            return output;
        }
        
        // 查找消费者步骤
        ChainStep consumerStep = chain.getConsumerStep();
        if (consumerStep == null) {
            return output;
        }

        if (!WarehouseRuleManager.isProductionEnabled(
                villager.getVillageId(), consumerStep.getProduces())) {
            return output;
        }
        
        VillagerData consumerVillager = WorkstationManager.findOperationalVillager(
                villager.getVillageId(), consumerStep.getProfession());
        if (consumerVillager == null) {
            // 如果没有消费者，直接返回原始产出
            return output;
        }
        
        // 处理协作链：生产者产出可能被消费者消费
        List<ItemStack> consumedItems = new ArrayList<>();
        List<ItemStack> newOutput = new ArrayList<>();
        
        for (ItemStack item : output) {
            if (item != null && isItemMatch(item, producerStep.getProduces())) {
                int possibleConversions = GameplayMath.completeRecipes(
                        item.getAmount(), consumerStep.getRatio());
                NewProfession consumer = newProfessions.get(consumerStep.getProfession());
                double efficiency = consumer == null ? 1.0 : consumer.getEfficiency();
                efficiency = GameplayMath.chainEfficiency(efficiency,
                        SpecializationManager.getEffect(villager, "chain_efficiency")
                                + Math.max(0, WorkstationManager.getProductionMultiplier(
                                consumerVillager) - 1.0),
                        NeedsManager.getProductionMultiplier(consumerVillager));
                int successfulConversions = 0;
                for (int i = 0; i < possibleConversions; i++) {
                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < efficiency) {
                        successfulConversions++;
                    }
                }

                int consumedAmount = GameplayMath.consumedRecipeInput(
                        successfulConversions, consumerStep.getRatio());
                int remainingAmount = item.getAmount() - consumedAmount;
                if (consumedAmount > 0) {
                    ItemStack consumed = item.clone();
                    consumed.setAmount(consumedAmount);
                    consumedItems.add(consumed);
                    ItemStack consumerOutput = createConsumerOutput(
                            consumerStep, successfulConversions);
                    if (consumerOutput != null) {
                        newOutput.add(consumerOutput);
                    }
                }
                if (remainingAmount > 0) {
                    ItemStack remainder = item.clone();
                    remainder.setAmount(remainingAmount);
                    newOutput.add(remainder);
                }
            } else {
                // 不参与协作链的物品直接保留
                newOutput.add(item);
            }
        }
        
        // 记录协作链日志
        recordChainActivity(villager.getVillageId(), chain.getName(), consumedItems, newOutput);
        
        return newOutput;
    }
    
    /**
     * 消费协作链物品
     */
    private void consumeItemInChain(int villageId, ChainStep consumerStep, ItemStack item, List<ItemStack> consumedItems) {
        // 记录消费到数据库
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO chain_activities (village_id, chain_name, step_type, profession, item_type, amount, consumed_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
            
            stmt.setInt(1, villageId);
            stmt.setString(2, consumerStep.getChainName());
            stmt.setString(3, "consume");
            stmt.setString(4, consumerStep.getProfession());
            stmt.setString(5, item.getType().name());
            stmt.setInt(6, item.getAmount());
            stmt.executeUpdate();
            
            consumedItems.add(item);
        } catch (SQLException e) {
            plugin.getLogger().warning("记录协作链活动失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建消费者产出
     */
    private ItemStack createConsumerOutput(ChainStep consumerStep, int outputAmount) {
        Material outputMaterial;
        try {
            outputMaterial = Material.valueOf(consumerStep.getProduces());
        } catch (IllegalArgumentException e) {
            // 可能是自定义物品，需要特殊处理
            return null;
        }
        
        if (outputAmount <= 0) {
            return null;
        }
        
        return new ItemStack(outputMaterial, outputAmount);
    }
    
    /**
     * 获取新职业信息
     */
    public NewProfession getNewProfession(String professionName) {
        return newProfessions.get(professionName);
    }
    
    /**
     * 检查职业是否是新职业
     */
    public boolean isNewProfession(String professionName) {
        return newProfessions.containsKey(professionName);
    }
    
    /**
     * 检查村庄是否满足新职业的前置条件
     */
    public boolean hasPrerequisites(Village village, String newProfessionName) {
        NewProfession newProf = newProfessions.get(newProfessionName);
        if (newProf == null || newProf.getRequires().isEmpty()) {
            return true;
        }
        
        // 检查是否有前置职业的村民
        return VillagerManager.getVillagers(village.getId()).stream()
                .anyMatch(v -> v.getProfession().equals(newProf.getRequires()));
    }

    public String getRequiredProfession(String professionName) {
        NewProfession profession = newProfessions.get(professionName);
        return profession == null ? "" : profession.getRequires();
    }
    
    /**
     * 记录协作链活动
     */
    private void recordChainActivity(int villageId, String chainName, List<ItemStack> consumed, List<ItemStack> produced) {
        try (Connection conn = DatabaseManager.getConnection()) {
            // 记录消费
            for (ItemStack item : consumed) {
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO chain_activities (village_id, chain_name, step_type, item_type, amount, consumed_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)");
                stmt.setInt(1, villageId);
                stmt.setString(2, chainName);
                stmt.setString(3, "consume");
                stmt.setString(4, item.getType().name());
                stmt.setInt(5, item.getAmount());
                stmt.executeUpdate();
                stmt.close();
            }
            
            // 记录产出
            for (ItemStack item : produced) {
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO chain_activities (village_id, chain_name, step_type, item_type, amount, produced_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)");
                stmt.setInt(1, villageId);
                stmt.setString(2, chainName);
                stmt.setString(3, "produce");
                stmt.setString(4, item.getType().name());
                stmt.setInt(5, item.getAmount());
                stmt.executeUpdate();
                stmt.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("记录协作链活动失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查功能是否启用
     */
    private boolean isFeatureEnabled() {
        return plugin.getConfig().getBoolean("features.eco_chain", true) && 
               plugin.getConfig().getBoolean("eco_chain.enabled", true);
    }
    
    private boolean isItemMatch(ItemStack item, String itemType) {
        return item.getType().name().equals(itemType) || 
               item.getType().name().equals(itemType.toUpperCase());
    }
    
    // ============== 数据类 ==============

    public record ProcessingRecipe(String id, String name, String profession,
                                   Map<String, Integer> inputs, Map<String, Integer> outputs,
                                   int priority, int maxBatchesPerCycle, double efficiency) {
        public ProcessingRecipe {
            inputs = Map.copyOf(inputs);
            outputs = Map.copyOf(outputs);
        }
    }

    public record ProcessingResult(boolean handled, boolean successful, String status,
                                   String outputItem, int attemptedAmount, int storedAmount,
                                   String diagnostic) {
    }
    
    /**
     * 职业协作链
     */
    public static class ProfessionChain {
        private final String name;
        private final List<ChainStep> steps;
        
        public ProfessionChain(String name, List<ChainStep> steps) {
            this.name = name;
            this.steps = steps;
        }
        
        public boolean isProducer(String profession) {
            return steps.stream().anyMatch(step -> step.getProfession().equals(profession)
                    && step.getConsumes().isEmpty() && !step.getProduces().isEmpty());
        }
        
        public ChainStep getProducerStep(String profession) {
            return steps.stream()
                .filter(step -> step.getProfession().equals(profession)
                        && step.getConsumes().isEmpty() && !step.getProduces().isEmpty())
                .findFirst()
                .orElse(null);
        }
        
        public ChainStep getConsumerStep() {
            return steps.stream()
                .filter(step -> !step.getConsumes().isEmpty())
                .findFirst()
                .orElse(null);
        }
        
        public String getName() { return name; }
        public List<ChainStep> getSteps() { return steps; }
    }
    
    /**
     * 协作链步骤
     */
    public static class ChainStep {
        private final String chainName;
        private final String profession;
        private final String produces;
        private final String consumes;
        private final int ratio;
        
        public ChainStep(String chainName, String profession, String produces, String consumes, int ratio) {
            this.chainName = chainName;
            this.profession = profession;
            this.produces = produces;
            this.consumes = consumes;
            this.ratio = ratio;
        }
        
        public String getProfession() { return profession; }
        public String getProduces() { return produces; }
        public String getConsumes() { return consumes; }
        public int getRatio() { return ratio; }
        public String getChainName() { return chainName; }
    }
    
    /**
     * 新职业
     */
    public static class NewProfession {
        private final String id;
        private final String name;
        private final String icon;
        private final String requires;
        private final String produces;
        private final double efficiency;
        
        public NewProfession(String id, String name, String icon, String requires,
                             String produces, double efficiency) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.requires = requires;
            this.produces = produces;
            this.efficiency = efficiency;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getIcon() { return icon; }
        public String getRequires() { return requires; }
        public String getProduces() { return produces; }
        public double getEfficiency() { return efficiency; }
    }
}
