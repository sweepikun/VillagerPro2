package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 联盟管理器
 * 实现联盟的创建、加入、离开、查询等数据库操作
 */
public class SimpleAllianceManager {
    
    private static SimpleAllianceManager instance;
    private final VillagerPro plugin;
    
    private SimpleAllianceManager(VillagerPro plugin) {
        this.plugin = plugin;
    }
    
    public static SimpleAllianceManager getInstance() {
        return instance;
    }
    
    public void initialize(VillagerPro plugin) {
        if (instance == null) {
            instance = new SimpleAllianceManager(plugin);
        }
    }
    
    /**
     * 检查玩家是否有联盟
     */
    public boolean hasAlliance(UUID playerUUID) {
        Village village = VillageManager.getVillage(playerUUID);
        if (village == null) return false;
        return getAllianceIdByVillageId(village.getId()) > 0;
    }
    
    /**
     * 获取玩家的联盟名称
     */
    public String getPlayerAllianceName(UUID playerUUID) {
        Village village = VillageManager.getVillage(playerUUID);
        if (village == null) return null;
        
        int allianceId = getAllianceIdByVillageId(village.getId());
        if (allianceId <= 0) return null;
        
        return getAllianceNameById(allianceId);
    }
    
    /**
     * 创建联盟
     */
    public void createAlliance(Player player, String allianceName) {
        if (!plugin.getConfig().getBoolean("features.alliance", false)) {
            player.sendMessage(ChatColor.RED + "联盟系统未启用");
            return;
        }
        
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage(ChatColor.RED + "你还没有村庄");
            return;
        }
        
        int minLevel = plugin.getConfig().getInt("alliance.config.min_village_level", 2);
        if (village.getLevel() < minLevel) {
            player.sendMessage(ChatColor.RED + "村庄需要达到等级 " + minLevel + " 才能创建联盟");
            return;
        }
        
        if (hasAlliance(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你已经在联盟中");
            return;
        }
        
        if (allianceName == null || allianceName.trim().isEmpty() || allianceName.length() > 20) {
            player.sendMessage(ChatColor.RED + "联盟名称不合法（1-20字符）");
            return;
        }
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO alliances (name, owner_village_id, max_members) VALUES (?, ?, ?)",
                 PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            int maxMembers = plugin.getConfig().getInt("alliance.config.max_members", 5);
            stmt.setString(1, allianceName);
            stmt.setInt(2, village.getId());
            stmt.setInt(3, maxMembers);
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int allianceId = keys.getInt(1);
                addMemberToAlliance(allianceId, village.getId());
                player.sendMessage(ChatColor.GREEN + "✅ 联盟 '" + allianceName + "' 创建成功！");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("创建联盟失败: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "创建联盟失败");
        }
    }
    
    /**
     * 加入联盟
     */
    public void joinAlliance(Player player, int allianceId) {
        if (!plugin.getConfig().getBoolean("features.alliance", false)) {
            player.sendMessage(ChatColor.RED + "联盟系统未启用");
            return;
        }
        
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage(ChatColor.RED + "你还没有村庄");
            return;
        }
        
        if (hasAlliance(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你已经在联盟中");
            return;
        }
        
        int minLevel = plugin.getConfig().getInt("alliance.config.min_village_level", 2);
        if (village.getLevel() < minLevel) {
            player.sendMessage(ChatColor.RED + "村庄需要达到等级 " + minLevel + " 才能加入联盟");
            return;
        }
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT max_members FROM alliances WHERE id = ?")) {
            
            stmt.setInt(1, allianceId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                player.sendMessage(ChatColor.RED + "联盟不存在");
                return;
            }
            
            int maxMembers = rs.getInt("max_members");
            int currentMembers = getMemberCount(allianceId);
            if (currentMembers >= maxMembers) {
                player.sendMessage(ChatColor.RED + "联盟成员已满");
                return;
            }
            
            if (addMemberToAlliance(allianceId, village.getId())) {
                player.sendMessage(ChatColor.GREEN + "✅ 成功加入联盟！");
            } else {
                player.sendMessage(ChatColor.RED + "加入联盟失败");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("加入联盟失败: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "加入联盟失败");
        }
    }
    
    /**
     * 离开联盟
     */
    public void leaveAlliance(Player player) {
        if (!plugin.getConfig().getBoolean("features.alliance", false)) {
            player.sendMessage(ChatColor.RED + "联盟系统未启用");
            return;
        }
        
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage(ChatColor.RED + "你还没有村庄");
            return;
        }
        
        int allianceId = getAllianceIdByVillageId(village.getId());
        if (allianceId <= 0) {
            player.sendMessage(ChatColor.RED + "你没有加入任何联盟");
            return;
        }
        
        if (removeMemberFromAlliance(allianceId, village.getId())) {
            // 如果是盟主离开，且联盟无成员，则删除联盟
            if (getMemberCount(allianceId) <= 0) {
                deleteAlliance(allianceId);
            }
            player.sendMessage(ChatColor.GREEN + "✅ 已离开联盟");
        } else {
            player.sendMessage(ChatColor.RED + "离开联盟失败");
        }
    }
    
