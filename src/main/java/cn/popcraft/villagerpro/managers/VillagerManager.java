package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VillagerManager {
    
    /**
     * 获取村庄的所有村民
     * @param villageId 村庄ID
     * @return 村民列表
     */
    public static List<VillagerData> getVillagers(int villageId) {
        List<VillagerData> villagers = new ArrayList<>();
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, entity_uuid, profession, level, experience, follow_mode FROM villagers WHERE village_id = ?")) {
            
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                VillagerData villager = new VillagerData(
                        resultSet.getInt("id"),
                        resultSet.getInt("village_id"),
                        UUID.fromString(resultSet.getString("entity_uuid")),
                        resultSet.getString("profession"),
                        resultSet.getInt("level"),
                        resultSet.getInt("experience"),
                        resultSet.getString("follow_mode")
                );
                villagers.add(villager);
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return villagers;
    }
    
    /**
     * 根据实体UUID获取村民数据
     * @param entityUUID 实体UUID
     * @return 村民数据，如果不存在则返回null
     */
    public static VillagerData getVillager(UUID entityUUID) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, entity_uuid, profession, level, experience, follow_mode FROM villagers WHERE entity_uuid = ?")) {
            
            statement.setString(1, entityUUID.toString());
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return new VillagerData(
                        resultSet.getInt("id"),
                        resultSet.getInt("village_id"),
                        UUID.fromString(resultSet.getString("entity_uuid")),
                        resultSet.getString("profession"),
                        resultSet.getInt("level"),
                        resultSet.getInt("experience"),
                        resultSet.getString("follow_mode")
                );
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 根据ID获取村民数据
     * @param id 村民ID
     * @return 村民数据，如果不存在则返回null
     */
    public static VillagerData getVillagerById(int id) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, entity_uuid, profession, level, experience, follow_mode FROM villagers WHERE id = ?")) {
            
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return new VillagerData(
                        resultSet.getInt("id"),
                        resultSet.getInt("village_id"),
                        UUID.fromString(resultSet.getString("entity_uuid")),
                        resultSet.getString("profession"),
                        resultSet.getInt("level"),
                        resultSet.getInt("experience"),
                        resultSet.getString("follow_mode")
                );
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 招募村民
     * @param player 玩家
     * @param village 村庄
     * @param entityUUID 实体UUID
     * @param profession 职业
     * @return 招募的村民数据，如果招募失败则返回null
     */
    public static VillagerData recruitVillager(Player player, Village village, UUID entityUUID, String profession) {
        // 检查村庄是否已达村民上限
        List<VillagerData> villagers = getVillagers(village.getId());
        if (villagers.size() >= village.getVillagerLimit()) {
            player.sendMessage("§c村庄村民数量已达上限！");
            return null;
        }
        
        // 检查是否能支付招募费用
        List<CostEntry> recruitCosts = getRecruitCosts();
        if (!CostHandler.canAfford(player, recruitCosts)) {
            player.sendMessage("§c你没有足够的资源来招募村民！");
            return null;
        }
        
        // 扣除招募费用
        if (!CostHandler.deduct(player, recruitCosts)) {
            player.sendMessage("§c招募村民时发生错误！");
            return null;
        }
        
        // 执行招募
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO villagers (village_id, entity_uuid, profession, level, experience, follow_mode) VALUES (?, ?, ?, ?, ?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            statement.setInt(1, village.getId());
            statement.setString(2, entityUUID.toString());
            statement.setString(3, profession);
            statement.setInt(4, 1); // 默认等级
            statement.setInt(5, 0); // 默认经验
            statement.setString(6, "FREE"); // 默认跟随模式
            
            int affectedRows = statement.executeUpdate();
            
            if (affectedRows > 0) {
                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    // 不在这里发送消息，让调用者处理
                    return new VillagerData(id, village.getId(), entityUUID, profession, 1, 0, "FREE");
                }
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
        }
        
        // 不在这里发送消息，让调用者处理
        return null;
    }
    
    /**
     * 移除村民
     * @param villagerId 村民ID
     * @return 是否移除成功
     */
    public static boolean removeVillager(int villagerId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM villagers WHERE id = ?")) {
            
            statement.setInt(1, villagerId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 升级村民
     * @param villager 村民数据
     * @return 是否升级成功
     */
    public static boolean upgradeVillager(VillagerData villager) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE villagers SET level = ?, experience = ?, profession = ?, follow_mode = ? WHERE id = ?")) {
            
            statement.setInt(1, villager.getLevel());
            statement.setInt(2, villager.getExperience());
            statement.setString(3, villager.getProfession());
            statement.setString(4, villager.getFollowMode());
            statement.setInt(5, villager.getId());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 更新村民信息
     * @param villager 村民数据
     * @return 是否更新成功
     */
    public static boolean updateVillager(VillagerData villager) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE villagers SET level = ?, experience = ?, profession = ?, follow_mode = ? WHERE id = ?")) {
            
            statement.setInt(1, villager.getLevel());
            statement.setInt(2, villager.getExperience());
            statement.setString(3, villager.getProfession());
            statement.setString(4, villager.getFollowMode());
            statement.setInt(5, villager.getId());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("数据库操作失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取招募成本
     * @return 招募成本列表
     */
    public static List<CostEntry> getRecruitCosts() {
        List<CostEntry> costs = new ArrayList<>();
        
        String path = "villager.recruit_cost";
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
     * 获取职业显示名称
     * @param profession 职业
     * @return 显示名称
     */
    public static String getProfessionDisplayName(String profession) {
        return cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                .getString("villager.professions." + profession + ".name", profession);
    }
    
    /**
     * 添加村民工作相关方法
     */
    // 村民工作相关方法已在WorkScheduler类中实现
}