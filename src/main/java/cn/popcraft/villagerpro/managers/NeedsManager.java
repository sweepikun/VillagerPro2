package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
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
import java.util.List;

public final class NeedsManager {
    private static BukkitTask needsTask;

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
    }

    public static VillagerNeeds getNeeds(int villagerId) {
        String sql = "SELECT villager_id, hunger, comfort, health, last_updated_ms, last_consumed " +
                "FROM villager_needs WHERE villager_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villagerId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) return readNeeds(resultSet);
        } catch (SQLException e) {
            logFailure("读取村民需求", e);
        }
        VillagerNeeds initial = new VillagerNeeds(
                villagerId, 100, 100, 100, System.currentTimeMillis(), "");
        saveNeeds(initial);
        return initial;
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
        String consumed = "";
        if (hunger < threshold) {
            String item = consumeFirstAvailable(village.getId(),
                    getSupplyItems("needs.supplies.food.items", "BREAD", "CARROT", "POTATO"));
            if (item != null) {
                hunger = clamp(hunger + VillagerPro.getInstance().getConfig()
                        .getDouble("needs.supplies.food.restore", 25)
                        * PolicyManager.getSupplyRestoreMultiplier(village.getId()));
                consumed = item;
            }
        }
        if (comfort < threshold) {
            String item = consumeFirstAvailable(village.getId(),
                    getSupplyItems("needs.supplies.comfort.items", "WHITE_WOOL", "WHITE_CARPET"));
            if (item != null) {
                comfort = clamp(comfort + VillagerPro.getInstance().getConfig()
                        .getDouble("needs.supplies.comfort.restore", 20)
                        * PolicyManager.getSupplyRestoreMultiplier(village.getId()));
                consumed = appendConsumed(consumed, item);
            }
        }
        if (health < threshold) {
            String item = consumeFirstAvailable(village.getId(),
                    getSupplyItems("needs.supplies.health.items", "POTION"));
            if (item != null) {
                health = clamp(health + VillagerPro.getInstance().getConfig()
                        .getDouble("needs.supplies.health.restore", 30)
                        * BuildingManager.getHealthSupplyMultiplier(village.getId())
                        * PolicyManager.getSupplyRestoreMultiplier(village.getId()));
                consumed = appendConsumed(consumed, item);
            }
        }
        saveNeeds(new VillagerNeeds(villager.getId(), hunger, comfort, health, now,
                consumed.isBlank() ? current.getLastConsumed() : consumed));
    }

    private static String consumeFirstAvailable(int villageId, List<String> itemTypes) {
        for (String itemType : itemTypes) {
            if (WarehouseManager.removeExtractableItem(villageId, itemType, 1)) {
                return itemType;
            }
        }
        return null;
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
            return statement.executeUpdate() > 0;
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

    private static String appendConsumed(String current, String item) {
        return current == null || current.isBlank() ? item : current + ", " + item;
    }

    public static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.needs", true)
                && VillagerPro.getInstance().getConfig().getBoolean("needs.enabled", true);
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
