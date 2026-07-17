package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.models.VillagerNeeds;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NeedsManager {
    private static BukkitTask needsTask;
    private static final Map<Integer, VillagerNeeds> NEEDS_CACHE = new ConcurrentHashMap<>();

    private NeedsManager() {
    }

    public static void initialize() {
        if (!isEnabled()) return;
        long interval = Math.max(200L, VillagerPro.getInstance().getConfig()
                .getLong("needs.check_interval_ticks", 1200L));
        needsTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllVillagers();
            }
        }.runTaskTimer(VillagerPro.getInstance(), interval, interval);
    }

    public static void shutdown() {
        if (needsTask != null) needsTask.cancel();
        needsTask = null;
        NEEDS_CACHE.clear();
    }

    public static VillagerNeeds getNeeds(int villagerId) {
        VillagerNeeds cached = NEEDS_CACHE.get(villagerId);
        if (cached != null) return cached;
        String sql = "SELECT villager_id, hunger, comfort, health, last_updated_ms, last_consumed " +
                "FROM villager_needs WHERE villager_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villagerId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                VillagerNeeds needs = readNeeds(resultSet);
                NEEDS_CACHE.put(villagerId, needs);
                return needs;
            }
        } catch (SQLException e) {
            logFailure("读取村民需求", e);
        }
        VillagerNeeds initial = new VillagerNeeds(
                villagerId, 100, 100, 100, System.currentTimeMillis(), "");
        saveNeeds(initial);
        return initial;
    }

    public static void removeNeeds(int villagerId) {
        NEEDS_CACHE.remove(villagerId);
    }

    public static double getProductionMultiplier(VillagerData villager) {
        if (!isEnabled()) return 1.0;
        double lowest = getNeeds(villager.getId()).getLowestValue();
        double excellent = VillagerPro.getInstance().getConfig()
                .getDouble("needs.efficiency.excellent_threshold", 75);
        double poor = VillagerPro.getInstance().getConfig()
                .getDouble("needs.efficiency.poor_threshold", 40);
        double critical = VillagerPro.getInstance().getConfig()
                .getDouble("needs.efficiency.critical_threshold", 20);
        return GameplayMath.needsMultiplier(lowest, excellent,
                VillagerPro.getInstance().getConfig()
                        .getDouble("needs.efficiency.excellent_bonus", 0.10),
                poor, VillagerPro.getInstance().getConfig()
                        .getDouble("needs.efficiency.poor_penalty", 0.20),
                critical, VillagerPro.getInstance().getConfig()
                        .getDouble("needs.efficiency.critical_penalty", 0.40));
    }

    private static void updateAllVillagers() {
        for (Village village : VillageManager.getAllVillages()) {
            for (VillagerData villager : VillagerManager.getVillagers(village.getId())) {
                updateVillager(village, villager);
            }
        }
    }

    private static void updateVillager(Village village, VillagerData villager) {
        VillagerNeeds current = getNeeds(villager.getId());
        long now = System.currentTimeMillis();
        double elapsedHours = Math.min(24.0,
                Math.max(0, now - current.getLastUpdatedMs()) / 3_600_000.0);
        double decayMultiplier = BuildingManager.getNeedsDecayMultiplier(village.getId())
                * PolicyManager.getNeedsDecayMultiplier(village.getId())
                * CrisisManager.getNeedsDecayMultiplier(village.getId());
        double hunger = clamp(current.getHunger() - elapsedHours * decayMultiplier * VillagerPro.getInstance().getConfig()
                .getDouble("needs.decay.hunger_per_hour", 8));
        double comfort = clamp(current.getComfort() - elapsedHours * decayMultiplier * VillagerPro.getInstance().getConfig()
                .getDouble("needs.decay.comfort_per_hour", 5));
        double health = clamp(current.getHealth() - elapsedHours * decayMultiplier * VillagerPro.getInstance().getConfig()
                .getDouble("needs.decay.health_per_hour", 3));
        if (WorkstationManager.isEnabled()
                && WorkstationManager.getOperationalIssue(villager) == null) {
            comfort = clamp(comfort + elapsedHours * VillagerPro.getInstance().getConfig()
                    .getDouble("needs.workstation_comfort_per_hour", 6));
        }

        double threshold = VillagerPro.getInstance().getConfig()
                .getDouble("needs.auto_consume_threshold", 65)
                + PolicyManager.getConsumeThresholdBonus(village.getId());
        threshold = Math.max(0, Math.min(100, threshold));
        List<String> foodItems = getSupplyItems("needs.supplies.food.items", "BREAD", "CARROT", "POTATO");
        List<String> comfortItems = getSupplyItems("needs.supplies.comfort.items", "WHITE_WOOL", "WHITE_CARPET");
        List<String> healthItems = getSupplyItems("needs.supplies.health.items", "POTION");
        Map<String, Integer> reserves = supplyReserves(village.getId(), foodItems, comfortItems, healthItems);
        double supplyMultiplier = PolicyManager.getSupplyRestoreMultiplier(village.getId());
        OperationTransactions.NeedSupply food = new OperationTransactions.NeedSupply(foodItems,
                VillagerPro.getInstance().getConfig().getDouble("needs.supplies.food.restore", 25)
                        * supplyMultiplier);
        OperationTransactions.NeedSupply comfortSupply = new OperationTransactions.NeedSupply(comfortItems,
                VillagerPro.getInstance().getConfig().getDouble("needs.supplies.comfort.restore", 20)
                        * supplyMultiplier);
        OperationTransactions.NeedSupply healthSupply = new OperationTransactions.NeedSupply(healthItems,
                VillagerPro.getInstance().getConfig().getDouble("needs.supplies.health.restore", 30)
                        * BuildingManager.getHealthSupplyMultiplier(village.getId()) * supplyMultiplier);
        try (Connection connection = DatabaseManager.getConnection()) {
            OperationTransactions.NeedsSettlement settlement = OperationTransactions.settleVillagerNeeds(
                    connection, village.getId(), villager.getId(), current.getLastUpdatedMs(), now,
                    hunger, comfort, health, current.getLastConsumed(), threshold, food, comfortSupply,
                    healthSupply, reserves, PolicyManager.getFrozenStockFraction(village.getId()));
            if (!settlement.saved()) {
                VillagerPro.getInstance().getLogger().warning("需求结算状态已变化，库存与需求均未更新: villager="
                        + villager.getId());
            } else {
                NEEDS_CACHE.put(villager.getId(), new VillagerNeeds(villager.getId(),
                        settlement.hunger(), settlement.comfort(), settlement.health(), now,
                        settlement.consumed().isBlank() ? current.getLastConsumed() : settlement.consumed()));
            }
        } catch (SQLException exception) {
            logFailure("结算村民需求", exception);
        }
    }

    private static List<String> getSupplyItems(String path, String... defaults) {
        List<String> configured = VillagerPro.getInstance().getConfig().getStringList(path);
        return configured.isEmpty() ? java.util.Arrays.asList(defaults) : configured;
    }

    private static boolean saveNeeds(VillagerNeeds needs) {
        String sql = DatabaseManager.upsert("INSERT INTO villager_needs " +
                "(villager_id, hunger, comfort, health, last_updated_ms, last_consumed) " +
                "VALUES (?, ?, ?, ?, ?, ?)", new String[]{"villager_id"},
                "hunger", "comfort", "health", "last_updated_ms", "last_consumed");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, needs.getVillagerId());
            statement.setDouble(2, needs.getHunger());
            statement.setDouble(3, needs.getComfort());
            statement.setDouble(4, needs.getHealth());
            statement.setLong(5, needs.getLastUpdatedMs());
            statement.setString(6, needs.getLastConsumed());
            boolean saved = statement.executeUpdate() > 0;
            if (saved) NEEDS_CACHE.put(needs.getVillagerId(), needs);
            return saved;
        } catch (SQLException e) {
            logFailure("保存村民需求", e);
            return false;
        }
    }

    private static VillagerNeeds readNeeds(ResultSet resultSet) throws SQLException {
        return new VillagerNeeds(resultSet.getInt("villager_id"),
                resultSet.getDouble("hunger"), resultSet.getDouble("comfort"),
                resultSet.getDouble("health"), resultSet.getLong("last_updated_ms"),
                resultSet.getString("last_consumed"));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }


    @SafeVarargs
    private static Map<String, Integer> supplyReserves(int villageId, List<String>... supplies) {
        Map<String, Integer> reserves = new HashMap<>();
        for (List<String> supply : supplies) {
            for (String item : supply) {
                reserves.putIfAbsent(item, WarehouseRuleManager.getItemRule(villageId, item)
                        .getReserveAmount());
            }
        }
        return reserves;
    }

    public static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.needs", true)
                && VillagerPro.getInstance().getConfig().getBoolean("needs.enabled", true);
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
