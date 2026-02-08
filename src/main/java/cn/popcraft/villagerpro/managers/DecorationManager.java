package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 装饰系统管理器
 * 负责管理村庄装饰的购买、放置、效果等
 */
public class DecorationManager {
    
    private static DecorationManager instance;
    private final VillagerPro plugin;
    private final CostHandler costHandler;
    private final Random random;
    
    public static DecorationManager getInstance() {
        if (instance == null) {
            instance = new DecorationManager();
        }
        return instance;
    }
    
    private DecorationManager() {
        this.plugin = VillagerPro.getInstance();
        this.costHandler = new CostHandler();
        this.random = new Random();
    }
    
    /**
     * 购买装饰
     */
    public boolean purchaseDecoration(Player player, Village village, String decorationType) {
        // 检查功能是否启用
        if (!plugin.getConfig().getBoolean("features.decorations", true) || 
            !plugin.getConfig().getBoolean("decorations.enabled", true)) {
            player.sendMessage("§c装饰系统已禁用");
            return false;
        }
        
        // 检查装饰类型是否存在
        ConfigurationSection decorationConfig = plugin.getConfig().getConfigurationSection("decorations.items." + decorationType);
        if (decorationConfig == null) {
            player.sendMessage("§c未知的装饰类型: " + decorationType);
            return false;
        }
        
        // 获取装饰信息
        String name = decorationConfig.getString("name", decorationType);
        Material material = Material.valueOf(decorationConfig.getString("material", "STONE"));
        List<String> costList = decorationConfig.getStringList("cost");
        int prosperityBoost = decorationConfig.getInt("prosperity_boost", 0);
        
        // 检查并扣除成本
        List<CostEntry> parsedCosts = parseCosts(costList);
        if (!costHandler.canAfford(player, parsedCosts)) {
            player.sendMessage("§c资源不足，无法购买 " + name);
            return false;
        }
        if (!costHandler.deduct(player, parsedCosts)) {
            player.sendMessage("§c扣除资源失败，请重试");
            return false;
        }
        
        // 给予玩家装饰物品
        ItemStack decorationItem = new ItemStack(material);
        player.getInventory().addItem(decorationItem);
        
        player.sendMessage("§a成功购买装饰: " + name);
        player.sendMessage("§7右键放置装饰物品");
        
        return true;
    }
    
    /**
     * 放置装饰
     */
    public boolean placeDecoration(Player player, String decorationType, Block block) {
        Village village = findNearbyVillage(block.getLocation());
        if (village == null) {
            player.sendMessage("§c只能在村庄附近放置装饰");
            return false;
        }
        
        // 检查装饰类型配置
        ConfigurationSection decorationConfig = plugin.getConfig().getConfigurationSection("decorations.items." + decorationType);
        if (decorationConfig == null) {
            return false;
        }
        
        // 放置装饰方块
        Material material = Material.valueOf(decorationConfig.getString("material", "STONE"));
        block.setType(material);
        
        // 保存到数据库
        saveDecorationToDatabase(village.getId(), decorationType, material, block.getLocation());
        
        // 增加繁荣度
        int prosperityBoost = decorationConfig.getInt("prosperity_boost", 0);
        if (prosperityBoost > 0) {
            village.addProsperity(prosperityBoost);
            player.sendMessage("§a村庄繁荣度 +" + prosperityBoost);
        }
        
        // 触发特殊效果
        triggerDecorationEffects(village, decorationType, block);
        
        player.sendMessage("§a装饰放置成功！");
        return true;
    }
    
    /**
     * 获取村庄的所有装饰
     */
    public List<VillageDecoration> getVillageDecorations(int villageId) {
        List<VillageDecoration> decorations = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM decorations WHERE village_id = ? ORDER BY id")) {
            
            stmt.setInt(1, villageId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                VillageDecoration decoration = new VillageDecoration(
                    rs.getInt("id"),
                    rs.getInt("village_id"),
                    rs.getString("decoration_type"),
                    rs.getString("item_type"),
                    rs.getInt("amount"),
                    rs.getDouble("location_x"),
                    rs.getDouble("location_y"),
                    rs.getDouble("location_z"),
                    rs.getString("world"),
                    rs.getTimestamp("placed_at")
                );
                decorations.add(decoration);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("获取装饰数据失败: " + e.getMessage());
        }
        
        return decorations;
    }
    
    /**
     * 触发装饰效果
     */
    private void triggerDecorationEffects(Village village, String decorationType, Block block) {
        switch (decorationType) {
            case "street_light":
                enableStreetLightEffect(village, block);
                break;
            case "flower_bed":
                enableFlowerBedEffect(village, block);
                break;
            case "bench":
                enableBenchEffect(village, block);
                break;
        }
    }
    
