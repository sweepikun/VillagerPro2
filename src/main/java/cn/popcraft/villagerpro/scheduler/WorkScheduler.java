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

public class WorkScheduler {
    private static BukkitTask workTask;
    
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
        }.runTaskTimer(VillagerPro.getInstance(), 0L, interval); // 改为同步执行
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
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            Village village = VillageManager.getVillage(player.getUniqueId());
            if (village == null) continue;

            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            for (VillagerData villager : villagers) {
                // 检查村民是否在线且在玩家附近
                // 使用异步安全的方法检查
                if (villager.getEntity() != null && 
                    player.getWorld().equals(villager.getEntity().getWorld()) &&
                    player.getLocation().distance(villager.getEntity().getLocation()) <= 32) {
                    performVillagerWork(villager, village);
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
        
        // 后续可以添加技能加成等更多因素
        // 获取村民技能加成
        int skillBonus = 0;
        switch (villager.getProfession()) {
            case "farmer":
                // 农民的高效收割技能加成
                int efficientHarvestLevel = villager.getSkills().getOrDefault("efficient_harvest", 0);
                skillBonus = efficientHarvestLevel; // 每级高效收割增加1个产出
                break;
            case "fisherman":
                // 渔夫的快速垂钓技能可能影响产出频率而不是数量
                // 这里可以添加其他加成
                break;
            case "shepherd":
                // 牧羊人的技能加成
                // 可以添加相关逻辑
                break;
        }
        
        return baseAmount + levelBonus + skillBonus;
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