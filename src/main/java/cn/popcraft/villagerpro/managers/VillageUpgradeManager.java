package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class VillageUpgradeManager {
    
    /**
     * 获取村庄已有的升级
     * @param villageId 村庄ID
     * @return 升级映射（升级ID -> 等级）
     */
    public static Map<String, Integer> getVillageUpgrades(int villageId) {
        Map<String, Integer> upgrades = new HashMap<>();
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT upgrade_id, level FROM village_upgrades WHERE village_id = ?")) {
            
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                upgrades.put(resultSet.getString("upgrade_id"), resultSet.getInt("level"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return upgrades;
    }
    
    /**
     * 获取村庄特定升级的等级
     * @param villageId 村庄ID
     * @param upgradeId 升级ID
     * @return 升级等级
     */
    public static int getVillageUpgradeLevel(int villageId, String upgradeId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT level FROM village_upgrades WHERE village_id = ? AND upgrade_id = ?")) {
            
            statement.setInt(1, villageId);
            statement.setString(2, upgradeId);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return resultSet.getInt("level");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return 0;
    }
    
    /**
     * 应用村庄升级
     * @param village 村庄
     * @param upgradeId 升级ID
     * @return 是否应用成功
     */
    public static boolean applyVillageUpgrade(Village village, String upgradeId) {
        int currentLevel = getVillageUpgradeLevel(village.getId(), upgradeId);
        int maxLevel = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                .getInt("village_upgrades.available_upgrades." + upgradeId + ".max_level", 1);
        
        // 检查是否已达最高等级
        if (currentLevel >= maxLevel) {
            return false;
        }
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO village_upgrades (village_id, upgrade_id, level) VALUES (?, ?, ?)")) {
            
            statement.setInt(1, village.getId());
            statement.setString(2, upgradeId);
            statement.setInt(3, currentLevel + 1);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取随机升级选项
     * @param village 村庄
     * @param count 选项数量
     * @return 升级选项列表
     */
    public static List<String> getRandomUpgradeOptions(Village village, int count) {
        List<String> allUpgrades = new ArrayList<>();
        List<String> availableUpgrades = new ArrayList<>();
        
        FileConfiguration config = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig();
        ConfigurationSection section = config.getConfigurationSection("village_upgrades.available_upgrades");
        if (section != null) {
            allUpgrades.addAll(section.getKeys(false));
        }
        
        // 筛选出未达最高等级的升级
        for (String upgradeId : allUpgrades) {
            int currentLevel = getVillageUpgradeLevel(village.getId(), upgradeId);
            int maxLevel = getUpgradeMaxLevel(upgradeId);
            if (currentLevel < maxLevel) {
                availableUpgrades.add(upgradeId);
            }
        }
        
        // 随机选择指定数量的升级
        List<String> options = new ArrayList<>();
        Random random = new Random();
        int selectCount = Math.min(count, availableUpgrades.size());
        
        while (options.size() < selectCount && !availableUpgrades.isEmpty()) {
            int index = random.nextInt(availableUpgrades.size());
            options.add(availableUpgrades.remove(index));
        }
        
        return options;
    }
    
    /**
     * 检查玩家是否能支付升级费用
     * @param player 玩家
     * @param upgradeId 升级ID
     * @return 是否能支付
     */
    public static boolean canAffordUpgrade(Player player, String upgradeId) {
        List<CostEntry> costs = getUpgradeCosts(upgradeId);
        return CostHandler.canAfford(player, costs);
    }
    
    /**
     * 支付升级费用
     * @param player 玩家
     * @param upgradeId 升级ID
     * @return 是否支付成功
     */
    public static boolean payUpgradeCost(Player player, String upgradeId) {
        List<CostEntry> costs = getUpgradeCosts(upgradeId);
        return CostHandler.deduct(player, costs);
    }
    
    /**
     * 获取升级费用
     * @param upgradeId 升级ID
     * @return 费用列表
     */
    public static List<CostEntry> getUpgradeCosts(String upgradeId) {
        List<CostEntry> costs = new ArrayList<>();
        
        String path = "village_upgrades.available_upgrades." + upgradeId + ".costs";
        if (cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path)) {
            // 从配置中读取成本列表
            for (Map<?, ?> costEntry : cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getMapList(path)) {
                String type = (String) costEntry.get("type");
                Number amountObj = (Number) costEntry.get("amount");
                double amount = amountObj != null ? amountObj.doubleValue() : 0;
                
                if ("itemsadder".equals(type)) {
                    String item = (String) costEntry.get("item");
                    costs.add(new CostEntry(type, amount, item));
                } else {
                    costs.add(new CostEntry(type, amount));
                }
            }
        }
        
        return costs;
    }
    
    /**
     * 获取升级显示名称
     * @param upgradeId 升级ID
     * @return 显示名称
     */
    public static String getUpgradeDisplayName(String upgradeId) {
        String path = "village_upgrades.available_upgrades." + upgradeId + ".name";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getString(path, upgradeId);
    }
    
    /**
     * 获取升级描述
     * @param upgradeId 升级ID
     * @return 描述
     */
    public static String getUpgradeDescription(String upgradeId) {
        String path = "village_upgrades.available_upgrades." + upgradeId + ".description";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getString(path, "");
    }
    
    /**
     * 获取升级图标
     * @param upgradeId 升级ID
     * @return 图标材料名称
     */
    public static String getUpgradeIcon(String upgradeId) {
        String path = "village_upgrades.available_upgrades." + upgradeId + ".icon";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getString(path, "STONE");
    }
    
    /**
     * 获取升级最大等级
     * @param upgradeId 升级ID
     * @return 最大等级
     */
    public static int getUpgradeMaxLevel(String upgradeId) {
        String path = "village_upgrades.available_upgrades." + upgradeId + ".max_level";
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getInt(path, 1);
    }
}