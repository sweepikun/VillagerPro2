package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 防御系统管理器
 * 负责管理村庄防御机制，包括守卫召唤、自动避难所等
 */
public class DefenseManager {
    
    private static DefenseManager instance;
    private final VillagerPro plugin;
    private final CostHandler costHandler;
    private final Random random;
    private final List<ActiveGuard> activeGuards;
    private final Map<Integer, Boolean> activeShelters;
    private final Map<Integer, Map<UUID, Location>> shelterOriginalLocations;
    
    public static DefenseManager getInstance() {
        if (instance == null) {
            instance = new DefenseManager();
        }
        return instance;
    }
    
    private DefenseManager() {
        this.plugin = VillagerPro.getInstance();
        this.costHandler = new CostHandler();
        this.random = new Random();
        this.activeGuards = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.activeShelters = new HashMap<>();
        this.shelterOriginalLocations = new HashMap<>();
        
        // 启动定时任务
        startDefenseTasks();
    }
    
    /**
     * 召唤守卫
     */
    public boolean summonGuard(Player player, Village village) {
        // 检查功能是否启用
        if (!plugin.getConfig().getBoolean("features.defense", true) || 
            !plugin.getConfig().getBoolean("defense.enabled", true)) {
            player.sendMessage("§c防御系统已禁用");
            return false;
        }
        
        // 检查村庄等级要求
        int levelRequirement = plugin.getConfig().getInt("defense.guard.level_requirement", 3);
        if (village.getLevel() < levelRequirement) {
            player.sendMessage("§c需要村庄等级 " + levelRequirement + " 才能召唤守卫");
            return false;
        }
        
        // 检查召唤成本
        List<CostEntry> parsedCosts = parseDefenseCosts("defense.guard.cost");
        if (!costHandler.canAfford(player, parsedCosts)) {
            player.sendMessage("§c资源不足，无法召唤守卫");
            return false;
        }
        if (!costHandler.deduct(player, parsedCosts)) {
            player.sendMessage("§c扣除资源失败，请重试");
            return false;
        }
        
        // 生成守卫
        Location summonLocation = getVillageCenter(village);
        if (summonLocation == null) {
            player.sendMessage("§c无法确定村庄中心位置");
            return false;
        }
        
        // 生成铁傀儡守卫
        IronGolem guard = summonLocation.getWorld().spawn(summonLocation, IronGolem.class);
        if (guard == null) {
            player.sendMessage("§c守卫生成失败");
            return false;
        }
        
        // 设置守卫属性
        setupGuardProperties(guard, village);
        
        // 添加到活跃守卫列表
        ActiveGuard activeGuard = new ActiveGuard(guard, village, System.currentTimeMillis());
        activeGuards.add(activeGuard);
        
        // 设置自动清理任务
        scheduleGuardCleanup(activeGuard);
        
        player.sendMessage("§a守卫召唤成功！");
        player.sendMessage("§7守卫将保护村庄 " + getGuardDuration() + " 分钟");
        
        return true;
    }
    
    /**
     * 设置守卫属性
     */
    private void setupGuardProperties(IronGolem guard, Village village) {
        // 设置守卫名字
        guard.setCustomName("§b村庄守卫 - " + village.getName());
        guard.setCustomNameVisible(true);
        
        // 让守卫更强
        guard.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(100);
        guard.setHealth(100);
        
        // 守卫攻击加成
        guard.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(15);
        
        // 守卫移动速度加成
        guard.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35);
        
        // 设置无敌（防止被误杀）
        guard.setInvulnerable(true);
        
