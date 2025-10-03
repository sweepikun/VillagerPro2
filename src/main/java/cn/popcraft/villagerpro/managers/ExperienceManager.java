package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ExperienceManager {
    
    /**
     * 村民获得经验
     * @param villager 村民
     * @param exp 经验值
     */
    public static void addVillagerExperience(VillagerData villager, int exp) {
        villager.addExperience(exp);
        VillagerManager.updateVillager(villager);
        
        // 检查是否升级
        checkVillagerLevelUp(villager);
    }
    
    /**
     * 村庄获得经验
     * @param village 村庄
     * @param exp 经验值
     */
    public static void addVillageExperience(Village village, int exp) {
        village.addExperience(exp);
        VillageManager.updateVillage(village);
        
        // 检查是否升级
        checkVillageLevelUp(village);
    }
    
    /**
     * 检查村民是否升级
     * @param villager 村民
     */
    private static void checkVillagerLevelUp(VillagerData villager) {
        // 简化的升级逻辑，实际应该根据配置来确定升级所需经验
        int currentLevel = villager.getLevel();
        int expNeeded = currentLevel * 100; // 每级需要的经验值递增
        
        if (villager.getExperience() >= expNeeded) {
            villager.setLevel(currentLevel + 1);
            villager.setExperience(villager.getExperience() - expNeeded);
            VillagerManager.updateVillager(villager);
            
            // 发送升级消息给村庄拥有者
            sendVillagerLevelUpMessage(villager);
        }
    }
    
    /**
     * 检查村庄是否升级
     * @param village 村庄
     */
    private static void checkVillageLevelUp(Village village) {
        // 简化的升级逻辑，实际应该根据配置来确定升级所需经验
        int currentLevel = village.getLevel();
        int maxLevel = VillagerPro.getInstance().getConfig().getInt("village.max_level", 5);
        
        if (currentLevel >= maxLevel) {
            return; // 已达到最高等级
        }
        
        int expNeeded = currentLevel * 200; // 每级需要的经验值递增
        
        if (village.getExperience() >= expNeeded) {
            village.setLevel(currentLevel + 1);
            village.setExperience(village.getExperience() - expNeeded);
            VillageManager.updateVillage(village);
            
            // 发送升级消息给村庄拥有者
            sendVillageLevelUpMessage(village);
        }
    }
    
    /**
     * 发送村民升级消息给村庄拥有者
     * @param villager 村民
     */
    private static void sendVillagerLevelUpMessage(VillagerData villager) {
        // 使用村庄ID获取拥有者UUID
        Village village = VillageManager.getVillageById(villager.getVillageId());
        if (village != null) {
            Player owner = Bukkit.getPlayer(village.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                String professionName = VillagerManager.getProfessionDisplayName(villager.getProfession());
                String message = Messages.getMessage("villager.level_up", 
                    "profession", professionName,
                    "level", String.valueOf(villager.getLevel()));
                owner.sendMessage(message);
            }
        }
    }
    
    /**
     * 发送村庄升级消息给村庄拥有者
     * @param village 村庄
     */
    private static void sendVillageLevelUpMessage(Village village) {
        Player owner = Bukkit.getPlayer(village.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            String message = Messages.getMessage("village.level_up", 
                "name", village.getName(),
                "level", String.valueOf(village.getLevel()));
            owner.sendMessage(message);
        }
    }
}