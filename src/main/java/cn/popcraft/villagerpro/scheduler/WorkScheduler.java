package cn.popcraft.villagerpro.scheduler;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.WarehouseManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.Map;

public class WorkScheduler {
    private static BukkitTask workTask;
    
    // 存储每个村民的下次产出时间
    private static Map<Integer, Long> nextWorkTime = new HashMap<>();
    
    /**
     * 初始化工作调度器
     */
    public static void initialize() {
        long interval = VillagerPro.getInstance().getConfig().getLong("villager.work_interval_ticks", 2400L);
        
        workTask = new BukkitRunnable() {
            @Override
            public void run() {
                performWork();
            }
        }.runTaskTimer(VillagerPro.getInstance(), 0L, interval);
        
        // 初始化所有村民的下次产出时间
        initializeWorkTimes();
    }
    
    /**
     * 初始化所有村民的下次产出时间
     */
    private static void initializeWorkTimes() {
        for (Village village : VillageManager.getAllVillages()) {
            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            for (VillagerData villager : villagers) {
                long workInterval = VillagerPro.getInstance().getConfig().getLong("villager.work_interval_ticks", 2400L);
                nextWorkTime.put(villager.getId(), System.currentTimeMillis() + workInterval * 50);
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
        long workInterval = VillagerPro.getInstance().getConfig().getLong("villager.work_interval_ticks", 2400L) * 50;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            Village village = VillageManager.getVillage(player.getUniqueId());
            if (village == null) continue;

            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            for (VillagerData villager : villagers) {
                // 检查是否到工作时间
                Long workTime = nextWorkTime.get(villager.getId());
                if (workTime != null && currentTime >= workTime) {
                    // 检查村民是否在线且在玩家附近（使用村民的实际工作范围）
                    if (villager.getEntity() != null && 
                        player.getWorld().equals(villager.getEntity().getWorld()) &&
                        player.getLocation().distance(villager.getEntity().getLocation()) <= villager.getWorkRange()) {
                        
                        // 调试信息
                        if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
                            player.sendMessage("§7[调试] 村民 " + villager.getProfession() + " 开始工作");
                        }
                        
                        performVillagerWork(villager, village);
                    } else {
                        // 调试信息 - 说明为什么没有工作
                        if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
                            String reason = "";
                            if (villager.getEntity() == null) {
                                reason = "村民实体不存在";
                            } else if (!player.getWorld().equals(villager.getEntity().getWorld())) {
                                reason = "村民在不同世界";
                            } else {
                                double distance = player.getLocation().distance(villager.getEntity().getLocation());
                                int workRange = villager.getWorkRange();
                                reason = "距离太远(" + String.format("%.1f", distance) + "格 > " + workRange + "格)";
                            }
                            player.sendMessage("§7[调试] 村民 " + villager.getProfession() + " 未工作: " + reason);
                        }
                    }
                    
                    // 设置下次工作时间
                    nextWorkTime.put(villager.getId(), currentTime + workInterval);
                }
            }
        }
    }
    
    /**
     * 执行单个村民的工作
     * @param villager 村民
     * @param village 村庄
     */
    private static void performVillagerWork(VillagerData villager, Village village) {
        String profession = villager.getProfession();
        String path = "villager.professions." + profession;
        
        if (VillagerPro.getInstance().getConfig().contains(path)) {
            // 获取该职业的工作物品列表
            List<String> workItems = VillagerPro.getInstance().getConfig().getStringList(path + ".work_items");
            if (workItems.isEmpty()) return;
            
            // 获取基础产出数量
            int baseAmount = VillagerPro.getInstance().getConfig().getInt(path + ".base_amount", 1);
            
            // 获取概率
            double probability = VillagerPro.getInstance().getConfig().getDouble(path + ".probability", 1.0);
            
            // 根据概率决定是否产出
            if (ThreadLocalRandom.current().nextDouble() > probability) return;
            
            // 随机选择一个工作物品
            String itemType = workItems.get(ThreadLocalRandom.current().nextInt(workItems.size()));
            
            // 计算实际产出数量（考虑村民等级等因素）
            int amount = calculateProductionAmount(villager, baseAmount);
            
            // 添加到仓库
            WarehouseManager.addWarehouseItem(village.getId(), itemType, amount);
            
            // 增加村民经验
            villager.addExperience(1);
            VillagerManager.updateVillager(villager);
        }
    }
    
    /**
     * 计算实际产出数量
     * @param villager 村民
     * @param baseAmount 基础数量
     * @return 实际产出数量
     */
    private static int calculateProductionAmount(VillagerData villager, int baseAmount) {
        // 基础数量加上村民等级的影响
        int levelBonus = (villager.getLevel() - 1) * 1; // 每级增加1个产出
        
        // 获取村民技能加成
        int skillBonus = 0;
        try {
            // 安全地获取村民技能，必要时使用数据库查询
            Map<String, Integer> skills = villager.getSkills();
            if (skills != null) {
                switch (villager.getProfession()) {
                    case "farmer":
                        // 农民的高效收割技能加成
                        int efficientHarvestLevel = skills.getOrDefault("efficient_harvest", 0);
                        skillBonus += efficientHarvestLevel * 1; // 每级高效收割增加1个产出
                        break;
                    case "fisherman":
                        // 渔夫的快速垂钓技能加成
                        int fastFishingLevel = skills.getOrDefault("fast_fishing", 0);
                        skillBonus += fastFishingLevel * 1; // 每级快速垂钓增加1个产出
                        break;
                    case "shepherd":
                        // 牧羊人的高效剪毛技能加成
                        int efficientShearingLevel = skills.getOrDefault("efficient_shearing", 0);
                        skillBonus += efficientShearingLevel * 1; // 每级高效剪毛增加1个产出
                        break;
                    case "miner":
                        // 矿工的快速挖掘技能加成
                        int quickMiningLevel = skills.getOrDefault("quick_mining", 0);
                        skillBonus += quickMiningLevel * 1; // 每级快速挖掘增加1个产出
                        break;
                }
            }
        } catch (Exception e) {
            // 如果技能获取失败，至少保证等级加成生效
            if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
                VillagerPro.getInstance().getLogger().warning("获取村民技能时出错: " + e.getMessage());
            }
        }
        
        int totalAmount = baseAmount + levelBonus + skillBonus;
        
        // 调试信息
        if (VillagerPro.getInstance().getConfig().getBoolean("debug", false)) {
            VillagerPro.getInstance().getLogger().info(String.format(
                "村民 %s (等级 %d): 基础产出 %d, 等级加成 %d, 技能加成 %d, 总产出 %d",
                villager.getProfession(), villager.getLevel(), baseAmount, levelBonus, skillBonus, totalAmount
            ));
        }
        
        return totalAmount;
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