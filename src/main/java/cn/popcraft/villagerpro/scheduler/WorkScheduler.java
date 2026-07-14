package cn.popcraft.villagerpro.scheduler;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.WarehouseManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.VillagerAbilityManager;
import cn.popcraft.villagerpro.managers.VillagerUpgradeManager;
import cn.popcraft.villagerpro.managers.ProductionStatsManager;
import cn.popcraft.villagerpro.managers.VillageOrderManager;
import cn.popcraft.villagerpro.managers.WarehouseRuleManager;
import cn.popcraft.villagerpro.managers.NeedsManager;
import cn.popcraft.villagerpro.managers.SpecializationManager;
import cn.popcraft.villagerpro.managers.WorkstationManager;
import cn.popcraft.villagerpro.managers.BuildingManager;
import cn.popcraft.villagerpro.managers.PolicyManager;
import cn.popcraft.villagerpro.managers.CrisisManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import cn.popcraft.villagerpro.managers.ExperienceManager;
import cn.popcraft.villagerpro.managers.EcoChainManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class WorkScheduler {
    private static BukkitTask workTask;
    
    // 存储每个村民的下次产出时间
    private static Map<Integer, Long> nextWorkTime = new ConcurrentHashMap<>();
    
    /**
     * 初始化工作调度器
     */
    public static void initialize() {
        long checkInterval = Math.max(1L, VillagerPro.getInstance().getConfig()
                .getLong("villager.work_check_interval_ticks", 20L));
        
        workTask = new BukkitRunnable() {
            @Override
            public void run() {
                performWork();
            }
        }.runTaskTimer(VillagerPro.getInstance(), checkInterval, checkInterval);
        
        // 初始化所有村民的下次产出时间
        initializeWorkTimes();
        for (Village village : VillageManager.getAllVillages()) {
            VillagerAbilityManager.applyVillageHealthBoost(village);
        }
    }
    
    /**
     * 初始化所有村民的下次产出时间
     */
    private static void initializeWorkTimes() {
        for (Village village : VillageManager.getAllVillages()) {
            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            for (VillagerData villager : villagers) {
                if (!isAllowedWorkWorld(villager)) {
                    continue;
                }
                nextWorkTime.put(villager.getId(),
                        System.currentTimeMillis() + calculateWorkInterval(villager, village));
            }
        }
    }
    
    /**
     * 执行工作
     */
    private static void performWork() {
        // 确保在主线程中执行
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(VillagerPro.getInstance(), () -> performWork());
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Village village = VillageManager.getVillage(player.getUniqueId());
            if (village == null) continue;
            VillagerAbilityManager.applyVillageHealthBoost(village);

            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            boolean warehouseFull = WarehouseManager.getCurrentStorage(village.getId())
                    >= village.getWarehouseCapacity();
            WarehouseRuleManager.OverflowMode fullWarehouseMode = warehouseFull
                    ? WarehouseRuleManager.getOverflowMode(village.getId())
                    : WarehouseRuleManager.OverflowMode.DISCARD;
            for (VillagerData villager : villagers) {
                if (!isAllowedWorkWorld(villager)) {
                    ProductionStatsManager.setDiagnostic(villager.getId(), "当前世界不允许生产");
                    nextWorkTime.put(villager.getId(),
                            currentTime + calculateWorkInterval(villager, village));
                    continue;
                }

                // 检查是否到工作时间
                long workTime = nextWorkTime.computeIfAbsent(villager.getId(),
                        id -> currentTime + calculateWorkInterval(villager, village));
                int workRange = villager.getWorkRange();
                if ("farmer".equals(villager.getProfession())) {
                    workRange += village.getUpgrades().getOrDefault("farming_boost", 0)
                            * VillageUpgradeManager.getIntEffect(
                            "farming_boost", "range_per_level", 2);
                }
                String blockedReason = getLocationBlockReason(player, villager, workRange);
                if (blockedReason != null) {
                    ProductionStatsManager.setDiagnostic(villager.getId(), blockedReason);
                    if (currentTime >= workTime) {
                        nextWorkTime.put(villager.getId(),
                                currentTime + calculateWorkInterval(villager, village));
                    }
                    continue;
                }
                String workstationIssue = WorkstationManager.getOperationalIssue(villager);
                if (workstationIssue != null) {
                    ProductionStatsManager.setDiagnostic(villager.getId(), workstationIssue);
                    if (currentTime >= workTime) {
                        nextWorkTime.put(villager.getId(),
                                currentTime + calculateWorkInterval(villager, village));
                    }
                    continue;
                }
                if (currentTime < workTime) {
                    if (EcoChainManager.getInstance().isNewProfession(villager.getProfession())
                            || EcoChainManager.getInstance().hasProcessingRecipes(villager.getProfession())) {
                        long seconds = Math.max(1, (workTime - currentTime + 999) / 1000);
                        ProductionStatsManager.setDiagnostic(
                                villager.getId(), "等待加工（" + seconds + "秒）");
                    } else if (warehouseFull) {
                        ProductionStatsManager.setDiagnostic(villager.getId(),
                                fullWarehouseMode == WarehouseRuleManager.OverflowMode.DROP
                                        ? "仓库已满，溢出将掉落"
                                        : "仓库已满，溢出将丢弃");
                    } else {
                        long seconds = Math.max(1, (workTime - currentTime + 999) / 1000);
                        ProductionStatsManager.setDiagnostic(
                                villager.getId(), "等待生产（" + seconds + "秒）");
                    }
                    continue;
                }

                if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
                    player.sendMessage("§7[调试] 村民 " + villager.getProfession() + " 开始工作");
                }
                if (EcoChainManager.getInstance().isNewProfession(villager.getProfession())
                        || EcoChainManager.getInstance().hasProcessingRecipes(villager.getProfession())) {
                    performProcessorWork(villager, village);
                    nextWorkTime.put(villager.getId(),
                            currentTime + calculateWorkInterval(villager, village));
                    continue;
                }
                performVillagerWork(villager, village);
                nextWorkTime.put(villager.getId(),
                        currentTime + calculateWorkInterval(villager, village));
            }
        }
    }

    private static void performProcessorWork(VillagerData villager, Village village) {
        EcoChainManager.ProcessingResult result = EcoChainManager.getInstance()
                .processStoredRecipe(villager, village);
        if (!result.handled()) {
            ProductionStatsManager.setDiagnostic(villager.getId(), result.diagnostic());
            return;
        }
        ProductionStatsManager.record(village.getId(), villager.getId(), villager.getProfession(),
                result.outputItem(), result.attemptedAmount(), result.storedAmount(), result.status());
        ProductionStatsManager.setDiagnostic(villager.getId(), result.diagnostic());
        ProductionStatsManager.setLastOutcome(villager.getId(), result.diagnostic());
        if (!result.successful()) return;

        ExperienceManager.addVillagerExperience(
                villager, calculateVillagerExperience(village, villager));
        ExperienceManager.addVillageExperience(village, 1);
        VillageOrderManager.tryAutoSubmit(Bukkit.getPlayer(village.getOwnerUUID()), village);
    }
    
    /**
     * 执行单个村民的工作
     * @param villager 村民
     * @param village 村庄
     */
    private static void performVillagerWork(VillagerData villager, Village village) {
        String profession = villager.getProfession();
        String path = "villager.professions." + profession;
        
        if (!VillagerPro.getInstance().getConfig().contains(path)) {
            recordFailure(villager, "", "INVALID_CONFIG", "职业没有生产配置");
            return;
        }
        
        // 获取该职业的工作物品列表
        List<String> workItems = VillagerPro.getInstance().getConfig().getStringList(path + ".work_items");
        if (workItems.isEmpty()) {
            recordFailure(villager, "", "INVALID_CONFIG", "职业产物列表为空");
            return;
        }

        if ("shepherd".equals(profession)) {
            int woolExpert = villager.getSkills().getOrDefault("wool_expert", 0);
            int colorsPerLevel = VillagerUpgradeManager.getIntEffect(
                    profession, "wool_expert", "colors_per_level", 1);
            int unlockedColors = Math.min(workItems.size(), Math.max(1,
                    woolExpert * Math.max(0, colorsPerLevel) + 1));
            workItems = new ArrayList<>(workItems.subList(0, unlockedColors));
        }

        workItems.removeIf(item -> !WarehouseRuleManager.isProductionEnabled(
                village.getId(), item));
        if (workItems.isEmpty()) {
            recordFailure(villager, "", "PRODUCTION_DISABLED", "该职业的产物已全部停产");
            return;
        }
        
        // 获取基础产出数量
        int baseAmount = VillagerPro.getInstance().getConfig().getInt(path + ".base_amount", 1);
        
        // 获取概率
        double probability = VillagerPro.getInstance().getConfig().getDouble(path + ".probability", 1.0);
        
        // 根据概率决定是否产出
        if (ThreadLocalRandom.current().nextDouble() > probability) {
            recordFailure(villager, "", "MISS", "本次生产概率未命中");
            return;
        }
        
        // 随机选择一个工作物品
        String itemType = workItems.get(ThreadLocalRandom.current().nextInt(workItems.size()));
        Material material = Material.getMaterial(itemType);
        if (material == null) {
            if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
                VillagerPro.getInstance().getLogger().warning("村民职业 " + profession + " 配置了无效物品: " + itemType);
            }
            recordFailure(villager, itemType, "INVALID_CONFIG", "产物配置无效: " + itemType);
            return;
        }
        
        // 计算实际产出数量（考虑村民等级等因素）
        int amount = calculateProductionAmount(villager, baseAmount);

        double productionMultiplier = 1.0;
        if ("farmer".equals(profession)) {
            int farmingBoost = village.getUpgrades().getOrDefault("farming_boost", 0);
            productionMultiplier *= 1.0 + farmingBoost
                    * VillageUpgradeManager.getDoubleEffect(
                    "farming_boost", "production_bonus_per_level", 0.2);
        }
        productionMultiplier *= 1.0 + SpecializationManager.getEffect(
                villager, "production_bonus");
        productionMultiplier *= NeedsManager.getProductionMultiplier(villager);
        productionMultiplier *= WorkstationManager.getProductionMultiplier(villager);
        productionMultiplier *= BuildingManager.getProductionMultiplier(village.getId());
        productionMultiplier *= PolicyManager.getProductionMultiplier(village.getId());
        productionMultiplier *= CrisisManager.getProductionMultiplier(village.getId());
        productionMultiplier *= cn.popcraft.villagerpro.gui.VisitorGUIManager
                .getActiveProductionBoost();
        amount = GameplayMath.applyExpectedMultiplier(amount, productionMultiplier,
                ThreadLocalRandom.current().nextDouble());
        
        // 构建基础产出
        List<ItemStack> baseOutput = new ArrayList<>();
        baseOutput.add(new ItemStack(material, amount));
        if ("fisherman".equals(profession)) {
            int treasureHunter = villager.getSkills().getOrDefault("treasure_hunter", 0);
            int fishingMastery = village.getUpgrades().getOrDefault("fishing_mastery", 0);
            double treasureChance = Math.min(1.0, treasureHunter
                    * VillagerUpgradeManager.getDoubleEffect(profession, "treasure_hunter",
                    "treasure_chance_per_level", 0.1)
                    + fishingMastery * VillageUpgradeManager.getDoubleEffect(
                    "fishing_mastery", "treasure_chance_per_level", 0.05)
                    + SpecializationManager.getEffect(villager, "treasure_chance"));
            if (ThreadLocalRandom.current().nextDouble() < treasureChance) {
                Material treasure = Material.getMaterial(VillagerPro.getInstance().getConfig()
                        .getString(path + ".treasure_item", "HEART_OF_THE_SEA"));
                if (treasure != null && WarehouseRuleManager.isProductionEnabled(
                        village.getId(), treasure.name())) {
                    baseOutput.add(new ItemStack(treasure));
                }
            }
        }
        if ("librarian".equals(profession)) {
            int enchantmentExpert = villager.getSkills().getOrDefault("enchantment_expert", 0);
            double specialChance = enchantmentExpert * VillagerUpgradeManager.getDoubleEffect(
                    profession, "enchantment_expert", "special_chance_per_level", 0.1);
            if (ThreadLocalRandom.current().nextDouble() < Math.min(1.0, specialChance)) {
                Material special = Material.getMaterial(VillagerPro.getInstance().getConfig()
                        .getString(path + ".special_item", "ENCHANTED_BOOK"));
                if (special != null && WarehouseRuleManager.isProductionEnabled(
                        village.getId(), special.name())) {
                    baseOutput.add(new ItemStack(special));
                }
            }
        }
        if ("cartographer".equals(profession)) {
            int treasureMap = villager.getSkills().getOrDefault("treasure_map", 0);
            if (ThreadLocalRandom.current().nextDouble() < Math.min(1.0,
                    treasureMap * VillagerUpgradeManager.getDoubleEffect(
                            profession, "treasure_map", "treasure_chance_per_level", 0.1)
                            + SpecializationManager.getEffect(villager, "treasure_chance"))) {
                Material treasure = Material.getMaterial(VillagerPro.getInstance().getConfig()
                        .getString(path + ".treasure_item", "FILLED_MAP"));
                if (treasure != null && WarehouseRuleManager.isProductionEnabled(
                        village.getId(), treasure.name())) {
                    baseOutput.add(new ItemStack(treasure));
                }
            }
        }
        String rareItem = SpecializationManager.getRareItem(villager);
        double rareChance = SpecializationManager.getEffect(villager, "rare_chance");
        if (rareItem != null && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, rareChance)) {
            Material rareMaterial = Material.getMaterial(rareItem);
            if (rareMaterial != null && WarehouseRuleManager.isProductionEnabled(
                    village.getId(), rareMaterial.name())) {
                baseOutput.add(new ItemStack(rareMaterial));
            }
        }
        
        // 通过生态联动处理产出（如农民+面包师协作）
        List<ItemStack> processedOutput = EcoChainManager.getInstance().processWorkOutput(villager, baseOutput);
        
        // 将产出加入仓库
        int attemptedAmount = 0;
        int storedAmount = 0;
        int droppedAmount = 0;
        WarehouseRuleManager.OverflowMode overflowMode =
                WarehouseRuleManager.getOverflowMode(village.getId());
        for (ItemStack item : processedOutput) {
            if (item != null && item.getAmount() > 0) {
                if (!WarehouseRuleManager.isProductionEnabled(
                        village.getId(), item.getType().name())) {
                    continue;
                }
                attemptedAmount += item.getAmount();
                int stored = WarehouseManager.storeWarehouseItem(
                        village.getId(), item.getType().name(), item.getAmount());
                storedAmount += stored;
                int overflow = item.getAmount() - stored;
                if (overflow > 0 && overflowMode == WarehouseRuleManager.OverflowMode.DROP
                        && villager.getEntity() != null) {
                    dropOverflow(villager, item.getType(), overflow);
                    droppedAmount += overflow;
                }
            }
        }

        if (storedAmount <= 0) {
            String status = droppedAmount > 0 ? "DROPPED" : "WAREHOUSE_FULL";
            ProductionStatsManager.record(village.getId(), villager.getId(), profession,
                    itemType, attemptedAmount, 0, status);
            ProductionStatsManager.setDiagnostic(villager.getId(), droppedAmount > 0
                    ? "仓库已满，产物按规则掉落" : "仓库已满");
            ProductionStatsManager.setLastOutcome(villager.getId(), droppedAmount > 0
                    ? "产物溢出并掉落 " + droppedAmount : "仓库已满，本次未入库");
            return;
        }

        String status = storedAmount < attemptedAmount
                ? (droppedAmount > 0 ? "PARTIAL_DROPPED" : "PARTIAL") : "SUCCESS";
        ProductionStatsManager.record(village.getId(), villager.getId(), profession,
                itemType, attemptedAmount, storedAmount, status);
        ProductionStatsManager.setDiagnostic(villager.getId(), "生产成功，入库 " + storedAmount);
        ProductionStatsManager.setLastOutcome(villager.getId(), "成功入库 " + storedAmount
                + (droppedAmount > 0 ? "，溢出掉落 " + droppedAmount : ""));

        // 增加村民与村庄经验，并通过 ExperienceManager 进行升级判定
        ExperienceManager.addVillagerExperience(
                villager, calculateVillagerExperience(village, villager));
        ExperienceManager.addVillageExperience(village, 1);
        VillageOrderManager.tryAutoSubmit(
                Bukkit.getPlayer(village.getOwnerUUID()), village);
    }
    
    /**
     * 计算实际产出数量
     * @param villager 村民
     * @param baseAmount 基础数量
     * @return 实际产出数量
     */
    private static int calculateProductionAmount(VillagerData villager, int baseAmount) {
        int skillBonus = VillagerUpgradeManager.getProductionSkillBonus(villager);
        int amountPerLevel = VillagerPro.getInstance().getConfig()
                .getInt("villager.production_amount_per_level", 1);
        int totalAmount = GameplayMath.productionAmount(
                baseAmount, villager.getLevel(), amountPerLevel, skillBonus);
        
        // 调试信息
        if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
            VillagerPro.getInstance().getLogger().info(String.format(
                "村民 %s (等级 %d): 基础产出 %d, 等级加成 %d, 技能加成 %d, 总产出 %d",
                villager.getProfession(), villager.getLevel(), baseAmount,
                Math.max(0, villager.getLevel() - 1) * Math.max(0, amountPerLevel),
                skillBonus, totalAmount
            ));
        }
        
        return totalAmount;
    }

    private static long calculateWorkInterval(VillagerData villager, Village village) {
        long baseInterval = VillagerPro.getInstance().getConfig()
                .getLong("villager.work_interval_ticks", 2400L);
        if ("fisherman".equals(villager.getProfession())) {
            int fastFishing = villager.getSkills().getOrDefault("fast_fishing", 0);
            int villageMastery = village.getUpgrades().getOrDefault("fishing_mastery", 0);
            double reduction = fastFishing * VillagerUpgradeManager.getDoubleEffect(
                    villager.getProfession(), "fast_fishing",
                    "interval_reduction_per_level", 0.2)
                    + villageMastery * VillageUpgradeManager.getDoubleEffect(
                    "fishing_mastery", "interval_reduction_per_level", 0.1)
                    + SpecializationManager.getEffect(villager, "interval_reduction");
            return GameplayMath.workIntervalMillis(baseInterval, reduction);
        }
        return GameplayMath.workIntervalMillis(baseInterval,
                SpecializationManager.getEffect(villager, "interval_reduction"));
    }

    private static boolean isAllowedWorkWorld(VillagerData villager) {
        if (VillagerPro.getInstance().getConfig().getBoolean(
                "compatibility.allow_work_in_all_worlds", false)) {
            return true;
        }
        return villager.getEntity() == null
                || (!Bukkit.getWorlds().isEmpty()
                && villager.getEntity().getWorld().equals(Bukkit.getWorlds().get(0)));
    }

    private static int calculateVillagerExperience(Village village, VillagerData villager) {
        double amount = VillagerAbilityManager.getKnowledgeMultiplier(village)
                * (1.0 + SpecializationManager.getEffect(villager, "experience_bonus"));
        int experience = (int) Math.floor(amount);
        if (ThreadLocalRandom.current().nextDouble() < amount - experience) {
            experience++;
        }
        return Math.max(1, experience);
    }

    private static void recordFailure(VillagerData villager, String itemType,
                                      String status, String diagnostic) {
        ProductionStatsManager.record(villager.getVillageId(), villager.getId(),
                villager.getProfession(), itemType, 0, 0, status);
        ProductionStatsManager.setDiagnostic(villager.getId(), diagnostic);
        ProductionStatsManager.setLastOutcome(villager.getId(), diagnostic);
    }

    private static String getLocationBlockReason(Player player, VillagerData villager, int workRange) {
        if (villager.getEntity() == null) return "村民实体不存在";
        if (!player.getWorld().equals(villager.getEntity().getWorld())) {
            return "村民与主人不在同一世界";
        }
        double distance = player.getLocation().distance(villager.getEntity().getLocation());
        if (distance > workRange) {
            return "距离过远（" + String.format("%.1f", distance) + "/" + workRange + "格）";
        }
        return null;
    }

    private static void dropOverflow(VillagerData villager, Material material, int amount) {
        int remaining = amount;
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            villager.getEntity().getWorld().dropItemNaturally(
                    villager.getEntity().getLocation(), new ItemStack(material, stackAmount));
            remaining -= stackAmount;
        }
    }
    
    /**
     * 获取村民的剩余工作时间（毫秒）
     * @param villagerId 村民ID
     * @return 剩余时间，如果<=0则表示可以工作
     */
    public static long getRemainingWorkTime(int villagerId) {
        Long workTime = nextWorkTime.get(villagerId);
        if (workTime == null) {
            return 0;
        }
        return workTime - System.currentTimeMillis();
    }
    
    /**
     * 检查村民是否可以工作
     * @param villagerId 村民ID
     * @return 是否可以工作
     */
    public static boolean canWorkNow(int villagerId) {
        return getRemainingWorkTime(villagerId) <= 0;
    }
    
    /**
     * 关闭工作调度器
     */
    public static void shutdown() {
        if (workTask != null) {
            workTask.cancel();
        }
    }
}
