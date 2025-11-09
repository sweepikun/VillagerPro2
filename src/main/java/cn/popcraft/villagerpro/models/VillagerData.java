package cn.popcraft.villagerpro.models;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillagerUpgradeManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;

import java.util.Map;
import java.util.UUID;

public class VillagerData {
    
    private int id;
    private int villageId;
    private UUID entityUUID;
    private String profession;
    private int level;
    private int experience;
    private String followMode;
    private Map<String, Integer> skills;
    
    /**
     * 构造函数
     * @param id 村民ID
     * @param villageId 村庄ID
     * @param entityUUID 实体UUID
     * @param profession 职业
     * @param level 等级
     * @param experience 经验值
     * @param followMode 跟随模式
     */
    public VillagerData(int id, int villageId, UUID entityUUID, String profession, int level, int experience, String followMode) {
        this.id = id;
        this.villageId = villageId;
        this.entityUUID = entityUUID;
        this.profession = profession;
        this.level = level;
        this.experience = experience;
        this.followMode = followMode;
        this.skills = null; // 延迟加载
    }
    
    // Getters and setters
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
    
    public UUID getEntityUUID() {
        return entityUUID;
    }
    
    public void setEntityUUID(UUID entityUUID) {
        this.entityUUID = entityUUID;
    }
    
    public String getProfession() {
        return profession;
    }
    
    public void setProfession(String profession) {
        this.profession = profession;
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
    
    public String getFollowMode() {
        return followMode;
    }
    
    public void setFollowMode(String followMode) {
        this.followMode = followMode;
    }
    
    /**
     * 获取村民实体
     * @return 村民实体
     */
    public Villager getEntity() {
        // 如果在主线程中直接获取实体
        if (Bukkit.isPrimaryThread()) {
            return (Villager) Bukkit.getEntity(entityUUID);
        } else {
            // 如果在异步线程中，通过调度器在主线程中获取实体
            // 这里我们返回null，让调用者处理异步情况
            return null;
        }
    }
    
    /**
     * 异步安全的方式获取村民实体
     * @param callback 回调函数，参数为村民实体（可能为null）
     */
    public void getEntityAsync(java.util.function.Consumer<Villager> callback) {
        if (Bukkit.isPrimaryThread()) {
            callback.accept((Villager) Bukkit.getEntity(entityUUID));
        } else {
            Bukkit.getScheduler().runTask(VillagerPro.getInstance(), () -> {
                callback.accept((Villager) Bukkit.getEntity(entityUUID));
            });
        }
    }
    
    /**
     * 获取村民位置
     * @return 村民位置，如果村民不存在则返回null
     */
    public Location getLocation() {
        // 由于可能在异步线程中调用，需要特殊处理
        if (Bukkit.isPrimaryThread()) {
            Villager villager = getEntity();
            return villager != null ? villager.getLocation() : null;
        } else {
            // 异步情况下返回null，让调用者处理
            return null;
        }
    }
    
    /**
     * 获取村民所在世界
     * @return 村民所在世界，如果村民不存在则返回null
     */
    public World getWorld() {
        // 由于可能在异步线程中调用，需要特殊处理
        if (Bukkit.isPrimaryThread()) {
            Villager villager = getEntity();
            return villager != null ? villager.getWorld() : null;
        } else {
            // 异步情况下返回null，让调用者处理
            return null;
        }
    }
    
    /**
     * 增加经验
     * @param exp 经验值
     */
    public void addExperience(int exp) {
        this.experience += exp;
    }
    
    /**
     * 获取工作范围
     * @return 工作范围
     */
    public int getWorkRange() {
        // 基础工作范围
        int baseRange = VillagerPro.getInstance().getConfig().getInt("villager.work_range", 5);
        
        // 检查是否有广域耕作技能（仅对农民有效）
        if ("farmer".equals(profession)) {
            Map<String, Integer> villagerSkills = getSkills();
            Integer wideRangeLevel = villagerSkills.get("wide_range");
            if (wideRangeLevel != null) {
                baseRange += wideRangeLevel * 2; // 每级广域耕作增加2格工作范围
            }
        }
        
        return baseRange;
    }
    
    /**
     * 获取基础产出数量
     * @return 基础产出数量
     */
    public int getBaseProductionAmount() {
        // 基础产出数量
        int baseAmount = VillagerPro.getInstance().getConfig().getInt("villager.professions." + profession + ".base_amount", 1);
        
        // 检查是否有高效收割技能（仅对农民有效）
        if ("farmer".equals(profession)) {
            Map<String, Integer> villagerSkills = getSkills();
            Integer efficientHarvestLevel = villagerSkills.get("efficient_harvest");
            if (efficientHarvestLevel != null) {
                baseAmount += efficientHarvestLevel; // 每级高效收割增加1个产出
            }
        }
        
        return baseAmount;
    }
    
    /**
     * 实现从数据库获取村民技能升级信息
     * @return 技能信息映射
     */
    public Map<String, Integer> getSkills() {
        if (skills == null) {
            skills = VillagerUpgradeManager.getVillagerUpgrades(id);
        }
        return skills;
    }
    
    /**
     * 检查是否升级
     * @return 是否可以升级
     */
    public boolean canUpgrade() {
        // 村民等级没有硬性上限，但可以检查是否满足升级条件
        // 根据经验系统来判断是否可以升级
        int currentLevel = this.getLevel();
        
        // 获取基础经验需求（默认为100）
        int baseExp = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                .getInt("villager.base_exp_per_level", 100);
        // 计算当前等级升级所需经验（线性增长）
        int expNeeded = currentLevel * baseExp;
        
        return this.getExperience() >= expNeeded;
    }
    
    @Override
    public String toString() {
        return "VillagerData{" +
                "id=" + id +
                ", villageId=" + villageId +
                ", entityUUID=" + entityUUID +
                ", profession='" + profession + '\'' +
                ", level=" + level +
                ", experience=" + experience +
                ", followMode='" + followMode + '\'' +
                '}';
    }
}