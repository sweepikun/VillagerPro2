package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillagerUpgradeManager {
    
    /**
     * 获取村民已有的技能升级
     * @param villagerId 村民ID
     * @return 技能映射（技能ID -> 等级）
     */
    public static Map<String, Integer> getVillagerUpgrades(int villagerId) {
        Map<String, Integer> upgrades = new HashMap<>();
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT skill_id, level FROM villager_upgrades WHERE villager_id = ?")) {
            
            statement.setInt(1, villagerId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                upgrades.put(resultSet.getString("skill_id"), resultSet.getInt("level"));
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return upgrades;
    }
    
    /**
     * 获取村民特定技能的等级
     * @param villagerId 村民ID
     * @param skillId 技能ID
     * @return 技能等级
     */
    public static int getVillagerSkillLevel(int villagerId, String skillId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT level FROM villager_upgrades WHERE villager_id = ? AND skill_id = ?")) {
            
            statement.setInt(1, villagerId);
            statement.setString(2, skillId);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return resultSet.getInt("level");
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * 应用村民技能升级
     * @param villager 村民
     * @param skillId 技能ID
     * @return 是否应用成功
     */
    public static boolean applyVillagerUpgrade(VillagerData villager, String skillId) {
        return applyVillagerUpgrade(villager, skillId,
                getVillagerSkillLevel(villager.getId(), skillId));
    }

    public static boolean applyVillagerUpgrade(VillagerData villager, String skillId,
                                               int expectedLevel) {
        int maxLevel = getSkillMaxLevel(villager.getProfession(), skillId);
        try (Connection connection = DatabaseManager.getConnection()) {
            return OperationTransactions.upgradeVillagerSkill(connection,
                    DatabaseManager.getDialect(), villager.getId(), skillId,
                    expectedLevel, maxLevel);
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查玩家是否能支付技能升级费用
     * @param player 玩家
     * @param profession 职业
     * @param skillId 技能ID
     * @return 是否能支付
     */
    public static boolean canAffordUpgrade(Player player, String profession, String skillId) {
        return canAffordUpgrade(player, profession, skillId, 1);
    }
    
    /**
     * 检查玩家是否能支付技能升级费用（指定下一级等级）
     * @param player 玩家
     * @param profession 职业
     * @param skillId 技能ID
     * @param nextLevel 要升的等级（通常为 currentLevel + 1）
     * @return 是否能支付
     */
    public static boolean canAffordUpgrade(Player player, String profession, String skillId, int nextLevel) {
        List<CostEntry> costs = getUpgradeCosts(profession, skillId, nextLevel);
        return CostHandler.canAfford(player, costs);
    }
    
    /**
     * 支付技能升级费用
     * @param player 玩家
     * @param profession 职业
     * @param skillId 技能ID
     * @return 是否支付成功
     */
    public static boolean payUpgradeCost(Player player, String profession, String skillId) {
        return payUpgradeCost(player, profession, skillId, 1);
    }
    
    /**
     * 支付技能升级费用（指定下一级等级）
     * @param player 玩家
     * @param profession 职业
     * @param skillId 技能ID
     * @param nextLevel 要升的等级（通常为 currentLevel + 1）
     * @return 是否支付成功
     */
    public static boolean payUpgradeCost(Player player, String profession, String skillId, int nextLevel) {
        List<CostEntry> costs = getUpgradeCosts(profession, skillId, nextLevel);
        return CostHandler.deduct(player, costs);
    }
    
    /**
     * 获取技能升级费用
     * @param profession 职业
     * @param skillId 技能ID
     * @return 费用列表
     */
    public static List<CostEntry> getUpgradeCosts(String profession, String skillId) {
        return getUpgradeCosts(profession, skillId, 1);
    }
    
    /**
     * 获取技能升级费用（指定下一级等级）
     * @param profession 职业
     * @param skillId 技能ID
     * @param nextLevel 要升的等级（通常为 currentLevel + 1）
     * @return 费用列表
     */
    public static List<CostEntry> getUpgradeCosts(String profession, String skillId, int nextLevel) {
        List<CostEntry> costs = new ArrayList<>();
        
        String path = "villager_upgrades." + profession + "." + skillId;
        if (cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path)) {
            // 检查是否有成本乘数
            double costMultiplier = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                    .getDouble(path + ".cost_multiplier", 1.0);
            
            if (cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path + ".costs")) {
                // 从配置中读取成本列表
                for (Map<?, ?> costEntry : cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getMapList(path + ".costs")) {
                    String type = (String) costEntry.get("type");
                    Number amountObj = (Number) costEntry.get("amount");
                    double baseAmount = amountObj != null ? amountObj.doubleValue() : 0;
                    // 应用成本乘数
                    double amount = baseAmount * Math.pow(costMultiplier, nextLevel - 1);
                    
                    if ("itemsadder".equals(type) || "item".equals(type)) {
                        String item = (String) costEntry.get("item");
                        costs.add(new CostEntry(type, amount, item));
                    } else {
                        costs.add(new CostEntry(type, amount));
                    }
                }
            }
        }
        
        return costs;
    }
    
    /**
     * 获取技能显示名称
     * @param profession 职业
     * @param skillId 技能ID
     * @return 显示名称
     */
    public static String getSkillDisplayName(String profession, String skillId) {
        String path = "villager_upgrades." + profession + "." + skillId + ".name";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getString(path, skillId);
    }
    
    /**
     * 获取技能描述
     * @param profession 职业
     * @param skillId 技能ID
     * @return 描述
     */
    public static String getSkillDescription(String profession, String skillId) {
        String path = "villager_upgrades." + profession + "." + skillId + ".description";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getString(path, "");
    }
    
    /**
     * 获取技能图标
     * @param profession 职业
     * @param skillId 技能ID
     * @return 图标材料名称
     */
    public static String getSkillIcon(String profession, String skillId) {
        String path = "villager_upgrades." + profession + "." + skillId + ".icon";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getString(path, "STONE");
    }
    
    /**
     * 获取技能最大等级
     * @param profession 职业
     * @param skillId 技能ID
     * @return 最大等级
     */
    public static int getSkillMaxLevel(String profession, String skillId) {
        String path = "villager_upgrades." + profession + "." + skillId + ".max_level";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getInt(path, 1);
    }

    public static int getIntEffect(String profession, String skillId, String effectId,
                                   int defaultValue) {
        String path = "villager_upgrades." + profession + "." + skillId
                + ".effects." + effectId;
        return VillagerPro.getInstance().getConfig().getInt(path, defaultValue);
    }

    public static double getDoubleEffect(String profession, String skillId, String effectId,
                                         double defaultValue) {
        String path = "villager_upgrades." + profession + "." + skillId
                + ".effects." + effectId;
        return VillagerPro.getInstance().getConfig().getDouble(path, defaultValue);
    }

    public static int getProductionSkillBonus(VillagerData villager) {
        Map<String, Integer> skills = villager.getSkills();
        String profession = villager.getProfession();
        return switch (profession) {
            case "farmer" -> skills.getOrDefault("efficient_harvest", 0)
                    * getIntEffect(profession, "efficient_harvest", "amount_per_level", 1);
            case "shepherd" -> skills.getOrDefault("efficient_shearing", 0)
                    * getIntEffect(profession, "efficient_shearing", "amount_per_level", 1);
            case "priest" -> skills.getOrDefault("potion_master", 0)
                    * getIntEffect(profession, "potion_master", "amount_per_level", 1);
            case "cartographer" -> skills.getOrDefault("map_expert", 0)
                    * getIntEffect(profession, "map_expert", "amount_per_level", 1);
            case "miner" -> skills.getOrDefault("quick_mining", 0);
            default -> 0;
        };
    }
}
