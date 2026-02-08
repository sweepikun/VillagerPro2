package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 村民个性管理器
 * 负责管理村民的忠诚度、情绪等个性化属性
 */
public class PersonalityManager {
    
    private static PersonalityManager instance;
    private final Map<UUID, VillagerPersonality> personalities = new HashMap<>();
    private final VillagerPro plugin;
    
    public static PersonalityManager getInstance() {
        if (instance == null) {
            instance = new PersonalityManager();
        }
        return instance;
    }
    
    private PersonalityManager() {
        this.plugin = VillagerPro.getInstance();
    }
    
    /**
     * 获取村民个性数据
     */
    public VillagerPersonality getVillagerPersonality(VillagerData villager) {
        if (villager == null) return null;
        
        return personalities.computeIfAbsent(villager.getEntityUUID(), 
            uuid -> loadPersonalityFromDatabase(villager));
    }
    
    /**
     * 与村民互动
     */
    public boolean interactWithVillager(Player player, VillagerData villager, String interactionType) {
        VillagerPersonality personality = getVillagerPersonality(villager);
        if (personality == null) return false;
        
        boolean success = false;
        int loyaltyChange = 0;
        int moodChange = 0;
        
        switch (interactionType.toLowerCase()) {
            case "gift":
                success = giveGift(player, villager);
                if (success) {
                    loyaltyChange = plugin.getConfig().getInt("personality.interactions.gift_boost", 10);
                    moodChange = loyaltyChange;
                }
                break;
            case "protect":
                success = protectVillager(villager);
                if (success) {
                    loyaltyChange = plugin.getConfig().getInt("personality.interactions.protection_reward", 5);
                    moodChange = loyaltyChange;
                }
                break;
            case "praise":
                success = praiseVillager(player, villager);
                if (success) {
                    moodChange = 5;
                }
                break;
        }
        
        if (success) {
            personality.addLoyalty(loyaltyChange);
            personality.addMood(moodChange);
            personality.setLastInteraction(System.currentTimeMillis());
            personality.incrementInteractionCount();
            
            // 保存到数据库
            savePersonalityToDatabase(villager, personality);
            
            // 检查是否触发特殊效果
            checkSpecialEffects(villager, personality);
            
            return true;
        }
        
        return false;
    }
    
