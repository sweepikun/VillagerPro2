package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.GameplayMath;
import cn.popcraft.villagerpro.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExperienceManager {
    
    /**
     * 村民获得经验
     * @param villager 村民
     * @param exp 经验值
     */
    public static void addVillagerExperience(VillagerData villager, int exp) {
        int previousLevel = villager.getLevel();
        int previousExperience = villager.getExperience();
        int baseExp = VillagerPro.getInstance().getConfig()
                .getInt("villager.base_exp_per_level", 100);
        GameplayMath.LevelProgress progress = GameplayMath.applyExperience(
                previousLevel, villager.getExperience(), exp, baseExp, Integer.MAX_VALUE);
        villager.setLevel(progress.level());
        villager.setExperience(progress.experience());
        if (!VillagerManager.updateVillager(villager)) {
            villager.setLevel(previousLevel);
            villager.setExperience(previousExperience);
            return;
        }
        if (progress.level() > previousLevel) sendVillagerLevelUpMessage(villager);
    }
    
    /**
     * 村庄获得经验
     * @param village 村庄
     * @param exp 经验值
     */
    public static void addVillageExperience(Village village, int exp) {
        int previousExperience = village.getExperience();
        village.addExperience(exp);
        if (!VillageManager.updateVillage(village)) {
            village.setExperience(previousExperience);
            return;
        }
        
        // 检查是否升级
        checkVillageLevelUp(village);
    }
    
    /**
     * 检查村庄是否升级
     * @param village 村庄
     */
    public static void checkVillageLevelUp(Village village) {
        int maxLevel = VillagerPro.getInstance().getConfig().getInt("village.max_level", 5);
        int baseExp = VillagerPro.getInstance().getConfig().getInt("village.base_exp_per_level", 200);
        while (village.getLevel() < maxLevel) {
            int currentLevel = village.getLevel();
            long expNeeded = (long) currentLevel * Math.max(1, baseExp);
            if (village.getExperience() < expNeeded) return;
            Player owner = Bukkit.getPlayer(village.getOwnerUUID());
            if (owner == null) return;
            List<CostEntry> costs = getVillageLevelUpCosts(currentLevel);
            if (!CostHandler.deduct(owner, costs)) return;

            int previousExperience = village.getExperience();
            village.setLevel(currentLevel + 1);
            village.setExperience((int) (village.getExperience() - expNeeded));
            if (!VillageManager.updateVillage(village)) {
                village.setLevel(currentLevel);
                village.setExperience(previousExperience);
                if (!CostHandler.refund(owner, costs)) {
                    owner.sendMessage("§c村庄升级保存失败且费用未完整退还，请联系管理员");
                }
                return;
            }

            sendVillageLevelUpMessage(village);
            if (PersonalityManager.isEnabled()) {
                PersonalityManager.getInstance().rewardVillageLevelUp(village);
            }
        }
    }

    private static List<CostEntry> getVillageLevelUpCosts(int currentLevel) {
        List<CostEntry> costs = new ArrayList<>();
        List<Map<?, ?>> levels = VillagerPro.getInstance().getConfig().getMapList("village.upgrade_costs");
        int index = currentLevel - 1;
        if (index < 0 || index >= levels.size()) {
            return costs;
        }

        Object entriesObject = levels.get(index).get("costs");
        if (!(entriesObject instanceof List<?>)) {
            return costs;
        }
        for (Object entryObject : (List<?>) entriesObject) {
            if (!(entryObject instanceof Map<?, ?>)) continue;
            Map<?, ?> entry = (Map<?, ?>) entryObject;
            String type = String.valueOf(entry.get("type"));
            Object amountObject = entry.get("amount");
            if (!(amountObject instanceof Number)) continue;
            double amount = ((Number) amountObject).doubleValue();
            if ("itemsadder".equalsIgnoreCase(type) || "item".equalsIgnoreCase(type)) {
                costs.add(new CostEntry(type, amount, String.valueOf(entry.get("item"))));
            } else {
                costs.add(new CostEntry(type, amount));
            }
        }
        return costs;
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
