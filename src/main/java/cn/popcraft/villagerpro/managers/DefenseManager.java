package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manages temporary guards and the automatic night shelter. */
public final class DefenseManager {
    private static DefenseManager instance;

    private final VillagerPro plugin;
    private final CostHandler costHandler;
    private final List<ActiveGuard> activeGuards = new CopyOnWriteArrayList<>();
    private final Map<Integer, Boolean> activeShelters = new ConcurrentHashMap<>();
    private final Map<Integer, Map<UUID, Location>> shelterOriginalLocations = new ConcurrentHashMap<>();
    private final NamespacedKey guardIdKey;
    private final NamespacedKey guardVillageKey;
    private final NamespacedKey guardExpiryKey;
    private final NamespacedKey shelterVillageKey;
    private final NamespacedKey shelterLocationKey;

    public static DefenseManager getInstance() {
        if (instance == null) instance = new DefenseManager();
        return instance;
    }

    public static void shutdownIfInitialized() {
        if (instance == null) return;
        instance.shutdown();
        instance = null;
    }

    private DefenseManager() {
        plugin = VillagerPro.getInstance();
        costHandler = new CostHandler();
        guardIdKey = new NamespacedKey(plugin, "defense_guard_id");
        guardVillageKey = new NamespacedKey(plugin, "defense_guard_village");
        guardExpiryKey = new NamespacedKey(plugin, "defense_guard_expires_at");
        shelterVillageKey = new NamespacedKey(plugin, "defense_shelter_village");
        shelterLocationKey = new NamespacedKey(plugin, "defense_shelter_origin");
        deleteExpiredGuardReservations();
        restoreLoadedEntities();
        startDefenseTasks();
    }

    public boolean summonGuard(Player player, Village village) {
        if (village == null || !village.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c你不能为其他村庄召唤守卫");
            return false;
        }
        if (!isFeatureEnabled()) {
            player.sendMessage("§c防御系统已禁用");
            return false;
        }
        int levelRequirement = plugin.getConfig().getInt("defense.guard.level_requirement", 3);
        if (village.getLevel() < levelRequirement) {
            player.sendMessage("§c需要村庄等级 " + levelRequirement + " 才能召唤守卫");
            return false;
        }
        Location summonLocation = getVillageCenter(village);
        if (summonLocation == null || summonLocation.getWorld() == null) {
            player.sendMessage("§c无法确定村庄中心位置");
            return false;
        }

        int maximum = Math.max(0, plugin.getConfig().getInt("defense.guard.max_active_per_village", 1));
        if (maximum == 0) {
            player.sendMessage("§c当前配置不允许召唤守卫");
            return false;
        }
        long expiresAt = System.currentTimeMillis() + getGuardDuration() * 60_000L;
        String guardId = UUID.randomUUID().toString();
        if (!reserveGuardSlot(village.getId(), guardId, expiresAt, maximum)) {
            player.sendMessage("§c该村庄的临时守卫已达到上限");
            return false;
        }

        List<CostEntry> costs = parseDefenseCosts("defense.guard.cost");
        if (!costHandler.canAfford(player, costs)) {
            releaseGuardReservation(guardId);
            player.sendMessage("§c资源不足，无法召唤守卫");
            return false;
        }
        if (!costHandler.deduct(player, costs)) {
            releaseGuardReservation(guardId);
            player.sendMessage("§c扣除资源失败，请重试");
            return false;
        }

        try {
            IronGolem guard = summonLocation.getWorld().spawn(summonLocation, IronGolem.class);
            tagGuard(guard, guardId, village.getId(), expiresAt);
            setupGuardProperties(guard, village);
            trackGuard(guard, village, guardId, expiresAt);
        } catch (RuntimeException exception) {
            releaseGuardReservation(guardId);
            boolean refunded = CostHandler.refund(player, costs);
            plugin.getLogger().warning("守卫生成失败: " + exception.getMessage());
            player.sendMessage(refunded ? "§c守卫生成失败，费用已退还"
                    : "§c守卫生成失败且费用未完整退还，请联系管理员");
            return false;
        }

        player.sendMessage("§a守卫召唤成功！");
        player.sendMessage("§7守卫将保护村庄 " + getGuardDuration() + " 分钟");
        return true;
    }

