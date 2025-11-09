package cn.popcraft.villagerpro.models;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;

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
    private Map<String, Integer> upgrades;
    
    /**
     * 构造函数
     * @param id 村庄ID
     * @param ownerUUID 所有者UUID
     * @param name 村庄名称
     * @param level 等级
     * @param experience 经验值
     * @param prosperity 繁荣度
     */
    public Village(int id, UUID ownerUUID, String name, int level, int experience, int prosperity) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.name = name;
        this.level = level;
        this.experience = experience;
        this.prosperity = prosperity;
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
            levelBonus += capacityUpgrade; // 每级基建扩张增加1个村民上限
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
            levelBonus += warehouseUpgrade * 50; // 每级仓储扩容增加50容量
        }
        
        return baseCapacity + levelBonus;
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
        // 检查是否可以升级
        if (canUpgrade()) {
            int currentLevel = this.level;
            int requiredExp = VillagerPro.getInstance().getConfig().getInt("village.level_up_experience." + (currentLevel + 1), 100);
            if (this.experience >= requiredExp) {
                this.level++;
                // 可以在这里添加升级事件通知等逻辑
            }
        }
    }
    
    /**
     * 增加繁荣度
     * @param prosperity 繁荣度
     */
    public void addProsperity(int prosperity) {
        this.prosperity += prosperity;
    }
}