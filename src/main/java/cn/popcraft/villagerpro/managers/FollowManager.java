package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FollowManager {
    private static final Map<UUID, BukkitTask> followTasks = new HashMap<>();
    private static final double FOLLOW_DISTANCE = 5.0; // 开始跟随的距离
    private static final double STOP_DISTANCE = 2.0; // 停止跟随的距离
    
    /**
     * 切换村民跟随模式
     * @param villagerData 村民数据
     */
    public static void toggleFollowMode(VillagerData villagerData) {
        String currentMode = villagerData.getFollowMode();
        String newMode;
        
        switch (currentMode) {
            case "FREE":
                newMode = "FOLLOW";
                startFollowing(villagerData);
                break;
            case "FOLLOW":
                newMode = "STAY";
                stopFollowing(villagerData);
                break;
            case "STAY":
                newMode = "FREE";
                break;
            default:
                newMode = "FREE";
                break;
        }
        
        villagerData.setFollowMode(newMode);
        
        // 异步更新数据库
        Bukkit.getScheduler().runTaskAsynchronously(VillagerPro.getInstance(), () -> {
            VillagerManager.updateVillager(villagerData);
        });
        
        // 更新村民的自定义名称以显示跟随模式
        updateVillagerDisplayName(villagerData, newMode);
        
        // 如果切换到跟随模式，立即开始跟随
        if ("FOLLOW".equals(newMode)) {
            startFollowing(villagerData);
        }
    }
    
    /**
     * 更新村民显示名称
     * @param villagerData 村民数据
     * @param mode 跟随模式
     */
    private static void updateVillagerDisplayName(VillagerData villagerData, String mode) {
        Villager villager = villagerData.getEntity();
        if (villager != null && !villager.isDead()) {
            String professionName = VillagerManager.getProfessionDisplayName(villagerData.getProfession());
            String modeDisplay = getModeDisplayName(mode);
            villager.setCustomName("§a" + professionName + " §7[" + modeDisplay + "]");
            villager.setCustomNameVisible(true);
        }
    }
    
    /**
     * 获取跟随模式显示名称
     * @param mode 模式代码
     * @return 显示名称
     */
    private static String getModeDisplayName(String mode) {
        switch (mode) {
            case "FOLLOW":
                return "跟随";
            case "STAY":
                return "停留";
            case "FREE":
            default:
                return "自由";
        }
    }
    
    /**
     * 开始跟随任务
     * @param villagerData 村民数据
     */
    public static void startFollowing(VillagerData villagerData) {
        // 如果已有跟随任务，先停止
        stopFollowing(villagerData);
        
        // 创建新的跟随任务（每10 ticks执行一次，即0.5秒）
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(VillagerPro.getInstance(), () -> {
            // 确保在主线程中执行
            if (!Bukkit.isPrimaryThread()) {
                return;
            }
            
            Villager villager = villagerData.getEntity();
            if (villager == null || villager.isDead()) {
                stopFollowing(villagerData);
                return;
            }
            
            // 获取村庄拥有者
            UUID ownerUUID = getVillageOwnerUUID(villagerData.getVillageId());
            if (ownerUUID == null) {
                return;
            }
            
            Player owner = Bukkit.getPlayer(ownerUUID);
            if (owner == null || !owner.isOnline()) {
                return;
            }
            
            // 检查村民与玩家的距离
            Location villagerLocation = villager.getLocation();
            Location playerLocation = owner.getLocation();
            
            // 检查是否在同一世界
            if (villagerLocation.getWorld() == null || !villagerLocation.getWorld().equals(playerLocation.getWorld())) {
                return;
            }
            
            double distance = villagerLocation.distance(playerLocation);
            String followMode = villagerData.getFollowMode();
            
            // 根据跟随模式执行相应操作
            switch (followMode) {
                case "FOLLOW":
                    handleFollowMode(villager, owner, distance);
                    break;
                case "STAY":
                    // 停留模式：村民停留在原地，不做任何移动
                    handleStayMode(villager);
                    break;
                case "FREE":
                default:
                    // 自由模式：村民可以自由活动
                    break;
            }
        }, 0L, 10L); // 每0.5秒执行一次
        
        followTasks.put(villagerData.getEntityUUID(), task);
    }
    
    /**
     * 处理跟随模式
     * @param villager 村民实体
     * @param owner 玩家（村庄拥有者）
     * @param distance 当前距离
     */
    private static void handleFollowMode(Villager villager, Player owner, double distance) {
        if (distance > FOLLOW_DISTANCE) {
            // 距离太远，让村民导航到玩家位置
            // 使用setTarget让村民移动到玩家附近
            villager.setTarget(owner);
        } else if (distance < STOP_DISTANCE) {
            // 距离太近，清除目标让村民停止移动
            villager.setTarget(null);
        }
    }
    
    /**
     * 处理停留模式
     * @param villager 村民实体
     */
    private static void handleStayMode(Villager villager) {
        // 清除目标让村民停留在原地
        villager.setTarget(null);
    }
    
    /**
     * 停止跟随任务
     * @param villagerData 村民数据
     */
    public static void stopFollowing(VillagerData villagerData) {
        if (villagerData == null || villagerData.getEntityUUID() == null) {
            return;
        }
        
        BukkitTask task = followTasks.get(villagerData.getEntityUUID());
        if (task != null) {
            task.cancel();
            followTasks.remove(villagerData.getEntityUUID());
        }
        
        // 停止村民的移动
        Villager villager = villagerData.getEntity();
        if (villager != null && !villager.isDead()) {
            villager.setTarget(null);
        }
    }
    
    /**
     * 关闭所有跟随任务
     */
    public static void shutdown() {
        for (BukkitTask task : followTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        followTasks.clear();
    }
    
    /**
     * 检查村民是否有跟随任务
     * @param villagerData 村民数据
     * @return 是否有跟随任务
     */
    public static boolean isFollowing(VillagerData villagerData) {
        return villagerData != null && villagerData.getEntityUUID() != null 
            && followTasks.containsKey(villagerData.getEntityUUID());
    }
    
    /**
     * 获取村庄拥有者的UUID
     * @param villageId 村庄ID
     * @return 拥有者UUID
     */
    private static UUID getVillageOwnerUUID(int villageId) {
        // 通过村庄ID获取村庄信息
        cn.popcraft.villagerpro.models.Village village = VillageManager.getVillageById(villageId);
        return village != null ? village.getOwnerUUID() : null;
    }
    
    /**
     * 玩家退出时清理相关跟随任务
     * @param playerUUID 玩家UUID
     */
    public static void cleanupPlayerTasks(UUID playerUUID) {
        // 由于我们按村民UUID存储任务，这里需要遍历查找与该玩家相关的任务
        // 实际上，当玩家离线时，任务会自动停止（因为owner为null）
        // 这里可以添加额外的清理逻辑（如果需要）
    }
}