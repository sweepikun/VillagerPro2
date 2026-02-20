package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.VisitorData;
import cn.popcraft.villagerpro.models.VisitorDeal;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.scheduler.VisitorScheduler;
import cn.popcraft.villagerpro.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访客系统管理器
 * 负责管理访客的生成、交易、清理等所有逻辑
 */
public class VisitorManager {
    
    private static VisitorManager instance;
    private final Map<Integer, VisitorData> activeVisitors = new ConcurrentHashMap<>();
    private final Map<Integer, List<VisitorDeal>> visitorDeals = new ConcurrentHashMap<>();
    private VisitorScheduler scheduler;
    private boolean initialized = false;
    
    public static VisitorManager getInstance() {
        if (instance == null) {
            instance = new VisitorManager();
        }
        return instance;
    }
    
    /**
     * 初始化访客管理器
     */
    public void initialize() {
        if (initialized) return;
        
        VillagerPro plugin = VillagerPro.getInstance();
        
        // 检查是否启用访客系统
        if (!plugin.getConfig().getBoolean("visitors.enabled", true)) {
            plugin.getLogger().info("访客系统已禁用");
            return;
        }
        
        // 清理过期的访客
        cleanupExpiredVisitors();
        
        // 恢复活跃的访客
        restoreActiveVisitors();
        
        // 初始化调度器
        scheduler = new VisitorScheduler();
        scheduler.start();
        
        initialized = true;
        plugin.getLogger().info("访客系统已启用");
    }
    
    /**
     * 关闭访客管理器
     */
    public void shutdown() {
        if (!initialized) return;
        
        // 停止调度器
        if (scheduler != null) {
            scheduler.stop();
        }
        
        // 保存所有访客数据
        saveAllVisitors();
        
        // 移除所有访客实体
        for (VisitorData visitor : activeVisitors.values()) {
            visitor.removeEntity();
        }
        
        activeVisitors.clear();
        visitorDeals.clear();
        initialized = false;
        
        VillagerPro.getInstance().getLogger().info("访客系统已关闭");
    }
    
    /**
     * 为指定村庄生成访客
     */
    public VisitorData spawnVisitor(Village village, String type) {
        if (!isFeatureEnabled() || village == null) return null;
        
        // 检查村庄是否已有活跃访客
        if (hasActiveVisitor(village.getId())) {
            return null;
        }
        
        // 检查繁荣度是否达到要求
        int threshold = VillagerPro.getInstance().getConfig().getInt("visitors.prosperity_threshold", 100);
        if (village.getProsperity() < threshold) {
            return null;
        }
        
        // 获取村庄中心位置
        Location center = getVillageCenter(village);
        if (center == null) {
            return null;
        }
        
        // 随机选择访客名称
        String[] names = getRandomNames(type);
        String name = names[new Random().nextInt(names.length)];
        String displayName = getDisplayName(type, name);
        
        // 计算过期时间
        int stayMinutes = VillagerPro.getInstance().getConfig().getInt("visitors.stay_duration_minutes", 10);
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (stayMinutes * 60 * 1000));
        
        // 创建访客数据
        VisitorData visitor = new VisitorData(0, village.getId(), type, center, name, displayName, 
                                            new Timestamp(System.currentTimeMillis()), expiresAt, "");
        
        // 保存到数据库
        int visitorId = saveVisitorToDatabase(visitor);
        if (visitorId == -1) {
            return null;
        }
        
        visitor.setId(visitorId);
        
        // 生成实体
        if (!visitor.spawnEntity()) {
            deleteVisitorFromDatabase(visitorId);
            return null;
        }
        
        // 添加到活跃列表
        activeVisitors.put(visitorId, visitor);
        
        // 创建清理任务
        visitor.createCleanupTask();
        
        // 通知附近玩家
        notifyNearbyPlayers(visitor);
        
        VillagerPro.getInstance().getLogger().info("为村庄 " + village.getName() + " 生成了 " + type + " 访客");
        
