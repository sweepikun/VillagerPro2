package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        if (village == null || !village.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c你不能为其他村庄购买装饰");
            return false;
        }
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
        int prosperityBoost = decorationConfig.getInt("prosperity_boost", 0);
        
        // 解析成本（兼容 YAML 对象列表与旧版字符串列表）
        List<CostEntry> parsedCosts = parseDecorationCosts(decorationConfig.getList("cost"));
        if (parsedCosts == null) {
            parsedCosts = new ArrayList<>();
        }
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
        org.bukkit.inventory.meta.ItemMeta itemMeta = decorationItem.getItemMeta();
        itemMeta.setDisplayName("§e" + name);
        itemMeta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "decoration_item"),
                PersistentDataType.STRING, decorationType);
        decorationItem.setItemMeta(itemMeta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(decorationItem);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        
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
        if (!village.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c只能在自己的村庄放置装饰");
            return false;
        }
        
        // 检查装饰类型配置
        ConfigurationSection decorationConfig = plugin.getConfig().getConfigurationSection("decorations.items." + decorationType);
        if (decorationConfig == null) {
            return false;
        }
        
        Material material = Material.valueOf(decorationConfig.getString("material", "STONE"));
        if (!block.getType().isAir() || !saveDecorationToDatabase(
                village.getId(), decorationType, material, block.getLocation())) {
            player.sendMessage("§c这个位置无法放置装饰");
            return false;
        }
        
        // 增加繁荣度
        int prosperityBoost = decorationConfig.getInt("prosperity_boost", 0);
        if (prosperityBoost > 0) {
            village.addProsperity(prosperityBoost);
            if (!VillageManager.updateVillage(village)) {
                village.addProsperity(-prosperityBoost);
                deleteDecorationAt(block.getLocation());
                player.sendMessage("§c保存村庄繁荣度失败");
                return false;
            }
            player.sendMessage("§a村庄繁荣度 +" + prosperityBoost);
        }

        block.setType(material);
        
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
     * 启用花坛效果：提升附近村民的情绪
     */
    private void enableFlowerBedEffect(Village village, Block block) {
        // 给村庄中心附近的所有村民增加心情
        Location center = block.getLocation();
        for (VillagerData villager : VillagerManager.getVillagers(village.getId())) {
            org.bukkit.entity.Villager entity = villager.getEntity();
            if (entity == null || !entity.isValid()) continue;
            if (entity.getWorld().equals(center.getWorld()) && entity.getLocation().distance(center) <= 10) {
                PersonalityManager.getInstance().interactWithVillager(null, villager, "praise");
            }
        }
    }
    
    /**
     * 启用长椅效果：让附近村民获得缓慢/休息效果
     */
    private void enableBenchEffect(Village village, Block block) {
        Location center = block.getLocation();
        for (VillagerData villager : VillagerManager.getVillagers(village.getId())) {
            org.bukkit.entity.Villager entity = villager.getEntity();
            if (entity == null || !entity.isValid()) continue;
            if (entity.getWorld().equals(center.getWorld()) && entity.getLocation().distance(center) <= 5) {
                entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOW, 200, 1, false, false));
            }
        }
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
        
        int prosperityBoost = 0;
        ConfigurationSection decorationConfig = plugin.getConfig().getConfigurationSection(
            "decorations.items." + decoration.getDecorationType());
        if (decorationConfig != null) {
            prosperityBoost = decorationConfig.getInt("prosperity_boost", 0);
        }

        if (!deleteDecorationFromDatabase(decoration.getId())) {
            player.sendMessage("§c删除装饰数据失败");
            return false;
        }
        if (prosperityBoost > 0) {
            village.addProsperity(-prosperityBoost);
            if (!VillageManager.updateVillage(village)) {
                village.addProsperity(prosperityBoost);
                saveDecorationToDatabase(village.getId(), decoration.getDecorationType(),
                        Material.valueOf(decoration.getItemType()), block.getLocation());
                player.sendMessage("§c更新村庄繁荣度失败");
                return false;
            }
        }

        block.setType(Material.AIR);
        
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
     * 获取村庄中心位置
     */
    private Location getVillageLocation(Village village) {
        return village.getLocation();
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
    private boolean saveDecorationToDatabase(int villageId, String decorationType, Material material, Location location) {
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
            
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            plugin.getLogger().warning("保存装饰数据失败: " + e.getMessage());
            return false;
        }
    }

    private void deleteDecorationAt(Location location) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM decorations WHERE world = ? AND location_x = ? AND location_y = ? AND location_z = ?")) {
            stmt.setString(1, location.getWorld().getName());
            stmt.setDouble(2, location.getX());
            stmt.setDouble(3, location.getY());
            stmt.setDouble(4, location.getZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("回滚装饰数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 从数据库删除装饰
     */
    private boolean deleteDecorationFromDatabase(int decorationId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM decorations WHERE id = ?")) {

            stmt.setInt(1, decorationId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            plugin.getLogger().warning("删除装饰数据失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 根据ID获取装饰
     */
    public VillageDecoration getDecorationById(int decorationId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM decorations WHERE id = ?")) {
            stmt.setInt(1, decorationId);
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
            plugin.getLogger().warning("查询装饰失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 清空村庄所有装饰（含数据库与方块）
     */
    public boolean clearAllDecorations(int villageId) {
        List<VillageDecoration> decorations = getVillageDecorations(villageId);
        int prosperityReduction = 0;
        for (VillageDecoration decoration : decorations) {
            ConfigurationSection config = plugin.getConfig().getConfigurationSection(
                    "decorations.items." + decoration.getDecorationType());
            if (config != null) {
                prosperityReduction += config.getInt("prosperity_boost", 0);
            }
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM decorations WHERE village_id = ?");
                 PreparedStatement update = conn.prepareStatement(
                    "UPDATE villages SET prosperity = MAX(0, prosperity - ?) WHERE id = ?")) {
                delete.setInt(1, villageId);
                delete.executeUpdate();
                update.setInt(1, prosperityReduction);
                update.setInt(2, villageId);
                update.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

            for (VillageDecoration decoration : decorations) {
                Location loc = decoration.getLocation();
                if (loc != null) {
                    loc.getBlock().setType(Material.AIR);
                }
            }
            Village village = VillageManager.getVillageById(villageId);
            if (village != null) {
                village.setProsperity(Math.max(0, village.getProsperity() - prosperityReduction));
                CacheManager.cacheVillage(village.getOwnerUUID(), village);
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("清空装饰数据失败: " + e.getMessage());
            return false;
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
     * 解析装饰成本列表为 CostEntry 列表
     * 兼容 YAML 对象列表（type/amount/item）和旧版字符串格式
     */
    private List<CostEntry> parseDecorationCosts(List<?> costList) {
        List<CostEntry> costs = new ArrayList<>();
        if (costList == null) {
            return costs;
        }
        
        for (Object obj : costList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> costMap = (Map<String, Object>) obj;
                String type = String.valueOf(costMap.get("type"));
                Object amountObj = costMap.get("amount");
                double amount = 0;
                if (amountObj instanceof Number) {
                    amount = ((Number) amountObj).doubleValue();
                } else if (amountObj != null) {
                    try {
                        amount = Double.parseDouble(String.valueOf(amountObj));
                    } catch (NumberFormatException ignored) {
                    }
                }
                
                if ("itemsadder".equalsIgnoreCase(type) || "item".equalsIgnoreCase(type)) {
                    String item = costMap.get("item") != null ? String.valueOf(costMap.get("item")) : "";
                    costs.add(new CostEntry(type, amount, item));
                } else {
                    costs.add(new CostEntry(type, amount));
                }
            } else if (obj instanceof String) {
                costs.addAll(parseLegacyCostString((String) obj));
            }
        }
        
        return costs;
    }
    
    /**
     * 解析旧版字符串成本格式
     * 格式: "vault:100", "playerpoints:50", "itemsadder:10:item_id"
     */
    private List<CostEntry> parseLegacyCostString(String costString) {
        List<CostEntry> costs = new ArrayList<>();
        try {
            String[] parts = costString.split(":");
            if (parts.length >= 2) {
                String type = parts[0].toLowerCase();
                double amount = Double.parseDouble(parts[1]);
                
                if (("itemsadder".equals(type) || "item".equals(type)) && parts.length >= 3) {
                    String item = parts[2];
                    costs.add(new CostEntry(type, amount, item));
                } else {
                    costs.add(new CostEntry(type, amount));
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            plugin.getLogger().warning("无效的成本格式: " + costString);
        }
        return costs;
    }
    
    /**
     * 解析成本字符串列表为CostEntry列表（保留旧方法名以兼容）
     * 格式: "vault:100", "playerpoints:50", "itemsadder:10:item_id"
     */
    private List<CostEntry> parseCosts(List<String> costStrings) {
        List<CostEntry> costs = new ArrayList<>();
        if (costStrings == null) {
            return costs;
        }
        for (String costString : costStrings) {
            costs.addAll(parseLegacyCostString(costString));
        }
        return costs;
    }
}
