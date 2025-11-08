package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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
                startFollowing(villagerData);
                break;
            default:
                newMode = "FREE";
                startFollowing(villagerData);
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
            
            // 检查村民与玩家的距离和位置
            Location villagerLocation = villager.getLocation();
            Location playerLocation = owner.getLocation();
            
            if (!villagerLocation.getWorld().equals(playerLocation.getWorld())) {
                return;
            }
            
            double distance = villagerLocation.distance(playerLocation);
            
            // 根据跟随模式执行相应操作
            switch (villagerData.getFollowMode()) {
                case "FOLLOW":
                    handleNaturalFollow(villager, owner, distance);
                    break;
                case "STAY":
                    // 停留模式，不执行任何操作
                    break;
                case "FREE":
                    // 自由模式，保持轻微跟随但不强制
                    if (distance > 8) {
                        handleNaturalFollow(villager, owner, distance);
                    }
                    break;
            }
        }, 0L, 10L); // 每半秒执行一次，更频繁的检查
        
        followTasks.put(villagerData.getEntityUUID(), task);
    }
    
    /**
     * 处理自然跟随逻辑
     */
    private static void handleNaturalFollow(Villager villager, Player owner, double distance) {
        Location villagerLocation = villager.getLocation();
        Location playerLocation = owner.getLocation();
        
        // 理想跟随距离：2-3格
        double idealDistance = 2.5;
        
        if (distance > idealDistance) {
            // 村民需要更靠近玩家
            // 计算方向向量
            Vector direction = playerLocation.toVector().subtract(villagerLocation.toVector()).normalize();
            
            // 在玩家后方一点的位置创建目标位置
            Location targetLocation = playerLocation.clone().subtract(direction.multiply(idealDistance));
            
            // 确保目标位置是固体
            if (isSafeLocation(targetLocation)) {
                // 使用Pathfinder寻路而不是直接设置目标
                villager.getPathfinder().moveTo(targetLocation, 0.8);
                
                // 播放跟随音效和粒子效果
                if (Math.random() < 0.1) { // 10%概率播放效果
                    villagerLocation.getWorld().playSound(villagerLocation, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                    villagerLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, villagerLocation, 3, 0.5, 0.5, 0.5, 0.0);
                }
            }
        } else if (distance < 1.0) {
            // 村民太近了，停止移动
            villager.getPathfinder().stop();
        }
        
        // 让村民看起来更生动
        addVillagerAnimations(villager, distance);
    }
    
    /**
     * 添加村民动画效果
     */
    private static void addVillagerAnimations(Villager villager, double distance) {
        Location loc = villager.getLocation();
        
        // 随机抬头动画
        if (Math.random() < 0.05) {
            villager.setLookAtTarget(loc.getWorld().getPlayers().stream()
                .findAny()
                .orElse(null), 5.0f, 5.0f);
        }
        
        // 如果距离较远，显示跟随提示
        if (distance > 5.0 && Math.random() < 0.02) {
            loc.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, loc, 2, 0.3, 0.3, 0.3, 0.0);
        }
    }
    
    /**
     * 检查位置是否安全可行走
     */
    private static boolean isSafeLocation(Location location) {
        if (location.getBlock().getType().isSolid()) {
            return false;
        }
        
        // 检查下方是否为固体
        Location below = location.clone().subtract(0, 1, 0);
        if (!below.getBlock().getType().isSolid()) {
            return false;
        }
        
        return true;
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