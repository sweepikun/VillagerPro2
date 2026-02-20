package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.WarehouseItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WarehouseManager {
    
    /**
     * 获取村庄仓库中的所有物品
     * @param villageId 村庄ID
     * @return 仓库物品列表
     */
    public static List<WarehouseItem> getWarehouseItems(int villageId) {
        List<WarehouseItem> items = new ArrayList<>();
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, item_type, amount FROM warehouse WHERE village_id = ?")) {
            
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                items.add(new WarehouseItem(
                        resultSet.getInt("id"),
                        resultSet.getInt("village_id"),
                        resultSet.getString("item_type"),
                        resultSet.getInt("amount")
                ));
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return items;
    }
    
    /**
     * 获取村庄仓库中指定类型的物品
     * @param villageId 村庄ID
     * @param itemType 物品类型
     * @return 仓库物品，如果不存在则返回null
     */
    public static WarehouseItem getWarehouseItem(int villageId, String itemType) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, item_type, amount FROM warehouse WHERE village_id = ? AND item_type = ?")) {
            
            statement.setInt(1, villageId);
            statement.setString(2, itemType);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return new WarehouseItem(
                        resultSet.getInt("id"),
                        resultSet.getInt("village_id"),
                        resultSet.getString("item_type"),
                        resultSet.getInt("amount")
                );
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 向仓库添加物品
     * @param villageId 村庄ID
     * @param itemType 物品类型
     * @param amount 数量
     * @return 是否添加成功
     */
    public static boolean addWarehouseItem(int villageId, String itemType, int amount) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO warehouse (village_id, item_type, amount) VALUES (?, ?, " +
                             "COALESCE((SELECT amount FROM warehouse WHERE village_id = ? AND item_type = ?), 0) + ?)")) {
            
            statement.setInt(1, villageId);
            statement.setString(2, itemType);
            statement.setInt(3, villageId);
            statement.setString(4, itemType);
            statement.setInt(5, amount);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 从仓库移除物品
     * @param villageId 村庄ID
     * @param itemType 物品类型
     * @param amount 数量
     * @return 是否移除成功
     */
    public static boolean removeWarehouseItem(int villageId, String itemType, int amount) {
        // 先检查是否有足够的物品
        WarehouseItem item = getWarehouseItem(villageId, itemType);
        if (item == null || item.getAmount() < amount) {
            return false;
        }
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE warehouse SET amount = amount - ? WHERE village_id = ? AND item_type = ?")) {
            
            statement.setInt(1, amount);
            statement.setInt(2, villageId);
            statement.setString(3, itemType);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 玩家从仓库提取物品
     * @param player 玩家
     * @param villageId 村庄ID
     * @param itemType 物品类型
     * @param amount 提取数量
     * @return 是否提取成功
     */
    public static boolean extractItem(Player player, int villageId, String itemType, int amount) {
        // 检查仓库中是否有足够物品
        WarehouseItem item = getWarehouseItem(villageId, itemType);
        if (item == null || item.getAmount() < amount) {
            player.sendMessage("§c仓库中没有足够的" + itemType + "！");
            return false;
        }
        
        // 检查玩家背包是否有足够空间
        Material material = Material.getMaterial(itemType);
        if (material == null) {
            player.sendMessage("§c无效的物品类型: " + itemType);
            return false;
        }
        
        ItemStack itemStack = new ItemStack(material, amount);
        if (!player.getInventory().addItem(itemStack).isEmpty()) {
            player.sendMessage("§c背包空间不足！");
            return false;
        }
        
        // 从仓库中移除物品
        if (!removeWarehouseItem(villageId, itemType, amount)) {
            player.sendMessage("§c提取物品时发生错误！");
            // 如果移除失败，需要从玩家背包中移除刚刚添加的物品
            player.getInventory().removeItem(itemStack);
            return false;
        }
        
        player.sendMessage("§a成功提取了 " + amount + " 个 " + itemType);
        return true;
    }
    
    /**
     * 玩家从仓库提取所有指定类型的物品
     * @param player 玩家
     * @param villageId 村庄ID
     * @param itemType 物品类型
     * @return 是否提取成功
     */
    public static boolean extractAllItem(Player player, int villageId, String itemType) {
        // 获取仓库中该物品的数量
        WarehouseItem item = getWarehouseItem(villageId, itemType);
        if (item == null || item.getAmount() <= 0) {
            player.sendMessage("§c仓库中没有" + itemType + "！");
            return false;
        }
        
        return extractItem(player, villageId, itemType, item.getAmount());
    }
    
    /**
     * 获取仓库容量
     * @param village 村庄对象
     * @return 仓库容量
     */
    public static int getWarehouseCapacity(Village village) {
        // 基础容量 + 每级增加容量
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getInt("village.base_warehouse_capacity", 50) +
               (village.getLevel() - 1) * cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getInt("village.warehouse_capacity_per_level", 25);
    }
    
    /**
     * 获取仓库当前存储量
     * @param villageId 村庄ID
     * @return 当前存储量
     */
    public static int getCurrentStorage(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT SUM(amount) as total FROM warehouse WHERE village_id = ?")) {
            
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return resultSet.getInt("total");
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * 清空村庄仓库
     * @param villageId 村庄ID
     * @return 是否清空成功
     */
    public static boolean clearWarehouse(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM warehouse WHERE village_id = ?")) {
            
            statement.setInt(1, villageId);
            return statement.executeUpdate() >= 0; // 即使没有记录被删除也返回true
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
}