        // 守卫不能被推动
        guard.setCollidable(false);
    }
    
    /**
     * 触发自动避难所
     */
    public void triggerAutoShelter(Village village) {
        if (!plugin.getConfig().getBoolean("defense.shelter.auto_shelter.enabled", true)) {
            return;
        }
        
        int triggerTime = plugin.getConfig().getInt("defense.shelter.auto_shelter.trigger_world_time", 13000);
        long worldTime = getVillageWorldTime(village);
        
        if (worldTime >= triggerTime && worldTime < (triggerTime + 200)) { // 避免重复触发
            activateShelter(village);
        } else if (worldTime >= 0 && worldTime < 1000) { // 天亮后解除避难
            deactivateShelter(village);
        }
    }
    
    /**
     * 激活避难所
     */
    private void activateShelter(Village village) {
        if (activeShelters.getOrDefault(village.getId(), false)) {
            return; // 已在避难状态，避免重复传送
        }
        
        Location center = getVillageCenter(village);
        if (center == null || center.getWorld() == null) return;
        
        Location shelterLocation = findSafeShelterLocation(center);
        Map<UUID, Location> originalLocations = new HashMap<>();
        
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        for (VillagerData villager : villagers) {
            org.bukkit.entity.Villager entity = villager.getEntity();
            if (entity == null || !entity.isValid()) continue;
            
            Location originalLocation = entity.getLocation().clone();
            originalLocations.put(entity.getUniqueId(), originalLocation);
            
            entity.teleport(shelterLocation);
            entity.setInvulnerable(true);
        }
        
        activeShelters.put(village.getId(), true);
        shelterOriginalLocations.put(village.getId(), originalLocations);
        
        // 通知玩家
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distance(center) <= 50) {
                player.sendMessage("§e夜晚来临，村民们已进入避难所");
            }
        }
    }
    
    /**
     * 关闭避难所（白天调用）
     */
    private void deactivateShelter(Village village) {
        if (!activeShelters.getOrDefault(village.getId(), false)) {
            return;
        }
        
        Location center = getVillageCenter(village);
        Map<UUID, Location> originalLocations = shelterOriginalLocations.get(village.getId());
        
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        for (VillagerData villager : villagers) {
            org.bukkit.entity.Villager entity = villager.getEntity();
            if (entity == null || !entity.isValid()) continue;
            
            Location originalLocation = originalLocations != null ? originalLocations.get(entity.getUniqueId()) : null;
            if (originalLocation != null && originalLocation.getWorld() != null) {
                entity.teleport(originalLocation);
            }
            entity.setInvulnerable(false);
        }
        
        activeShelters.put(village.getId(), false);
        if (originalLocations != null) {
            originalLocations.clear();
        }
        
        if (center != null && center.getWorld() != null) {
            for (Player player : center.getWorld().getPlayers()) {
                if (player.getLocation().distance(center) <= 50) {
                    player.sendMessage("§a天亮了，村民们离开避难所");
                }
            }
        }
    }
    
    /**
     * 寻找安全的避难所位置
     */
    private Location findSafeShelterLocation(Location center) {
        Location shelter = center.clone();
        // 优先在村庄中心上方寻找空气位置
        for (int y = 3; y >= -3; y--) {
            Location candidate = center.clone().add(0, y, 0);
            if (candidate.getBlock().getType() == Material.AIR && 
                candidate.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                return candidate;
            }
        }
        //  fallback：返回中心上方 3 格，即使可能有方块也会因无敌而不受伤害
        return center.clone().add(0, 3, 0);
    }
    
    /**
     * 启动防御任务
     */
    private void startDefenseTasks() {
        // 每分钟检查一次夜晚时间
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isFeatureEnabled()) {
                    return;
                }
                
                // 检查所有村庄的夜晚时间
                for (Village village : VillageManager.getAllVillages()) {
                    triggerAutoShelter(village);
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // 每分钟检查一次
        
        // 每30秒检查一次守卫状态
        new BukkitRunnable() {
            @Override
            public void run() {
                checkGuardStatus();
            }
        }.runTaskTimer(plugin, 600L, 600L); // 每30秒检查一次
    }
    
    /**
     * 检查守卫状态
     */
    private void checkGuardStatus() {
        activeGuards.removeIf(guard -> {
            if (guard.getGuard().isDead() || !guard.getGuard().isValid()) {
                // 守卫已死亡或失效
                guard.getGuard().remove();
                return true;
            }
            
            // 检查守卫是否超时
            long elapsed = System.currentTimeMillis() - guard.getSpawnTime();
            if (elapsed > getGuardDuration() * 60 * 1000) {
                // 守卫超时，自动消失
                guard.getGuard().remove();
                return true;
            }
            
            return false;
        });
    }
    
    /**
     * 安排守卫清理任务
     */
    private void scheduleGuardCleanup(ActiveGuard activeGuard) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeGuard.getGuard().isDead() || !activeGuard.getGuard().isValid()) {
                    activeGuards.remove(activeGuard);
                    this.cancel();
                }
                
                // 检查超时
                long elapsed = System.currentTimeMillis() - activeGuard.getSpawnTime();
                if (elapsed > getGuardDuration() * 60 * 1000) {
                    activeGuard.getGuard().remove();
                    activeGuards.remove(activeGuard);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // 每10秒检查一次
    }
    
    /**
     * 获取村庄世界时间
     */
    private long getVillageWorldTime(Village village) {
        Location center = getVillageCenter(village);
        if (center != null) {
            return center.getWorld().getTime();
        }
        return 0;
    }
    
    /**
     * 获取村庄中心位置
     */
    private Location getVillageCenter(Village village) {
        return village.getLocation();
    }
    
    /**
     * 获取守卫持续时间
     */
    private int getGuardDuration() {
        return plugin.getConfig().getInt("defense.guard.duration_minutes", 5);
    }
    
    /**
     * 检查功能是否启用
     */
    private boolean isFeatureEnabled() {
        return plugin.getConfig().getBoolean("features.defense", true) && 
               plugin.getConfig().getBoolean("defense.enabled", true);
    }
    
    /**
     * 获取活跃守卫列表
     */
    public List<ActiveGuard> getActiveGuards() {
        return new java.util.ArrayList<>(activeGuards);
    }
    
    /**
     * 获取村庄的活跃守卫数量
     */
    public int getActiveGuardCount(Village village) {
        return (int) activeGuards.stream()
                .filter(guard -> guard.getVillage().getId() == village.getId())
                .count();
    }
    
    /**
     * 活跃守卫数据类
     */
    public static class ActiveGuard {
        private final IronGolem guard;
        private final Village village;
        private final long spawnTime;
        
        public ActiveGuard(IronGolem guard, Village village, long spawnTime) {
            this.guard = guard;
            this.village = village;
            this.spawnTime = spawnTime;
        }
        
        public IronGolem getGuard() {
            return guard;
        }
        
        public Village getVillage() {
            return village;
        }
        
        public long getSpawnTime() {
            return spawnTime;
        }
    }
    
    /**
     * 解析防御成本配置（兼容 YAML 对象列表与旧版字符串列表）
     */
    private List<CostEntry> parseDefenseCosts(String path) {
        List<CostEntry> costs = new ArrayList<>();
        List<?> costList = plugin.getConfig().getList(path);
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
                if ("itemsadder".equalsIgnoreCase(type)) {
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
     */
    private List<CostEntry> parseLegacyCostString(String costString) {
        List<CostEntry> costs = new ArrayList<>();
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