    /**
     * 送礼互动
     */
    private boolean giveGift(Player player, VillagerData villager) {
        // 检查玩家背包中是否有礼物
        // 这里可以配置不同的礼物类型
        if (player.getInventory().contains(org.bukkit.Material.CAKE)) {
            player.getInventory().removeItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CAKE));
            return true;
        }
        
        if (player.getInventory().contains(org.bukkit.Material.DANDELION)) {
            player.getInventory().removeItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DANDELION));
            return true;
        }
        
        return false;
    }
    
    /**
     * 保护村民
     */
    private boolean protectVillager(VillagerData villager) {
        // 检查村民是否受到攻击或处于危险中
        // 这里可以添加更复杂的逻辑
        
        // 给村民一个短暂的保护效果
        if (villager.getEntity() instanceof Villager) {
            Villager bukkitVillager = (Villager) villager.getEntity();
            bukkitVillager.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 300, 1));
            return true;
        }
        
        return false;
    }
    
    /**
     * 赞扬村民
     */
    private boolean praiseVillager(Player player, VillagerData villager) {
        // 简单的互动，只需要靠近即可
        return true;
    }
    
    /**
     * 检查特殊效果
     */
    private void checkSpecialEffects(VillagerData villager, VillagerPersonality personality) {
        int loyalty = personality.getLoyalty();
        int mood = personality.getMood();
        
        // 高忠诚度效果
        if (loyalty >= 80) {
            enableHighLoyaltyEffects(villager);
        }
        
        // 满情绪效果
        if (mood >= 100) {
            enableMaxMoodEffects(villager);
        }
        
        // 低忠诚度警告
        if (loyalty <= plugin.getConfig().getInt("personality.loyalty.leave_threshold", 20)) {
            enableLowLoyaltyWarning(villager);
        }
    }
    
    /**
     * 启用高忠诚度效果
     */
    private void enableHighLoyaltyEffects(VillagerData villager) {
        if (villager.getEntity() instanceof Villager) {
            Villager bukkitVillager = (Villager) villager.getEntity();
            // 村民发光效果
            bukkitVillager.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 1));
            
            // 提示玩家
            bukkitVillager.getWorld().getPlayers().forEach(player -> {
                if (player.getLocation().distance(bukkitVillager.getLocation()) <= 5) {
                    player.sendMessage("§a" + villager.getProfession() + " 对村庄非常忠诚！");
                }
            });
        }
    }
    
    /**
     * 启用满情绪效果
     */
    private void enableMaxMoodEffects(VillagerData villager) {
        if (villager.getEntity() instanceof Villager) {
            Villager bukkitVillager = (Villager) villager.getEntity();
            // 村民心情愉悦，移动更快
            bukkitVillager.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
        }
    }
    
    /**
     * 启用低忠诚度警告
     */
    private void enableLowLoyaltyWarning(VillagerData villager) {
        if (villager.getEntity() instanceof Villager) {
            Villager bukkitVillager = (Villager) villager.getEntity();
            // 村民显示悲伤粒子效果
            bukkitVillager.getWorld().getPlayers().forEach(player -> {
                if (player.getLocation().distance(bukkitVillager.getLocation()) <= 10) {
                    player.sendMessage("§c警告: " + villager.getProfession() + " 的忠诚度很低，可能离开村庄！");
                }
            });
        }
    }
    
    /**
     * 从数据库加载个性数据
     */
    private VillagerPersonality loadPersonalityFromDatabase(VillagerData villager) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM villager_personality WHERE villager_id = ?")) {
            
            stmt.setInt(1, villager.getId());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new VillagerPersonality(
                    rs.getInt("villager_id"),
                    rs.getInt("loyalty"),
                    rs.getInt("mood"),
                    rs.getLong("last_interaction"),
                    rs.getInt("interaction_count")
                );
            }
            
            // 如果没有找到记录，创建默认记录
            return createDefaultPersonality(villager);
            
        } catch (SQLException e) {
            plugin.getLogger().warning("加载村民个性数据失败: " + e.getMessage());
            return createDefaultPersonality(villager);
        }
    }
    
    /**
     * 创建默认个性数据
     */
    private VillagerPersonality createDefaultPersonality(VillagerData villager) {
        int baseLoyalty = plugin.getConfig().getInt("personality.loyalty.base_loyalty", 50);
        int baseMood = plugin.getConfig().getInt("personality.mood.base_mood", 70);
        
        VillagerPersonality personality = new VillagerPersonality(
            villager.getId(), baseLoyalty, baseMood, 0, 0
        );
        
        // 保存到数据库
        savePersonalityToDatabase(villager, personality);
        
        return personality;
    }
    
    /**
     * 保存个性数据到数据库
     */
    private void savePersonalityToDatabase(VillagerData villager, VillagerPersonality personality) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT OR REPLACE INTO villager_personality (villager_id, loyalty, mood, last_interaction, interaction_count) VALUES (?, ?, ?, ?, ?)")) {
            
            stmt.setInt(1, personality.getVillagerId());
            stmt.setInt(2, personality.getLoyalty());
            stmt.setInt(3, personality.getMood());
            stmt.setLong(4, personality.getLastInteraction());
            stmt.setInt(5, personality.getInteractionCount());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("保存村民个性数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 村民个性数据类
     */
    public static class VillagerPersonality {
        private int villagerId;
        private int loyalty;
        private int mood;
        private long lastInteraction;
        private int interactionCount;
        
        public VillagerPersonality(int villagerId, int loyalty, int mood, long lastInteraction, int interactionCount) {
            this.villagerId = villagerId;
            this.loyalty = loyalty;
            this.mood = mood;
            this.lastInteraction = lastInteraction;
            this.interactionCount = interactionCount;
        }
        
        public void addLoyalty(int amount) {
            this.loyalty = Math.max(0, Math.min(100, this.loyalty + amount));
        }
        
        public void addMood(int amount) {
            this.mood = Math.max(0, Math.min(100, this.mood + amount));
        }
        
        public void setLastInteraction(long timestamp) {
            this.lastInteraction = timestamp;
        }
        
        public void incrementInteractionCount() {
            this.interactionCount++;
        }
        
        // Getters and Setters
        public int getVillagerId() { return villagerId; }
        public int getLoyalty() { return loyalty; }
        public int getMood() { return mood; }
        public long getLastInteraction() { return lastInteraction; }
        public int getInteractionCount() { return interactionCount; }
    }
}