    /**
     * 获取所有联盟
     */
    public List<SimpleAlliance> getAllAlliances() {
        List<SimpleAlliance> alliances = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT a.id, a.name, a.owner_village_id, a.created_at, COUNT(am.village_id) as member_count " +
                 "FROM alliances a LEFT JOIN alliance_members am ON a.id = am.alliance_id " +
                 "GROUP BY a.id")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                alliances.add(new SimpleAlliance(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("member_count"),
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : ""
                ));
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("获取联盟列表失败: " + e.getMessage());
        }
        
        return alliances;
    }
    
    /**
     * 获取玩家所在联盟的成员村庄ID
     */
    public List<Integer> getAllianceVillageIds(UUID playerUUID) {
        List<Integer> villageIds = new ArrayList<>();
        Village village = VillageManager.getVillage(playerUUID);
        if (village == null) return villageIds;
        
        int allianceId = getAllianceIdByVillageId(village.getId());
        if (allianceId <= 0) return villageIds;
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT village_id FROM alliance_members WHERE alliance_id = ?")) {
            
            stmt.setInt(1, allianceId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                villageIds.add(rs.getInt("village_id"));
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("获取联盟成员失败: " + e.getMessage());
        }
        
        return villageIds;
    }
    
    // ============== 数据库辅助方法 ==============
    
    private int getAllianceIdByVillageId(int villageId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT alliance_id FROM alliance_members WHERE village_id = ?")) {
            
            stmt.setInt(1, villageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("alliance_id");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("查询联盟成员失败: " + e.getMessage());
        }
        return -1;
    }
    
    private String getAllianceNameById(int allianceId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT name FROM alliances WHERE id = ?")) {
            
            stmt.setInt(1, allianceId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("查询联盟名称失败: " + e.getMessage());
        }
        return null;
    }
    
    private int getMemberCount(int allianceId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) as count FROM alliance_members WHERE alliance_id = ?")) {
            
            stmt.setInt(1, allianceId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("统计联盟成员失败: " + e.getMessage());
        }
        return 0;
    }
    
    private boolean addMemberToAlliance(int allianceId, int villageId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT OR IGNORE INTO alliance_members (alliance_id, village_id) VALUES (?, ?)")) {
            
            stmt.setInt(1, allianceId);
            stmt.setInt(2, villageId);
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            plugin.getLogger().warning("添加联盟成员失败: " + e.getMessage());
            return false;
        }
    }
    
    private boolean removeMemberFromAlliance(int allianceId, int villageId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM alliance_members WHERE alliance_id = ? AND village_id = ?")) {
            
            stmt.setInt(1, allianceId);
            stmt.setInt(2, villageId);
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            plugin.getLogger().warning("移除联盟成员失败: " + e.getMessage());
            return false;
        }
    }
    
    private void deleteAlliance(int allianceId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM alliances WHERE id = ?")) {
            
            stmt.setInt(1, allianceId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().warning("删除联盟失败: " + e.getMessage());
        }
    }
    
    /**
     * 简化联盟数据类
     */
    public static class SimpleAlliance {
        private int id;
        private String name;
        private int memberCount;
        private String createdDate;
        
        public SimpleAlliance(int id, String name, int memberCount, String createdDate) {
            this.id = id;
            this.name = name;
            this.memberCount = memberCount;
            this.createdDate = createdDate;
        }
        
        public int getId() { return id; }
        public String getName() { return name; }
        public int getMemberCount() { return memberCount; }
        public String getCreatedDate() { return createdDate; }
    }
}
