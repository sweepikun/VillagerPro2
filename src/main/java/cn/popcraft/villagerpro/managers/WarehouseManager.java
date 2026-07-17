package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.WarehouseItem;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return storeWarehouseItem(villageId, itemType, amount) == amount;
    }

    /**
     * 尽可能存入物品，返回实际存入数量。仓库剩余空间不足时会部分存入。
     */
    public static int storeWarehouseItem(int villageId, String itemType, int amount) {
        if (amount <= 0) {
            return 0;
        }

        Village village = VillageManager.getVillageById(villageId);
        if (village == null || Material.getMaterial(itemType) == null) {
            return 0;
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            return OperationTransactions.storeWarehouseStock(connection,
                    DatabaseManager.getDialect(), villageId, itemType, amount,
                    village.getWarehouseCapacity());
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return 0;
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
        if (amount <= 0) {
            return false;
        }

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE warehouse SET amount = amount - ? " +
                             "WHERE village_id = ? AND item_type = ? AND amount >= ?")) {
            
            statement.setInt(1, amount);
            statement.setInt(2, villageId);
            statement.setString(3, itemType);
            statement.setInt(4, amount);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 原子扣除玩家可用库存，同时保护手动保留量和储备政策冻结比例。
     */
    public static boolean removeExtractableItem(int villageId, String itemType, int amount) {
        if (amount <= 0) return false;
        int configuredReserve = WarehouseRuleManager.getItemRule(
                villageId, itemType).getReserveAmount();
        double frozenFraction = PolicyManager.getFrozenStockFraction(villageId);
        String sql = "UPDATE warehouse SET amount = amount - ? "
                + "WHERE village_id = ? AND item_type = ? AND amount >= ? "
                + "AND amount - ? >= ? AND amount - ? >= amount * ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, amount);
            statement.setInt(2, villageId);
            statement.setString(3, itemType);
            statement.setInt(4, amount);
            statement.setInt(5, amount);
            statement.setInt(6, configuredReserve);
            statement.setInt(7, amount);
            statement.setDouble(8, frozenFraction);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("扣除可用库存失败：" + e.getMessage());
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
        Village ownedVillage = VillageManager.getVillage(player.getUniqueId());
        if (ownedVillage == null || ownedVillage.getId() != villageId || amount <= 0) {
            player.sendMessage("§c你无权访问这个仓库！");
            return false;
        }

        // 检查仓库中是否有足够物品
        WarehouseItem item = getWarehouseItem(villageId, itemType);
        int extractable = item == null ? 0 : getExtractableAmount(villageId, itemType);
        if (extractable < amount) {
            player.sendMessage("§c可提取的 " + itemType + " 不足！当前可提取 " + extractable
                    + "，保留量不会被取出");
            return false;
        }
        
        // 检查玩家背包是否有足够空间
        Material material = Material.getMaterial(itemType);
        if (material == null) {
            player.sendMessage("§c无效的物品类型: " + itemType);
            return false;
        }
        
        // 从仓库中移除物品
        if (!removeExtractableItem(villageId, itemType, amount)) {
            player.sendMessage("§c提取物品时发生错误！");
            return false;
        }

        int remaining = amount;
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack itemStack = new ItemStack(material, stackAmount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stackAmount;
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
        int extractable = item == null ? 0 : getExtractableAmount(villageId, itemType);
        if (extractable <= 0) {
            player.sendMessage("§c仓库中没有" + itemType + "！");
            return false;
        }
        
        return extractItem(player, villageId, itemType, extractable);
    }

    public static int getExtractableAmount(int villageId, String itemType) {
        WarehouseItem item = getWarehouseItem(villageId, itemType);
        if (item == null) return 0;
        int reserve = getProtectedReserveAmount(villageId, itemType, item.getAmount());
        return GameplayMath.extractableAmount(item.getAmount(), reserve);
    }

    public static int getProtectedReserveAmount(int villageId, String itemType) {
        WarehouseItem item = getWarehouseItem(villageId, itemType);
        return item == null ? 0 : getProtectedReserveAmount(villageId, itemType, item.getAmount());
    }

    private static int getProtectedReserveAmount(int villageId, String itemType, int storedAmount) {
        return GameplayMath.effectiveReserveAmount(storedAmount,
                WarehouseRuleManager.getItemRule(villageId, itemType).getReserveAmount(),
                PolicyManager.getFrozenStockFraction(villageId));
    }
    
    /**
     * 获取仓库容量
     * @param village 村庄对象
     * @return 仓库容量
     */
    public static int getWarehouseCapacity(Village village) {
        return village.getWarehouseCapacity();
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
