package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
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
        int currentLevel = getVillagerSkillLevel(villager.getId(), skillId);
        int maxLevel = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                .getInt("villager_upgrades." + villager.getProfession() + "." + skillId + ".max_level", 1);
        
        // 检查是否已达最高等级
        if (currentLevel >= maxLevel) {
            return false;
        }
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO villager_upgrades (villager_id, skill_id, level) VALUES (?, ?, ?)")) {
            
            statement.setInt(1, villager.getId());
            statement.setString(2, skillId);
            statement.setInt(3, currentLevel + 1);
            
            return statement.executeUpdate() > 0;
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
        List<CostEntry> costs = getUpgradeCosts(profession, skillId);
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
        List<CostEntry> costs = getUpgradeCosts(profession, skillId);
        return CostHandler.deduct(player, costs);
    }
    
    /**
     * 获取技能升级费用
     * @param profession 职业
     * @param skillId 技能ID
     * @return 费用列表
     */
    public static List<CostEntry> getUpgradeCosts(String profession, String skillId) {
        List<CostEntry> costs = new ArrayList<>();
        
        String path = "villager_upgrades." + profession + "." + skillId;
        if (cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path)) {
            // 检查是否有成本乘数
            double costMultiplier = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                    .getDouble(path + ".cost_multiplier", 1.0);
            
            // 获取技能等级
            int level = 1; // 默认等级为1
            
            if (cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path + ".costs")) {
                // 从配置中读取成本列表
                for (Map<?, ?> costEntry : cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getMapList(path + ".costs")) {
                    String type = (String) costEntry.get("type");
                    Number amountObj = (Number) costEntry.get("amount");
                    double baseAmount = amountObj != null ? amountObj.doubleValue() : 0;
                    // 应用成本乘数
                    double amount = baseAmount * Math.pow(costMultiplier, level - 1);
                    
                    if ("itemsadder".equals(type)) {
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
}