        return visitor;
    }
    
    /**
     * 移除访客
     */
    public void removeVisitor(int visitorId) {
        VisitorData visitor = activeVisitors.remove(visitorId);
        if (visitor != null) {
            visitor.removeEntity();
            deleteVisitorFromDatabase(visitorId);
            
            // 清理相关的交易记录
            visitorDeals.remove(visitorId);
        }
    }
    
    /**
     * 检查村庄是否已有活跃访客
     */
    public boolean hasActiveVisitor(int villageId) {
        return activeVisitors.values().stream()
                .anyMatch(visitor -> visitor.getVillageId() == villageId && visitor.isActive());
    }
    
    /**
     * 获取村庄的活跃访客
     */
    public VisitorData getActiveVisitor(int villageId) {
        return activeVisitors.values().stream()
                .filter(visitor -> visitor.getVillageId() == villageId && visitor.isActive())
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取访客的交易列表
     */
    public List<VisitorDeal> getVisitorDeals(int visitorId) {
        return visitorDeals.getOrDefault(visitorId, new ArrayList<>());
    }
    
    /**
     * 为访客创建交易
     */
    public VisitorDeal createDeal(VisitorData visitor, String dealType, String itemType, 
                                 int amount, double price, String playerName) {
        int stayMinutes = VillagerPro.getInstance().getConfig().getInt("visitors.stay_duration_minutes", 10);
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (stayMinutes * 60 * 1000));
        
        VisitorDeal deal = new VisitorDeal(0, visitor.getId(), dealType, itemType, amount, 
                                         price, "", 0, "", playerName, new Timestamp(System.currentTimeMillis()), 
                                         expiresAt);
        
        // 保存到数据库
        int dealId = saveDealToDatabase(deal);
        if (dealId == -1) return null;
        
        deal.setId(dealId);
        
        // 添加到访客交易列表
        visitorDeals.computeIfAbsent(visitor.getId(), k -> new ArrayList<>()).add(deal);
        
        return deal;
    }
    
    /**
     * 处理玩家与访客的交互
     */
    public void interactWithVisitor(Player player, VisitorData visitor) {
        if (visitor == null || !visitor.isActive()) return;
        
        // 根据访客类型显示对应的GUI
        switch (visitor.getType().toLowerCase()) {
            case "merchant":
                showMerchantGUI(player, visitor);
                break;
            case "traveler":
                showTravelerGUI(player, visitor);
                break;
            case "festival":
                showFestivalGUI(player, visitor);
                break;
        }
    }
    
    /**
     * 显示商人GUI
     */
    private void showMerchantGUI(Player player, VisitorData visitor) {
        try {
            cn.popcraft.villagerpro.gui.VisitorGUIManager.showMerchantGUI(player, visitor);
        } catch (Exception e) {
            // Fallback to simple message if GUI fails
            player.sendMessage("§e[商人] 欢迎来到我的商店！");
            player.sendMessage("§7点击查看我的商品...");
            VillagerPro.getInstance().getLogger().warning("访客GUI显示失败: " + e.getMessage());
        }
    }
    
    /**
     * 显示旅行者GUI
     */
    private void showTravelerGUI(Player player, VisitorData visitor) {
        try {
            cn.popcraft.villagerpro.gui.VisitorGUIManager.showTravelerGUI(player, visitor);
        } catch (Exception e) {
            // Fallback to simple message if GUI fails
            player.sendMessage("§e[旅行者] 你好，村长！");
            player.sendMessage("§7我需要一些帮助...");
            VillagerPro.getInstance().getLogger().warning("访客GUI显示失败: " + e.getMessage());
        }
    }
    
    /**
     * 显示节日使者GUI
     */
    private void showFestivalGUI(Player player, VisitorData visitor) {
        try {
            cn.popcraft.villagerpro.gui.VisitorGUIManager.showFestivalGUI(player, visitor);
        } catch (Exception e) {
            // Fallback to simple message if GUI fails
            player.sendMessage("§e[节日使者] 节日快乐！");
            player.sendMessage("§7来领取节日奖励吧！");
            VillagerPro.getInstance().getLogger().warning("访客GUI显示失败: " + e.getMessage());
        }
    }
    
    // ============== 私有方法 ==============
    
    /**
     * 检查功能是否启用
     */
    private boolean isFeatureEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.visitors", true);
    }
    
    /**
     * 获取村庄中心位置
     */
    private Location getVillageCenter(Village village) {
        // 尝试从村庄的拥有者位置获取村庄中心
        // 如果村庄拥有者在世界中有位置，使用该位置
        // 否则使用主世界的出生点
        
        // 获取第一个在线的玩家（如果村庄拥有者不在线）
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(village.getOwnerUUID())) {
                Location playerLoc = player.getLocation();
                return new Location(playerLoc.getWorld(), 
                                  playerLoc.getX(), 
                                  playerLoc.getY(), 
                                  playerLoc.getZ());
            }
        }
        
        // 如果所有者不在线或未找到，使用主世界出生点
        World world = Bukkit.getWorlds().get(0);
        return world.getSpawnLocation();
    }
    
    /**
     * 获取随机名称
     */
    private String[] getRandomNames(String type) {
        switch (type.toLowerCase()) {
            case "merchant":
                return new String[]{"流浪商人", "神秘商贩", "行商者", "货郎", "行脚商人"};
            case "traveler":
                return new String[]{"旅行家", "冒险者", "游侠", "行者", "探险家"};
            case "festival":
                return new String[]{"节日使者", "庆典主持", "欢庆使者", "节日专员", "庆典官"};
            default:
                return new String[]{"访客"};
        }
    }
    
    /**
     * 获取显示名称
     */
    private String getDisplayName(String type, String name) {
        return "§e[访客] " + name;
    }
    
    /**
     * 通知附近玩家
     */
    private void notifyNearbyPlayers(VisitorData visitor) {
        Location location = visitor.getLocation();
        double radius = 10.0; // 10格半径
        
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= radius) {
                player.sendMessage("§e[访客] " + visitor.getName() + " 来到了村庄！");
                player.sendMessage("§7点击村民进行交互...");
            }
        }
    }
    
    /**
     * 清理过期访客
     */
    private void cleanupExpiredVisitors() {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM visitors WHERE expires_at < CURRENT_TIMESTAMP")) {
            
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                VillagerPro.getInstance().getLogger().info("清理了 " + deleted + " 个过期访客");
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("清理过期访客失败: " + e.getMessage());
        }
    }
    
    /**
     * 恢复活跃访客
     */
    private void restoreActiveVisitors() {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM visitors WHERE expires_at > CURRENT_TIMESTAMP AND active = 1 ORDER BY id")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                int villageId = rs.getInt("village_id");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String displayName = rs.getString("display_name");
                double x = rs.getDouble("location_x");
                double y = rs.getDouble("location_y");
                double z = rs.getDouble("location_z");
                String worldName = rs.getString("world");
                Timestamp spawnedAt = rs.getTimestamp("spawned_at");
                Timestamp expiresAt = rs.getTimestamp("expires_at");
                String customData = rs.getString("custom_data");
                
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    VillagerPro.getInstance().getLogger().warning("访客 " + name + " 的世界不存在，跳过恢复");
                    continue;
                }
                
                Location location = new Location(world, x, y, z);
                
                VisitorData visitor = new VisitorData(
                    id, villageId, type, location, 
                    name, displayName, spawnedAt, 
                    expiresAt, customData
                );
                
                if (visitor.spawnEntity()) {
                    activeVisitors.put(id, visitor);
                    visitor.createCleanupTask();
                    VillagerPro.getInstance().getLogger().info("已恢复访客: " + name);
                } else {
                    VillagerPro.getInstance().getLogger().warning("恢复访客实体失败: " + name);
                }
            }
            
            if (!activeVisitors.isEmpty()) {
                VillagerPro.getInstance().getLogger().info("成功恢复 " + activeVisitors.size() + " 个活跃访客");
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("恢复访客数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存访客到数据库
     */
    private int saveVisitorToDatabase(VisitorData visitor) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO visitors (village_id, type, name, display_name, location_x, location_y, location_z, world, spawned_at, expires_at, custom_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                 java.sql.Statement.RETURN_GENERATED_KEYS)) {
            
            Location loc = visitor.getLocation();
            stmt.setInt(1, visitor.getVillageId());
            stmt.setString(2, visitor.getType());
            stmt.setString(3, visitor.getName());
            stmt.setString(4, visitor.getDisplayName());
            stmt.setDouble(5, loc.getX());
            stmt.setDouble(6, loc.getY());
            stmt.setDouble(7, loc.getZ());
            stmt.setString(8, loc.getWorld().getName());
            stmt.setTimestamp(9, visitor.getSpawnedAt());
            stmt.setTimestamp(10, visitor.getExpiresAt());
            stmt.setString(11, visitor.getCustomData());
            
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("保存访客数据失败: " + e.getMessage());
        }
        return -1;
    }
    
    /**
     * 从数据库删除访客
     */
    private void deleteVisitorFromDatabase(int visitorId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM visitors WHERE id = ?")) {
            
            stmt.setInt(1, visitorId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("删除访客数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存交易到数据库
     */
    private int saveDealToDatabase(VisitorDeal deal) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO visitor_deals (visitor_id, deal_type, item_type, amount_required, price, reward_type, reward_amount, reward_item, player_name, status, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                 java.sql.Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, deal.getVisitorId());
            stmt.setString(2, deal.getDealType());
            stmt.setString(3, deal.getItemType());
            stmt.setInt(4, deal.getAmountRequired());
            stmt.setDouble(5, deal.getPrice());
            stmt.setString(6, deal.getRewardType());
            stmt.setInt(7, deal.getRewardAmount());
            stmt.setString(8, deal.getRewardItem());
            stmt.setString(9, deal.getPlayerName());
            stmt.setString(10, deal.getStatus());
            stmt.setTimestamp(11, deal.getCreatedAt());
            stmt.setTimestamp(12, deal.getExpiresAt());
            
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("保存交易数据失败: " + e.getMessage());
        }
        return -1;
    }
    
    /**
     * 保存所有访客
     */
    private void saveAllVisitors() {
        int savedCount = 0;
        for (VisitorData visitor : activeVisitors.values()) {
            if (visitor.getId() <= 0) {
                int newId = saveVisitorToDatabase(visitor);
                if (newId > 0) {
                    visitor.setId(newId);
                    savedCount++;
                }
            } else {
                updateVisitorInDatabase(visitor);
                savedCount++;
            }
        }
        
        if (savedCount > 0) {
            VillagerPro.getInstance().getLogger().info("已保存 " + savedCount + " 个访客到数据库");
        }
    }
    
    /**
     * 更新数据库中的访客
     */
    private boolean updateVisitorInDatabase(VisitorData visitor) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE visitors SET village_id = ?, type = ?, name = ?, display_name = ?, " +
                 "location_x = ?, location_y = ?, location_z = ?, world = ?, " +
                 "spawned_at = ?, expires_at = ?, active = ?, custom_data = ? WHERE id = ?")) {
            
            Location loc = visitor.getLocation();
            stmt.setInt(1, visitor.getVillageId());
            stmt.setString(2, visitor.getType());
            stmt.setString(3, visitor.getName());
            stmt.setString(4, visitor.getDisplayName());
            stmt.setDouble(5, loc.getX());
            stmt.setDouble(6, loc.getY());
            stmt.setDouble(7, loc.getZ());
            stmt.setString(8, loc.getWorld() != null ? loc.getWorld().getName() : "");
            stmt.setTimestamp(9, visitor.getSpawnedAt());
            stmt.setTimestamp(10, visitor.getExpiresAt());
            stmt.setBoolean(11, visitor.isActive());
            stmt.setString(12, visitor.getCustomData());
            stmt.setInt(13, visitor.getId());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("更新访客数据失败: " + e.getMessage());
            return false;
        }
    }
    
    // ============== 公共接口方法 ==============
    
    /**
     * 获取所有活跃访客
     */
    public Collection<VisitorData> getAllActiveVisitors() {
        return new ArrayList<>(activeVisitors.values());
    }
    
    /**
     * 获取指定村庄的所有访客交易
     */
    public List<VisitorDeal> getDealsByVillage(int villageId) {
        List<VisitorDeal> villageDeals = new ArrayList<>();
        for (List<VisitorDeal> deals : visitorDeals.values()) {
            for (VisitorDeal deal : deals) {
                // 这里需要根据实际的关联关系来筛选
                // 暂时返回所有交易
                villageDeals.add(deal);
            }
        }
        return villageDeals;
    }
    
    /**
     * 获取访客系统状态
     */
    public boolean isInitialized() {
        return initialized;
    }
}