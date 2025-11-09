package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生态联动管理器
 * 负责管理职业协作链、生产效率等核心玩法机制
 */
public class EcoChainManager {
    
    private static EcoChainManager instance;
    private final VillagerPro plugin;
    private final Map<String, ProfessionChain> professionChains;
    private final Map<String, NewProfession> newProfessions;
    
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
        loadConfiguration();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfiguration() {
        if (!plugin.getConfig().getBoolean("features.eco_chain", true)) {
            return;
        }
        
        ConfigurationSection chainsSection = plugin.getConfig().getConfigurationSection("eco_chain.profession_chains");
        if (chainsSection != null) {
            for (String chainName : chainsSection.getKeys(false)) {
                ConfigurationSection chainConfig = chainsSection.getConfigurationSection(chainName);
                if (chainConfig.getBoolean("enabled", true)) {
                    List<ChainStep> steps = loadChainSteps(chainConfig.getList("chain"));
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
                int workInterval = profConfig.getInt("work_interval_ticks", 1200);
                String produces = profConfig.getString("produces", "");
                double efficiency = profConfig.getDouble("efficiency", 1.0);
                
                newProfessions.put(profName, new NewProfession(profName, name, icon, requires, workInterval, produces, efficiency));
            }
        }
        
        plugin.getLogger().info("生态联动系统已加载，发现 " + professionChains.size() + " 个协作链，" + newProfessions.size() + " 个新职业");
    }
    
    /**
     * 加载协作链步骤
     */
    private List<ChainStep> loadChainSteps(List<?> chainList) {
        List<ChainStep> steps = new ArrayList<>();
        
        for (Object obj : chainList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stepData = (Map<String, Object>) obj;
                
                String profession = (String) stepData.get("profession");
                String produces = (String) stepData.getOrDefault("produces", "");
                String consumes = (String) stepData.getOrDefault("consumes", "");
                int ratio = (Integer) stepData.getOrDefault("ratio", 1);
                
                steps.add(new ChainStep(profession, produces, consumes, ratio));
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
        
        // 检查是否有消费者职业的村民
        if (!hasConsumerVillagers(villager.getVillageId(), consumerStep.getProfession())) {
            // 如果没有消费者，直接返回原始产出
            return output;
        }
        
        // 处理协作链：生产者产出可能被消费者消费
        List<ItemStack> consumedItems = new ArrayList<>();
        List<ItemStack> newOutput = new ArrayList<>();
        
        for (ItemStack item : output) {
            if (item != null && isItemMatch(item, producerStep.getProduces())) {
                // 这个物品可以被消费
                consumeItemInChain(villager.getVillageId(), consumerStep, item, consumedItems);
                
                // 消费者产出新物品
                ItemStack consumerOutput = createConsumerOutput(consumerStep, item.getAmount());
                if (consumerOutput != null) {
                    newOutput.add(consumerOutput);
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
     * 检查村民是否参与协作链
     */
    private boolean hasConsumerVillagers(int villageId, String consumerProfession) {
        return VillagerManager.getVillagers(villageId).stream()
                .anyMatch(v -> v.getProfession().equals(consumerProfession));
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
    private ItemStack createConsumerOutput(ChainStep consumerStep, int inputAmount) {
        Material outputMaterial;
        try {
            outputMaterial = Material.valueOf(consumerStep.getProduces());
        } catch (IllegalArgumentException e) {
            // 可能是自定义物品，需要特殊处理
            return null;
        }
        
        int outputAmount = (int) (inputAmount / (double) consumerStep.getRatio());
        if (outputAmount <= 0) {
            return null;
        }
        
        return new ItemStack(outputMaterial, outputAmount);
    }
    
    /**
     * 检查是否有工作站加成
     */
    public double getWorkstationBonus(VillagerData villager, org.bukkit.Location workLocation) {
        if (!plugin.getConfig().getBoolean("eco_chain.workstation_detection.enabled", true)) {
            return 1.0;
        }
        
        double baseEfficiency = plugin.getConfig().getDouble("eco_chain.workstation_detection.efficiency_boost", 0.2);
        int detectionRange = plugin.getConfig().getInt("eco_chain.workstation_detection.detection_range", 3);
        
        // 检测附近是否有工作站
        org.bukkit.block.Block targetBlock = workLocation.getBlock();
        
        // 检查常见的职业相关方块
        for (int x = -detectionRange; x <= detectionRange; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -detectionRange; z <= detectionRange; z++) {
                    org.bukkit.block.Block checkBlock = targetBlock.getRelative(x, y, z);
                    if (isWorkstationBlock(checkBlock, villager.getProfession())) {
                        return 1.0 + baseEfficiency;
                    }
                }
            }
        }
        
        return 1.0;
    }
    
    /**
     * 检查是否是工作站方块
     */
    private boolean isWorkstationBlock(org.bukkit.block.Block block, String profession) {
        Material material = block.getType();
        
        switch (profession.toLowerCase()) {
            case "farmer":
                return material == Material.FARMLAND || material == Material.FURNACE;
            case "fisherman":
                return material == Material.STONECUTTER || material == Material.BARREL;
            case "shepherd":
                return material == Material.SHEARS || material == Material.LOOM;
            case "priest":
                return material == Material.BREWING_STAND || material == Material.CAULDRON;
            case "baker":
                return material == Material.FURNACE || material == Material.SMOKER;
            case "weaver":
                return material == Material.LOOM || material == Material.CRAFTING_TABLE;
            default:
                return false;
        }
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
            return steps.stream().anyMatch(step -> step.getProfession().equals(profession) && !step.getProduces().isEmpty());
        }
        
        public ChainStep getProducerStep(String profession) {
            return steps.stream()
                .filter(step -> step.getProfession().equals(profession) && !step.getProduces().isEmpty())
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
        private final String profession;
        private final String produces;
        private final String consumes;
        private final int ratio;
        
        public ChainStep(String profession, String produces, String consumes, int ratio) {
            this.profession = profession;
            this.produces = produces;
            this.consumes = consumes;
            this.ratio = ratio;
        }
        
        public String getProfession() { return profession; }
        public String getProduces() { return produces; }
        public String getConsumes() { return consumes; }
        public int getRatio() { return ratio; }
        public String getChainName() { return ""; }
    }
    
    /**
     * 新职业
     */
    public static class NewProfession {
        private final String id;
        private final String name;
        private final String icon;
        private final String requires;
        private final int workInterval;
        private final String produces;
        private final double efficiency;
        
        public NewProfession(String id, String name, String icon, String requires, int workInterval, String produces, double efficiency) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.requires = requires;
            this.workInterval = workInterval;
            this.produces = produces;
            this.efficiency = efficiency;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getIcon() { return icon; }
        public String getRequires() { return requires; }
        public int getWorkInterval() { return workInterval; }
        public String getProduces() { return produces; }
        public double getEfficiency() { return efficiency; }
    }
}