    private void setupGuardProperties(IronGolem guard, Village village) {
        guard.setCustomName("§b村庄守卫 - " + village.getName());
        guard.setCustomNameVisible(true);
        setAttribute(guard, Attribute.GENERIC_MAX_HEALTH,
                Math.max(1.0, plugin.getConfig().getDouble("defense.guard.max_health", 100.0)));
        guard.setHealth(guard.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        setAttribute(guard, Attribute.GENERIC_ATTACK_DAMAGE,
                Math.max(0.0, plugin.getConfig().getDouble("defense.guard.attack_damage", 15.0)));
        setAttribute(guard, Attribute.GENERIC_MOVEMENT_SPEED,
                Math.max(0.0, plugin.getConfig().getDouble("defense.guard.movement_speed", 0.35)));
        guard.setInvulnerable(plugin.getConfig().getBoolean("defense.guard.invulnerable", true));
        guard.setCollidable(false);
    }

    private void setAttribute(IronGolem guard, Attribute attribute, double value) {
        AttributeInstance instance = guard.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public void triggerAutoShelter(Village village) {
        if (!plugin.getConfig().getBoolean("defense.shelter.auto_shelter.enabled", true)) return;
        long worldTime = getVillageWorldTime(village);
        int triggerTime = plugin.getConfig().getInt("defense.shelter.auto_shelter.trigger_world_time", 13000);
        if (GameplayMath.isShelterTime(worldTime, triggerTime)) {
            activateShelter(village);
        } else {
            deactivateShelter(village);
        }
    }

    private void activateShelter(Village village) {
        if (activeShelters.getOrDefault(village.getId(), false)) return;
        Location center = getVillageCenter(village);
        if (center == null || center.getWorld() == null) return;
        Location shelter = findSafeShelterLocation(center);
        Map<UUID, Location> originals = new HashMap<>();
        for (VillagerData data : VillagerManager.getVillagers(village.getId())) {
            Villager villager = data.getEntity();
            if (villager == null || !villager.isValid()) continue;
            Location original = villager.getLocation().clone();
            if (!villager.teleport(shelter)) continue;
            originals.put(villager.getUniqueId(), original);
            tagShelteredVillager(villager, village.getId(), original);
            villager.setInvulnerable(true);
        }
        activeShelters.put(village.getId(), true);
        shelterOriginalLocations.put(village.getId(), originals);
        notifyNearby(center, "§e夜晚来临，村民们已进入避难所");
    }

    private void deactivateShelter(Village village) {
        if (!activeShelters.getOrDefault(village.getId(), false)) return;
        Map<UUID, Location> originals = shelterOriginalLocations.remove(village.getId());
        for (VillagerData data : VillagerManager.getVillagers(village.getId())) {
            Villager villager = data.getEntity();
            if (villager == null || !villager.isValid()) continue;
            releaseShelterState(villager, originals == null ? null : originals.get(villager.getUniqueId()));
        }
        activeShelters.remove(village.getId());
        Location center = getVillageCenter(village);
        if (center != null) notifyNearby(center, "§a天亮了，村民们离开避难所");
    }

    private void notifyNearby(Location center, String message) {
        if (center.getWorld() == null) return;
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distance(center) <= 50) player.sendMessage(message);
        }
    }

    private Location findSafeShelterLocation(Location center) {
        for (int y = 4; y >= -3; y--) {
            Location candidate = center.clone().add(0.5, y, 0.5);
            if (candidate.getBlock().getType().isAir()
                    && candidate.clone().add(0, 1, 0).getBlock().getType().isAir()
                    && candidate.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return candidate;
            }
        }
        return center.getWorld().getHighestBlockAt(center).getLocation().add(0.5, 1, 0.5);
    }

