package cn.popcraft.villagerpro.models;

import cn.popcraft.villagerpro.VillagerPro;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * 访客数据模型
 * 表示村庄中的临时访客（商人、旅行者、节日使者）
 */
public class VisitorData {
    
    private int id;
    private int villageId;
    private String type; // 'merchant', 'traveler', 'festival'
    private Location location;
    private String name;
    private String displayName;
    private Timestamp spawnedAt;
    private Timestamp expiresAt;
    private boolean active;
    private LivingEntity entity;
    private UUID entityUUID;
    private String customData; // 额外的自定义数据（JSON格式）
    
    public VisitorData(int id, int villageId, String type, Location location, 
                      String name, String displayName, Timestamp spawnedAt, 
                      Timestamp expiresAt, String customData) {
        this.id = id;
        this.villageId = villageId;
        this.type = type;
        this.location = location;
        this.name = name;
        this.displayName = displayName;
        this.spawnedAt = spawnedAt;
        this.expiresAt = expiresAt;
        this.active = true;
        this.customData = customData;
    }
    
    /**
     * 生成访客实体
     */
    public boolean spawnEntity() {
        try {
            World world = location.getWorld();
            if (world == null) return false;
            
            // 在位置生成村民实体
            entity = (LivingEntity) world.spawnEntity(location, EntityType.VILLAGER);
            entityUUID = entity.getUniqueId();
            
            // 设置村民名称和交易
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                villager.setCustomName(displayName);
                villager.setCustomNameVisible(true);
                villager.setCanPickupItems(false);
                villager.setCollidable(false);
                villager.setInvulnerable(true);
                
                // 设置职业（用于不同的外观）
                setVillagerProfession(villager);
            }
            
            return true;
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().warning("生成访客实体失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 设置村民职业以显示不同的外观
     */
    private void setVillagerProfession(Villager villager) {
        try {
            // 使用通用的职业类型，避免不同版本API的差异
            villager.setVillagerType(Villager.Type.PLAINS);
        } catch (Exception e) {
            // 如果API调用失败，忽略错误
            VillagerPro.getInstance().getLogger().warning("设置访客职业失败: " + e.getMessage());
        }
    }
    
    /**
     * 移除访客实体
     */
    public void removeEntity() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        this.entity = null;
        this.active = false;
    }
    
    /**
     * 检查访客是否过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt.getTime() || !active;
    }
    
    /**
     * 获取剩余时间（秒）
     */
    public long getRemainingTimeInSeconds() {
        return Math.max(0, (expiresAt.getTime() - System.currentTimeMillis()) / 1000);
    }
    
    /**
     * 创建自动清理任务
     */
    public void createCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isExpired()) {
                    removeEntity();
                    this.cancel();
                }
            }
        }.runTaskTimer(VillagerPro.getInstance(), 1200L, 200L); // 每10秒检查一次
    }
    
    /**
     * 向玩家显示访客信息
     */
    public void showInfo(Player player) {
        String message = String.format("§e[访客] §f%s - %s", name, getTypeDisplayName());
        player.sendMessage(message);
        
        long remainingMinutes = getRemainingTimeInSeconds() / 60;
        player.sendMessage(String.format("§7剩余时间: §e%d §7分钟", remainingMinutes));
    }
    
    /**
     * 获取访客类型的中文显示名称
     */
    private String getTypeDisplayName() {
        switch (type.toLowerCase()) {
            case "merchant": return "流浪商人";
            case "traveler": return "旅行者";
            case "festival": return "节日使者";
            default: return "未知访客";
        }
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getVillageId() {
        return villageId;
    }
    
    public void setVillageId(int villageId) {
        this.villageId = villageId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public void setLocation(Location location) {
        this.location = location;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public Timestamp getSpawnedAt() {
        return spawnedAt;
    }
    
    public void setSpawnedAt(Timestamp spawnedAt) {
        this.spawnedAt = spawnedAt;
    }
    
    public Timestamp getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public LivingEntity getEntity() {
        return entity;
    }
    
    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }
    
    public UUID getEntityUUID() {
        return entityUUID;
    }
    
    public void setEntityUUID(UUID entityUUID) {
        this.entityUUID = entityUUID;
    }
    
    public String getCustomData() {
        return customData;
    }
    
    public void setCustomData(String customData) {
        this.customData = customData;
    }
    
    @Override
    public String toString() {
        return "VisitorData{" +
                "id=" + id +
                ", villageId=" + villageId +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                '}';
    }
}