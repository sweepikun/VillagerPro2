package cn.popcraft.villagerpro.models;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.BuildingManager;
import cn.popcraft.villagerpro.managers.PolicyManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Village {
    
    private int id;
    private UUID ownerUUID;
    private String name;
    private int level;
    private int experience;
    private int prosperity;
    private double centerX;
    private double centerY;
    private double centerZ;
    private String world;
    private Map<String, Integer> upgrades;
    
    /**
     * 构造函数（兼容旧代码，不带位置）
     * @param id 村庄ID
     * @param ownerUUID 所有者UUID
     * @param name 村庄名称
     * @param level 等级
     * @param experience 经验值
     * @param prosperity 繁荣度
     */
    public Village(int id, UUID ownerUUID, String name, int level, int experience, int prosperity) {
        this(id, ownerUUID, name, level, experience, prosperity, 0, 0, 0, "");
    }
    
    /**
     * 构造函数（带村庄中心位置）
     * @param id 村庄ID
     * @param ownerUUID 所有者UUID
     * @param name 村庄名称
     * @param level 等级
     * @param experience 经验值
     * @param prosperity 繁荣度
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param centerZ 中心Z坐标
     * @param world 世界名
     */
    public Village(int id, UUID ownerUUID, String name, int level, int experience, int prosperity,
                   double centerX, double centerY, double centerZ, String world) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.name = name;
        this.level = level;
        this.experience = experience;
        this.prosperity = prosperity;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.world = world;
        this.upgrades = null; // 延迟加载
    }
    
    // Getters and setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public UUID getOwnerUUID() {
        return ownerUUID;
    }
    
    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public int getExperience() {
        return experience;
    }
    
    public void setExperience(int experience) {
        this.experience = experience;
    }
    
    public int getProsperity() {
        return prosperity;
    }
    
    public void setProsperity(int prosperity) {
        this.prosperity = prosperity;
    }
    
    public double getCenterX() {
        return centerX;
    }
    
    public void setCenterX(double centerX) {
        this.centerX = centerX;
    }
    
    public double getCenterY() {
        return centerY;
    }
    
    public void setCenterY(double centerY) {
        this.centerY = centerY;
    }
    
    public double getCenterZ() {
        return centerZ;
    }
    
    public void setCenterZ(double centerZ) {
        this.centerZ = centerZ;
    }
    
    public String getWorld() {
        return world;
    }
    
    public void setWorld(String world) {
        this.world = world;
    }
    
    /**
     * 获取村庄中心位置
     * @return Bukkit Location，如果世界不存在则返回 null
     */
    public org.bukkit.Location getLocation() {
        org.bukkit.World bukkitWorld = VillagerPro.getInstance().getServer().getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new org.bukkit.Location(bukkitWorld, centerX, centerY, centerZ);
    }
    
    /**
     * 设置村庄中心位置
     * @param location 位置
     */
    public void setLocation(org.bukkit.Location location) {
        if (location != null && location.getWorld() != null) {
            this.centerX = location.getX();
            this.centerY = location.getY();
            this.centerZ = location.getZ();
            this.world = location.getWorld().getName();
        }
    }
    
    /**
     * 获取村民上限
     * @return 村民上限
     */
    public int getVillagerLimit() {
        // 基础限制 + 每级增加的数量
        int baseLimit = VillagerPro.getInstance().getConfig().getInt("village.base_villager_limit", 3);
        int levelBonus = (level - 1) * 1; // 每级增加1个村民上限
        
        // 检查是否有基建扩张升级
        Map<String, Integer> villageUpgrades = getUpgrades();
        Integer capacityUpgrade = villageUpgrades.get("villager_capacity");
        if (capacityUpgrade != null) {
            levelBonus += capacityUpgrade * VillageUpgradeManager.getIntEffect(
                    "villager_capacity", "capacity_per_level", 1);
        }
        
        return baseLimit + levelBonus;
    }
    
    /**
     * 获取仓库容量
     * @return 仓库容量
     */
    public int getWarehouseCapacity() {
        // 基础容量 + 每级增加容量
        int baseCapacity = VillagerPro.getInstance().getConfig().getInt("village.base_warehouse_capacity", 50);
        int levelBonus = (level - 1) * VillagerPro.getInstance().getConfig().getInt("village.warehouse_capacity_per_level", 25);
        
        // 检查是否有仓储扩容升级
        Map<String, Integer> villageUpgrades = getUpgrades();
        Integer warehouseUpgrade = villageUpgrades.get("warehouse_expansion");
        if (warehouseUpgrade != null) {
            levelBonus += warehouseUpgrade * VillageUpgradeManager.getIntEffect(
                    "warehouse_expansion", "capacity_per_level", 50);
        }
        
        return PolicyManager.applyWarehouseCapacity(id,
                baseCapacity + levelBonus + BuildingManager.getWarehouseCapacityBonus(id));
    }
    
    /**
     * 实现从数据库获取村庄升级信息
     * @return 升级信息映射
     */
    public Map<String, Integer> getUpgrades() {
        if (upgrades == null) {
            upgrades = VillageUpgradeManager.getVillageUpgrades(id);
        }
        return upgrades;
    }

    public void reloadUpgrades() {
        this.upgrades = VillageUpgradeManager.getVillageUpgrades(id);
    }
    
    /**
     * 检查是否升级
     * @return 是否可以升级
     */
    public boolean canUpgrade() {
        int maxLevel = VillagerPro.getInstance().getConfig().getInt("village.max_level", 5);
        return level < maxLevel;
    }
    
    @Override
    public String toString() {
        return "Village{" +
                "id=" + id +
                ", ownerUUID=" + ownerUUID +
                ", name='" + name + '\'' +
                ", level=" + level +
                ", experience=" + experience +
                ", prosperity=" + prosperity +
                '}';
    }
    
    /**
     * 增加经验
     * @param exp 经验值
     */
    public void addExperience(int exp) {
        this.experience += exp;
    }
    
    /**
     * 增加繁荣度
     * @param prosperity 繁荣度
     */
    public void addProsperity(int prosperity) {
        this.prosperity += prosperity;
    }
}
