package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VillageManager {
    
    /**
     * 获取所有村庄
     * @return 村庄列表
     */
    public static List<Village> getAllVillages() {
        List<Village> villages = new ArrayList<>();
        
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, owner_uuid, name, level, experience, prosperity FROM villages")) {
            
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                Village village = new Village(
                        resultSet.getInt("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getInt("level"),
                        resultSet.getInt("experience"),
                        resultSet.getInt("prosperity")
                );
                villages.add(village);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return villages;
    }
    
    /**
     * 获取玩家的村庄
     * @param ownerUUID 玩家UUID
     * @return 村庄对象，如果不存在则返回null
     */
    public static Village getVillage(UUID ownerUUID) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, owner_uuid, name, level, experience, prosperity FROM villages WHERE owner_uuid = ?")) {
            
            statement.setString(1, ownerUUID.toString());
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return new Village(
                        resultSet.getInt("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getInt("level"),
                        resultSet.getInt("experience"),
                        resultSet.getInt("prosperity")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * 根据ID获取村庄
     * @param id 村庄ID
     * @return 村庄对象，如果不存在则返回null
     */
    public static Village getVillageById(int id) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, owner_uuid, name, level, experience, prosperity FROM villages WHERE id = ?")) {
            
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return new Village(
                        resultSet.getInt("id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getInt("level"),
                        resultSet.getInt("experience"),
                        resultSet.getInt("prosperity")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * 创建村庄
     * @param ownerUUID 玩家UUID
     * @param name 村庄名称
     * @return 创建的村庄对象
     */
    public static Village createVillage(UUID ownerUUID, String name) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO villages (owner_uuid, name) VALUES (?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            statement.setString(1, ownerUUID.toString());
            statement.setString(2, name);
            
            int affectedRows = statement.executeUpdate();
            
            if (affectedRows > 0) {
                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return new Village(id, ownerUUID, name, 1, 0, 0);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * 升级村庄
     * @param village 村庄对象
     * @return 是否升级成功
     */
    public static boolean upgradeVillage(Village village) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE villages SET level = ?, experience = ?, prosperity = ? WHERE id = ?")) {
            
            statement.setInt(1, village.getLevel());
            statement.setInt(2, village.getExperience());
            statement.setInt(3, village.getProsperity());
            statement.setInt(4, village.getId());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 更新村庄信息
     * @param village 村庄对象
     * @return 是否更新成功
     */
    public static boolean updateVillage(Village village) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE villages SET name = ?, level = ?, experience = ?, prosperity = ? WHERE id = ?")) {
            
            statement.setString(1, village.getName());
            statement.setInt(2, village.getLevel());
            statement.setInt(3, village.getExperience());
            statement.setInt(4, village.getProsperity());
            statement.setInt(5, village.getId());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 添加村庄升级技能树相关方法
     */
    // 村庄升级技能树相关方法已在VillageUpgradeManager类中实现
}