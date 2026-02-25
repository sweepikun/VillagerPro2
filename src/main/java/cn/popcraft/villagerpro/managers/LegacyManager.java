package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 传承系统管理器
 * 负责管理村民的传承、继承技能、精英村民培养等长期玩法
 */
public class LegacyManager {
    
    private static LegacyManager instance;
    private final VillagerPro plugin;
    private final Map<String, LegacyConfig> legacyConfigs;
    private final List<LegacyRecord> legacyHistory;
    
    public static LegacyManager getInstance() {
        if (instance == null) {
            instance = new LegacyManager();
        }
        return instance;
    }
    
    private LegacyManager() {
        this.plugin = VillagerPro.getInstance();
        this.legacyConfigs = new HashMap<>();
        this.legacyHistory = new ArrayList<>();
        loadConfiguration();
    }
    
    /**
     * 加载配置
     */
    private void loadConfiguration() {
        if (!plugin.getConfig().getBoolean("features.legacy", true) || 
            !plugin.getConfig().getBoolean("legacy.enabled", true)) {
            return;
        }
        
        ConfigurationSection legacySection = plugin.getConfig().getConfigurationSection("legacy.config");
        if (legacySection != null) {
            int minLevel = legacySection.getInt("min_level", 5);
            int skillInheritancePercentage = legacySection.getInt("skill_inheritance_percentage", 50);
            String scrollItem = legacySection.getString("scroll_item", "villagerpro:legacy_scroll");
            int scrollCost = legacySection.getInt("scroll_cost", 1);
            
            legacyConfigs.put("default", new LegacyConfig(minLevel, skillInheritancePercentage, scrollItem, scrollCost));
        }
        
        plugin.getLogger().info("传承系统已启用");
    }
    
    /**
     * 检查村民是否可以传承
     */
    public boolean canRetireVillager(VillagerData villager) {
        if (!isFeatureEnabled()) return false;
        
        LegacyConfig config = getLegacyConfig();
        return villager.getLevel() >= config.getMinLevel();
    }
    
    /**
     * 执行村民传承
     */
    public boolean retireVillager(Player player, VillagerData villager) {
        if (!canRetireVillager(villager)) {
            player.sendMessage("§c该村民等级不足，无法传承！");
            return false;
        }
        
        LegacyConfig config = getLegacyConfig();
        
        // 检查是否有传承卷轴
        if (!hasLegacyScroll(player, config.getScrollCost())) {
            player.sendMessage("§c需要 " + config.getScrollCost() + " 个传承卷轴！");
            return false;
        }
        
        // 消耗传承卷轴
        if (!consumeLegacyScroll(player, config.getScrollCost())) {
            player.sendMessage("§c消耗材料失败！");
            return false;
        }
        
        // 记录传承历史
        LegacyRecord record = createLegacyRecord(villager, player);
        saveLegacyRecord(record);
        
        // 创建新的村民
        VillagerData newVillager = createInheritedVillager(villager, record, player);
        if (newVillager == null) {
            player.sendMessage("§c创建继承村民失败！");
            return false;
        }
        
        // 移除原村民
        removeOriginalVillager(villager);
        
        // 特殊效果
        triggerLegacyEffects(player, villager, newVillager);
        
        player.sendMessage("§a村民传承成功！");
        player.sendMessage("§7" + villager.getProfession() + " 退休，" + newVillager.getProfession() + " 继承了技能！");
        
        return true;
    }
    
    /**
     * 创建传承记录
     */
    private LegacyRecord createLegacyRecord(VillagerData villager, Player player) {
        int inheritancePercentage = getLegacyConfig().getSkillInheritancePercentage();
        
        // 计算继承的技能等级
        Map<String, Integer> inheritedSkillLevels = new HashMap<>();
        Map<String, Integer> originalSkillLevels = new HashMap<>();
        
        // 获取村民技能
        originalSkillLevels = getVillagerSkillLevels(villager);
        for (Map.Entry<String, Integer> entry : originalSkillLevels.entrySet()) {
            String skillId = entry.getKey();
            int originalLevel = entry.getValue();
            int inheritedLevel = (int) Math.ceil(originalLevel * inheritancePercentage / 100.0);
            inheritedSkillLevels.put(skillId, Math.max(1, inheritedLevel)); // 至少1级
        }
        
        LegacyRecord record = new LegacyRecord(
            0, // ID稍后设置
            villager.getId(),
            villager.getVillageId(),
            villager.getProfession(),
            villager.getLevel(),
            originalSkillLevels,
            inheritedSkillLevels,
            inheritancePercentage,
            player.getName(),
            new java.sql.Timestamp(System.currentTimeMillis())
        );
        
        return record;
    }
    