    /**
     * 启用路灯效果
     */
    private void enableStreetLightEffect(Village village, Block block) {
        // 在路灯周围创建光照效果
        // 这里可以添加自定义光照逻辑或使用物品
        block.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().distance(block.getLocation()) <= 8) {
                player.sendMessage("§e路灯照亮了周围的道路");
            }
        });
    }
    
    /**
     * 启用花坛效果
     */
    private void enableFlowerBedEffect(Village village, Block block) {
        // 给附近的村民增加情绪值
        // 这里可以遍历村庄的村民并增加情绪值
        // 使用 VillagerManager.getVillagers(villageId) 获取村民列表
    }
    
    /**
     * 启用长椅效果
     */
    private void enableBenchEffect(Village village, Block block) {
        // 让村民可以在长椅上"坐下"
        // 这里可以添加村民的坐下动画或位置调整逻辑
    }
    
    /**
     * 移除装饰
     */
    public boolean removeDecoration(Player player, Block block) {
        VillageDecoration decoration = findDecorationAt(block.getLocation());
        if (decoration == null) {
            player.sendMessage("§c这里没有装饰");
            return false;
        }
        
        Village village = VillageManager.getVillageById(decoration.getVillageId());
        if (village == null || !village.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c你不能移除其他村庄的装饰");
            return false;
        }
        
        // 扣除繁荣度
        ConfigurationSection decorationConfig = plugin.getConfig().getConfigurationSection(
            "decorations.items." + decoration.getDecorationType());
        if (decorationConfig != null) {
            int prosperityBoost = decorationConfig.getInt("prosperity_boost", 0);
            if (prosperityBoost > 0) {
                village.addProsperity(-prosperityBoost);
            }
        }
        
        // 移除方块
        block.setType(Material.AIR);
        
        // 从数据库删除
        deleteDecorationFromDatabase(decoration.getId());
        
        player.sendMessage("§a装饰已移除");
        return true;
    }
    
    /**
     * 查找附近的村庄
     */
    private Village findNearbyVillage(Location location) {
        List<Village> villages = VillageManager.getAllVillages();
        
        for (Village village : villages) {
            // 这里需要根据实际的村庄中心点计算距离
            // 暂时使用简单的距离检查
            Location villageLocation = getVillageLocation(village);
            if (villageLocation != null && 
                villageLocation.getWorld().equals(location.getWorld()) &&
                villageLocation.distance(location) <= 50) { // 50格范围
                return village;
            }
        }
        
        return null;
    }
    
    /**
     * 获取村庄位置（临时实现）
     */
    private Location getVillageLocation(Village village) {
        // 这里需要根据实际的村庄数据模型来获取村庄中心位置
        // 暂时返回世界出生点
        return village.getOwnerUUID() != null ? 
            plugin.getServer().getWorlds().get(0).getSpawnLocation() : null;
    }
    
    /**
     * 查找指定位置的装饰
     */
    private VillageDecoration findDecorationAt(Location location) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM decorations WHERE world = ? AND location_x = ? AND location_y = ? AND location_z = ?")) {
            
            stmt.setString(1, location.getWorld().getName());
            stmt.setDouble(2, location.getX());
            stmt.setDouble(3, location.getY());
            stmt.setDouble(4, location.getZ());
            
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new VillageDecoration(
                    rs.getInt("id"),
                    rs.getInt("village_id"),
                    rs.getString("decoration_type"),
                    rs.getString("item_type"),
                    rs.getInt("amount"),
                    rs.getDouble("location_x"),
                    rs.getDouble("location_y"),
                    rs.getDouble("location_z"),
                    rs.getString("world"),
                    rs.getTimestamp("placed_at")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查找装饰失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 保存装饰到数据库
     */
    private void saveDecorationToDatabase(int villageId, String decorationType, Material material, Location location) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO decorations (village_id, decoration_type, item_type, amount, location_x, location_y, location_z, world) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            stmt.setInt(1, villageId);
            stmt.setString(2, decorationType);
            stmt.setString(3, material.name());
            stmt.setInt(4, 1);
            stmt.setDouble(5, location.getX());
            stmt.setDouble(6, location.getY());
            stmt.setDouble(7, location.getZ());
            stmt.setString(8, location.getWorld().getName());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("保存装饰数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 从数据库删除装饰
     */
    private void deleteDecorationFromDatabase(int decorationId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM decorations WHERE id = ?")) {
            
            stmt.setInt(1, decorationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("删除装饰数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 村庄装饰数据类
     */
    public static class VillageDecoration {
        private int id;
        private int villageId;
        private String decorationType;
        private String itemType;
        private int amount;
        private double x, y, z;
        private String world;
        private java.sql.Timestamp placedAt;
        
        public VillageDecoration(int id, int villageId, String decorationType, String itemType, 
                               int amount, double x, double y, double z, String world, 
                               java.sql.Timestamp placedAt) {
            this.id = id;
            this.villageId = villageId;
            this.decorationType = decorationType;
            this.itemType = itemType;
            this.amount = amount;
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
            this.placedAt = placedAt;
        }
        
        public Location getLocation() {
            return new Location(org.bukkit.Bukkit.getServer().getWorld(world), x, y, z);
        }
        
        // Getters
        public int getId() { return id; }
        public int getVillageId() { return villageId; }
        public String getDecorationType() { return decorationType; }
        public String getItemType() { return itemType; }
        public int getAmount() { return amount; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public String getWorld() { return world; }
        public java.sql.Timestamp getPlacedAt() { return placedAt; }
    }
    
    /**
     * 解析成本字符串列表为CostEntry列表
     * 格式: "vault:100", "playerpoints:50", "itemsadder:10:item_id"
     */
    private List<CostEntry> parseCosts(List<String> costStrings) {
        List<CostEntry> costs = new ArrayList<>();
        for (String costString : costStrings) {
            try {
                String[] parts = costString.split(":");
                if (parts.length >= 2) {
                    String type = parts[0].toLowerCase();
                    double amount = Double.parseDouble(parts[1]);
                    
                    if ("itemsadder".equals(type) && parts.length >= 3) {
                        String item = parts[2];
                        costs.add(new CostEntry(type, amount, item));
                    } else {
                        costs.add(new CostEntry(type, amount));
                    }
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                plugin.getLogger().warning("无效的成本格式: " + costString);
            }
        }
        return costs;
    }
}