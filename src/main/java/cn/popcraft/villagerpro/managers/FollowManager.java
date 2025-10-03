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
        VillagerManager.updateVillager(villagerData);
        
        // 更新村民的自定义名称以显示跟随模式
        Villager villager = villagerData.getEntity();
        if (villager != null) {
            String professionName = VillagerManager.getProfessionDisplayName(villagerData.getProfession());
            villager.setCustomName("§a" + professionName + " §7[" + newMode + "]");
            villager.setCustomNameVisible(true);
        }
    }
    
    /**
     * 开始跟随任务
     * @param villagerData 村民数据
     */
    public static void startFollowing(VillagerData villagerData) {
        // 如果已有跟随任务，先停止
        stopFollowing(villagerData);
        
        // 创建新的跟随任务
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(VillagerPro.getInstance(), () -> {
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
            
            if (!villagerLocation.getWorld().equals(playerLocation.getWorld())) {
                return;
            }
            
            double distance = villagerLocation.distance(playerLocation);
            
            // 根据跟随模式执行相应操作
            switch (villagerData.getFollowMode()) {
                case "FOLLOW":
                    if (distance > 3) {
                        // 使用导航系统而不是传送
                        villager.setTarget(owner);
                    }
                    break;
                case "STAY":
                    // 停留模式，不执行任何操作
                    break;
                case "FREE":
                default:
                    // 自由模式，不执行任何操作
                    break;
            }
        }, 0L, 20L); // 每秒执行一次
        
        followTasks.put(villagerData.getEntityUUID(), task);
    }
    
    /**
     * 停止跟随任务
     * @param villagerData 村民数据
     */
    public static void stopFollowing(VillagerData villagerData) {
        BukkitTask task = followTasks.get(villagerData.getEntityUUID());
        if (task != null) {
            task.cancel();
            followTasks.remove(villagerData.getEntityUUID());
        }
    }
    
    /**
     * 关闭所有跟随任务
     */
    public static void shutdown() {
        for (BukkitTask task : followTasks.values()) {
            task.cancel();
        }
        followTasks.clear();
    }
    
    /**
     * 检查村民是否有跟随任务
     * @param villagerData 村民数据
     * @return 是否有跟随任务
     */
    public static boolean isFollowing(VillagerData villagerData) {
        return followTasks.containsKey(villagerData.getEntityUUID());
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
}