    /**
     * 创建继承村民
     */
    private VillagerData createInheritedVillager(VillagerData original, LegacyRecord record, Player player) {
        try {
            // 创建新村民
            VillagerData newVillager = new VillagerData(
                0, // 临时ID，稍后设置
                original.getVillageId(),
                UUID.randomUUID(),
                original.getProfession(),
                1, // 新村民从1级开始
                0, // 0经验值
                "FREE" // 默认跟随模式
            );
            
            // 保存到数据库
            int newVillagerId = saveVillagerToDatabase(newVillager);
            if (newVillagerId == -1) return null;
            
            newVillager.setId(newVillagerId);
            
            // 应用继承的技能
            applyInheritedSkills(newVillager, record);
            
            // 生成实体
            if (!spawnVillagerEntity(newVillager, original.getEntity().getLocation())) {
                deleteVillagerFromDatabase(newVillager.getId());
                return null;
            }
            
            return newVillager;
            
        } catch (Exception e) {
            plugin.getLogger().warning("创建继承村民失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 应用继承的技能
     */
    private void applyInheritedSkills(VillagerData newVillager, LegacyRecord record) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO villager_upgrades (villager_id, skill_id, level) VALUES (?, ?, ?)")) {
            
            for (Map.Entry<String, Integer> entry : record.getInheritedSkillLevels().entrySet()) {
                String skillId = entry.getKey();
                int inheritedLevel = entry.getValue();
                
                stmt.setInt(1, newVillager.getId());
                stmt.setString(2, skillId);
                stmt.setInt(3, inheritedLevel);
                stmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("保存继承技能失败: " + e.getMessage());
        }
    }
    
    /**
     * 触发传承特殊效果
     */
    private void triggerLegacyEffects(Player player, VillagerData original, VillagerData newVillager) {
        Village village = VillageManager.getVillageById(original.getVillageId());
        if (village == null) return;
        
        // 增加村庄声望
        int fameBoost = plugin.getConfig().getInt("legacy.effects.village_fame_boost", 25);
        village.addProsperity(fameBoost);
        
        // 解锁特殊村民（如果配置了）
        if (plugin.getConfig().getBoolean("legacy.effects.unlock_special_villagers", true)) {
            unlockSpecialVillagers(village, player);
        }
        
        // 播放特效
        playLegacyEffect(player, original, newVillager);
        
        // 通知附近玩家
        notifyNearbyPlayers(player, village, original, newVillager);
    }
    
    /**
     * 解锁特殊村民
     */
    private void unlockSpecialVillagers(Village village, Player player) {
        // 这里可以实现特殊村民的解锁逻辑
        // 比如增加新的职业选项
        player.sendMessage("§a村庄声望提升，解锁了新的职业可能性！");
    }
    
    /**
     * 播放传承特效
     */
    private void playLegacyEffect(Player player, VillagerData original, VillagerData newVillager) {
        org.bukkit.Location location = original.getEntity().getLocation();
        
        // 使用粒子效果
        location.getWorld().spawnParticle(
            org.bukkit.Particle.ENCHANTMENT_TABLE, 
            location, 
            50, 
            2, 2, 2, 
            1, 
            null
        );
        
        // 播放音效
        location.getWorld().playSound(location, org.bukkit.Sound.ITEM_TOTEM_USE, 2.0F, 1.0F);
    }
    
    /**
     * 通知附近玩家
     */
    private void notifyNearbyPlayers(Player player, Village village, VillagerData original, VillagerData newVillager) {
        org.bukkit.Location location = original.getEntity().getLocation();
        double radius = 20.0;
        
        for (org.bukkit.entity.Player nearbyPlayer : location.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(location) <= radius) {
                nearbyPlayer.sendMessage("§e[传承] " + player.getName() + " 的 " + original.getProfession() + " 退休了！");
                nearbyPlayer.sendMessage("§7新的 " + newVillager.getProfession() + " 继承了前辈的技能！");
            }
        }
    }
    
    /**
     * 获取村民技能等级
     */
    private Map<String, Integer> getVillagerSkillLevels(VillagerData villager) {
        Map<String, Integer> skillLevels = new HashMap<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT skill_id, level FROM villager_upgrades WHERE villager_id = ?")) {
            
            stmt.setInt(1, villager.getId());
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String skillId = rs.getString("skill_id");
                int level = rs.getInt("level");
                skillLevels.put(skillId, level);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("获取村民技能等级失败: " + e.getMessage());
        }
        
        return skillLevels;
    }
    