    private void startDefenseTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isFeatureEnabled()) return;
                for (Village village : VillageManager.getAllVillages()) triggerAutoShelter(village);
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
        new BukkitRunnable() {
            @Override
            public void run() {
                checkGuardStatus();
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void checkGuardStatus() {
        long now = System.currentTimeMillis();
        for (ActiveGuard activeGuard : activeGuards) {
            if (activeGuard.guard().isDead() || !activeGuard.guard().isValid()
                    || activeGuard.expiresAt() <= now) {
                removeGuard(activeGuard);
            }
        }
        deleteExpiredGuardReservations();
    }

    public void restoreEntityState(Entity entity) {
        if (entity instanceof IronGolem guard) restoreGuard(guard);
        if (entity instanceof Villager villager) restoreShelteredVillager(villager);
    }

    private void restoreLoadedEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) restoreEntityState(entity);
        }
    }

    private void restoreGuard(IronGolem guard) {
        PersistentDataContainer data = guard.getPersistentDataContainer();
        String guardId = data.get(guardIdKey, PersistentDataType.STRING);
        Integer villageId = data.get(guardVillageKey, PersistentDataType.INTEGER);
        Long expiresAt = data.get(guardExpiryKey, PersistentDataType.LONG);
        if (guardId == null || villageId == null || expiresAt == null) return;
        Village village = VillageManager.getVillageById(villageId);
        if (expiresAt <= System.currentTimeMillis() || village == null
                || !hasGuardReservation(guardId, villageId, expiresAt)) {
            guard.remove();
            releaseGuardReservation(guardId);
            return;
        }
        setupGuardProperties(guard, village);
        trackGuard(guard, village, guardId, expiresAt);
    }

    private void restoreShelteredVillager(Villager villager) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        if (!data.has(shelterVillageKey, PersistentDataType.INTEGER)) return;
        Location original = deserializeLocation(data.get(shelterLocationKey, PersistentDataType.STRING));
        releaseShelterState(villager, original);
    }

    private void tagGuard(IronGolem guard, String guardId, int villageId, long expiresAt) {
        PersistentDataContainer data = guard.getPersistentDataContainer();
        data.set(guardIdKey, PersistentDataType.STRING, guardId);
        data.set(guardVillageKey, PersistentDataType.INTEGER, villageId);
        data.set(guardExpiryKey, PersistentDataType.LONG, expiresAt);
    }

    private void tagShelteredVillager(Villager villager, int villageId, Location original) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.set(shelterVillageKey, PersistentDataType.INTEGER, villageId);
        data.set(shelterLocationKey, PersistentDataType.STRING, serializeLocation(original));
    }

    private void releaseShelterState(Villager villager, Location original) {
        if (original != null && original.getWorld() != null) villager.teleport(original);
        villager.setInvulnerable(false);
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.remove(shelterVillageKey);
        data.remove(shelterLocationKey);
    }

    private String serializeLocation(Location location) {
        return location.getWorld().getUID() + ";" + location.getX() + ";" + location.getY() + ";"
                + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location deserializeLocation(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(";", -1);
        if (parts.length != 6) return null;
        try {
            org.bukkit.World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) return null;
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void trackGuard(IronGolem guard, Village village, String guardId, long expiresAt) {
        activeGuards.removeIf(active -> active.guard().getUniqueId().equals(guard.getUniqueId()));
        activeGuards.add(new ActiveGuard(guard, village, guardId, expiresAt));
    }

    private void removeGuard(ActiveGuard activeGuard) {
        activeGuards.remove(activeGuard);
        if (activeGuard.guard().isValid()) activeGuard.guard().remove();
        releaseGuardReservation(activeGuard.guardId());
    }

    private boolean reserveGuardSlot(int villageId, String guardId, long expiresAt, int maximum) {
        try (Connection connection = DatabaseManager.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement removeExpired = connection.prepareStatement(
                    "DELETE FROM active_guards WHERE expires_at_ms <= ?")) {
                removeExpired.setLong(1, System.currentTimeMillis());
                removeExpired.executeUpdate();
            }
            int count;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM active_guards WHERE village_id = ? AND expires_at_ms > ?")) {
                query.setInt(1, villageId);
                query.setLong(2, System.currentTimeMillis());
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    count = rows.getInt(1);
                }
            }
            if (count >= maximum) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO active_guards (guard_id, village_id, expires_at_ms) VALUES (?, ?, ?)")) {
                insert.setString(1, guardId);
                insert.setInt(2, villageId);
                insert.setLong(3, expiresAt);
                insert.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("无法预约守卫名额: " + exception.getMessage());
            return false;
        }
    }

    private boolean hasGuardReservation(String guardId, int villageId, long expiresAt) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT 1 FROM active_guards WHERE guard_id = ? AND village_id = ? "
                             + "AND expires_at_ms = ? AND expires_at_ms > ?")) {
            query.setString(1, guardId);
            query.setInt(2, villageId);
            query.setLong(3, expiresAt);
            query.setLong(4, System.currentTimeMillis());
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("无法恢复守卫状态: " + exception.getMessage());
            return false;
        }
    }

    private void releaseGuardReservation(String guardId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM active_guards WHERE guard_id = ?")) {
            delete.setString(1, guardId);
            delete.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("无法释放守卫名额: " + exception.getMessage());
        }
    }

    private void deleteExpiredGuardReservations() {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM active_guards WHERE expires_at_ms <= ?")) {
            delete.setLong(1, System.currentTimeMillis());
            delete.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("无法清理过期守卫记录: " + exception.getMessage());
        }
    }

    private int countActiveGuardReservations(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM active_guards WHERE village_id = ? AND expires_at_ms > ?")) {
            query.setInt(1, villageId);
            query.setLong(2, System.currentTimeMillis());
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("无法读取守卫数量: " + exception.getMessage());
            long tracked = activeGuards.stream()
                    .filter(guard -> guard.village().getId() == villageId)
                    .count();
            return tracked > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tracked;
        }
    }

    private void shutdown() {
        for (ActiveGuard activeGuard : new ArrayList<>(activeGuards)) removeGuard(activeGuard);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) restoreShelteredVillager(villager);
            }
        }
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM active_guards")) {
            delete.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("无法清理守卫记录: " + exception.getMessage());
        }
    }

    private long getVillageWorldTime(Village village) {
        Location center = getVillageCenter(village);
        return center == null || center.getWorld() == null ? 0 : center.getWorld().getTime();
    }

    private Location getVillageCenter(Village village) {
        return village.getLocation();
    }

    private int getGuardDuration() {
        return Math.max(1, plugin.getConfig().getInt("defense.guard.duration_minutes", 5));
    }

    private boolean isFeatureEnabled() {
        return plugin.getConfig().getBoolean("features.defense", true)
                && plugin.getConfig().getBoolean("defense.enabled", true);
    }

    public List<ActiveGuard> getActiveGuards() {
        return new ArrayList<>(activeGuards);
    }

    public int getActiveGuardCount(Village village) {
        return countActiveGuardReservations(village.getId());
    }

    public record ActiveGuard(IronGolem guard, Village village, String guardId, long expiresAt) {
    }

    private List<CostEntry> parseDefenseCosts(String path) {
        List<CostEntry> costs = new ArrayList<>();
        List<?> costList = plugin.getConfig().getList(path);
        if (costList == null) return costs;
        for (Object entry : costList) {
            if (entry instanceof Map<?, ?> map) {
                String type = String.valueOf(map.get("type"));
                Object amountObject = map.get("amount");
                double amount = amountObject instanceof Number number ? number.doubleValue() : 0;
                if (!(amountObject instanceof Number) && amountObject != null) {
                    try {
                        amount = Double.parseDouble(String.valueOf(amountObject));
                    } catch (NumberFormatException ignored) {
                        // Invalid costs are ignored in the same way as the legacy parser.
                    }
                }
                if ("itemsadder".equalsIgnoreCase(type) || "item".equalsIgnoreCase(type)) {
                    costs.add(new CostEntry(type, amount,
                            map.get("item") == null ? "" : String.valueOf(map.get("item"))));
                } else {
                    costs.add(new CostEntry(type, amount));
                }
            } else if (entry instanceof String legacy) {
                costs.addAll(parseLegacyCostString(legacy));
            }
        }
        return costs;
    }

    private List<CostEntry> parseLegacyCostString(String costString) {
        List<CostEntry> costs = new ArrayList<>();
        String[] parts = costString.split(":");
        if (parts.length < 2) return costs;
        try {
            double amount = Double.parseDouble(parts[1]);
            if (("itemsadder".equalsIgnoreCase(parts[0]) || "item".equalsIgnoreCase(parts[0]))
                    && parts.length >= 3) {
                costs.add(new CostEntry(parts[0], amount, parts[2]));
            } else {
                costs.add(new CostEntry(parts[0], amount));
            }
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("无效的防御成本格式: " + costString);
        }
        return costs;
    }
}