    /**
     * 保存传承记录
     */
    private void saveLegacyRecord(LegacyRecord record) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO legacy_records (original_villager_id, village_id, profession, original_level, skill_inheritance_percentage, player_name, inherited_skills, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                 java.sql.Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, record.getOriginalVillagerId());
            stmt.setInt(2, record.getVillageId());
            stmt.setString(3, record.getProfession());
            stmt.setInt(4, record.getOriginalLevel());
            stmt.setInt(5, record.getInheritancePercentage());
            stmt.setString(6, record.getPlayerName());
            stmt.setString(7, serializeSkillMap(record.getInheritedSkillLevels()));
            stmt.setTimestamp(8, record.getCreatedAt());
            
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    record.setId(keys.getInt(1));
                }
            }
            
            legacyHistory.add(record);
            
        } catch (SQLException e) {
            plugin.getLogger().warning("保存传承记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取村民的传承历史
     */
    public List<LegacyRecord> getVillagerLegacyHistory(int villagerId) {
        List<LegacyRecord> history = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM legacy_records WHERE original_villager_id = ? OR village_id = (SELECT village_id FROM villagers WHERE id = ?)",
                 java.sql.Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, villagerId);
            stmt.setInt(2, villagerId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                LegacyRecord record = new LegacyRecord(
                    rs.getInt("id"),
                    rs.getInt("original_villager_id"),
                    rs.getInt("village_id"),
                    rs.getString("profession"),
                    rs.getInt("original_level"),
                    deserializeSkillMap(rs.getString("inherited_skills")),
                    deserializeSkillMap(rs.getString("inherited_skills")), // 简化处理
                    rs.getInt("skill_inheritance_percentage"),
                    rs.getString("player_name"),
                    rs.getTimestamp("created_at")
                );
                history.add(record);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("获取传承历史失败: " + e.getMessage());
        }
        
        return history;
    }
    
    /**
     * 检查是否有传承卷轴
     */
    private boolean hasLegacyScroll(Player player, int amount) {
        // 检查玩家背包中的传承卷轴
        String scrollItem = plugin.getConfig().getString("legacy.config.scroll_item", "villagerpro:legacy_scroll");
        int found = 0;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (item.getType().name().equals(scrollItem) || 
                    item.hasItemMeta() && item.getItemMeta().hasLore() && 
                    item.getItemMeta().getLore().toString().contains("传承卷轴")) {
                    found += item.getAmount();
                    if (found >= amount) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 消耗传承卷轴
     */
    private boolean consumeLegacyScroll(Player player, int amount) {
        // 从玩家背包中移除传承卷轴
        String scrollItem = plugin.getConfig().getString("legacy.config.scroll_item", "villagerpro:legacy_scroll");
        int toConsume = amount;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (item.getType().name().equals(scrollItem) || 
                    item.hasItemMeta() && item.getItemMeta().hasLore() && 
                    item.getItemMeta().getLore().toString().contains("传承卷轴")) {
                    
                    int itemAmount = item.getAmount();
                    if (itemAmount <= toConsume) {
                        // 移除整个物品
                        player.getInventory().remove(item);
                        toConsume -= itemAmount;
                    } else {
                        // 移除部分物品
                        item.setAmount(itemAmount - toConsume);
                        toConsume = 0;
                    }
                    
                    if (toConsume == 0) {
                        break;
                    }
                }
            }
        }
        
        return toConsume == 0;
    }
    
    /**
     * 移除原村民
     */
    private void removeOriginalVillager(VillagerData villager) {
        if (villager.getEntity() instanceof Villager) {
            villager.getEntity().remove();
        }
        deleteVillagerFromDatabase(villager.getId());
    }
    
    /**
     * 保存村民数据到数据库
     */
    private int saveVillagerToDatabase(VillagerData villager) {
        // 简化实现，返回-1
        return -1;
    }
    
    /**
     * 从数据库删除村民
     */
    private void deleteVillagerFromDatabase(int villagerId) {
        try {
            String sql = "DELETE FROM villagers WHERE id = ?";
            
            try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
                stmt.setInt(1, villagerId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("删除村民数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成村民实体
     */
    private boolean spawnVillagerEntity(VillagerData villager, org.bukkit.Location location) {
        try {
            // 创建村民实体
            org.bukkit.entity.Villager bukkitVillager = location.getWorld().spawn(location, org.bukkit.entity.Villager.class);
            
            // 设置村民属性
            bukkitVillager.setVillagerType(getVillagerType(villager.getProfession()));
            // bukkitVillager.setLevel(villager.getLevel()); // 移除不存在的API调用
            
            // 更新数据库中的实体UUID
            String sql = "UPDATE villagers SET entity_uuid = ? WHERE id = ?";
            try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
                stmt.setString(1, bukkitVillager.getUniqueId().toString());
                stmt.setInt(2, villager.getId());
                stmt.executeUpdate();
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("生成村民实体失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取村民类型
     */
    private org.bukkit.entity.Villager.Type getVillagerType(String profession) {
        try {
            // 由于API版本问题，暂时返回null让Bukkit自动处理
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("获取村民类型失败: " + e.getMessage());
            return null;
        }
    }
    
    private LegacyConfig getLegacyConfig() {
        return legacyConfigs.get("default");
    }
    
    private boolean isFeatureEnabled() {
        return plugin.getConfig().getBoolean("features.legacy", true) && 
               plugin.getConfig().getBoolean("legacy.enabled", true);
    }
    
    private String serializeSkillMap(Map<String, Integer> skillMap) {
        // 将技能地图序列化为JSON字符串
        if (skillMap == null || skillMap.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Integer> entry : skillMap.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        
        json.append("}");
        return json.toString();
    }
    
    private Map<String, Integer> deserializeSkillMap(String serialized) {
        // 从JSON字符串反序列化技能地图
        Map<String, Integer> result = new HashMap<>();
        
        if (serialized == null || serialized.trim().isEmpty() || serialized.equals("{}")) {
            return result;
        }
        
        try {
            // 移除花括号
            String content = serialized.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            
            if (content.isEmpty()) {
                return result;
            }
            
            // 分割键值对
            String[] pairs = content.split(",");
            
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim();
                    
                    try {
                        int intValue = Integer.parseInt(value);
                        result.put(key, intValue);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("无法解析技能值: " + value);
                    }
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("反序列化技能地图失败: " + e.getMessage());
        }
        
        return result;
    }
    
    // ============== 数据类 ==============
    
    /**
     * 传承配置
     */
    public static class LegacyConfig {
        private final int minLevel;
        private final int skillInheritancePercentage;
        private final String scrollItem;
        private final int scrollCost;
        
        public LegacyConfig(int minLevel, int skillInheritancePercentage, String scrollItem, int scrollCost) {
            this.minLevel = minLevel;
            this.skillInheritancePercentage = skillInheritancePercentage;
            this.scrollItem = scrollItem;
            this.scrollCost = scrollCost;
        }
        
        public int getMinLevel() { return minLevel; }
        public int getSkillInheritancePercentage() { return skillInheritancePercentage; }
        public String getScrollItem() { return scrollItem; }
        public int getScrollCost() { return scrollCost; }
    }
    
    /**
     * 传承记录
     */
    public static class LegacyRecord {
        private int id;
        private int originalVillagerId;
        private int villageId;
        private String profession;
        private int originalLevel;
        private Map<String, Integer> originalSkillLevels;
        private Map<String, Integer> inheritedSkillLevels;
        private int inheritancePercentage;
        private String playerName;
        private java.sql.Timestamp createdAt;
        
        public LegacyRecord(int id, int originalVillagerId, int villageId, String profession, int originalLevel,
                          Map<String, Integer> originalSkillLevels, Map<String, Integer> inheritedSkillLevels,
                          int inheritancePercentage, String playerName, java.sql.Timestamp createdAt) {
            this.id = id;
            this.originalVillagerId = originalVillagerId;
            this.villageId = villageId;
            this.profession = profession;
            this.originalLevel = originalLevel;
            this.originalSkillLevels = originalSkillLevels;
            this.inheritedSkillLevels = inheritedSkillLevels;
            this.inheritancePercentage = inheritancePercentage;
            this.playerName = playerName;
            this.createdAt = createdAt;
        }
        
        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getOriginalVillagerId() { return originalVillagerId; }
        public int getVillageId() { return villageId; }
        public String getProfession() { return profession; }
        public int getOriginalLevel() { return originalLevel; }
        public Map<String, Integer> getOriginalSkillLevels() { return originalSkillLevels; }
        public Map<String, Integer> getInheritedSkillLevels() { return inheritedSkillLevels; }
        public int getInheritancePercentage() { return inheritancePercentage; }
        public String getPlayerName() { return playerName; }
        public java.sql.Timestamp getCreatedAt() { return createdAt; }
    }
}