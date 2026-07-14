package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ProductionStatsManager {
    public static final class Stats {
        private final int attempts;
        private final int successes;
        private final int storedAmount;
        private final int misses;
        private final int blocked;
        private final long lastEventAt;

        public Stats(int attempts, int successes, int storedAmount,
                     int misses, int blocked, long lastEventAt) {
            this.attempts = attempts;
            this.successes = successes;
            this.storedAmount = storedAmount;
            this.misses = misses;
            this.blocked = blocked;
            this.lastEventAt = lastEventAt;
        }

        public int getAttempts() { return attempts; }
        public int getSuccesses() { return successes; }
        public int getStoredAmount() { return storedAmount; }
        public int getMisses() { return misses; }
        public int getBlocked() { return blocked; }
        public long getLastEventAt() { return lastEventAt; }
    }

    private static final Map<Integer, String> diagnostics = new ConcurrentHashMap<>();
    private static final Map<Integer, String> lastOutcomes = new ConcurrentHashMap<>();
    private static final AtomicLong lastCleanup = new AtomicLong();

    private ProductionStatsManager() {
    }

    public static void setDiagnostic(int villagerId, String diagnostic) {
        diagnostics.put(villagerId, diagnostic);
    }

    public static String getDiagnostic(int villagerId) {
        return diagnostics.getOrDefault(villagerId, "等待首次生产检查");
    }

    public static void setLastOutcome(int villagerId, String outcome) {
        lastOutcomes.put(villagerId, outcome);
    }

    public static String getLastOutcome(int villagerId) {
        return lastOutcomes.getOrDefault(villagerId, "暂无生产记录");
    }

    public static void record(int villageId, int villagerId, String profession, String itemType,
                              int attemptedAmount, int storedAmount, String status) {
        String sql = "INSERT INTO production_events " +
                "(village_id, villager_id, profession, item_type, attempted_amount, stored_amount, status, created_at_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setInt(2, villagerId);
            statement.setString(3, profession);
            statement.setString(4, itemType == null ? "" : itemType);
            statement.setInt(5, Math.max(0, attemptedAmount));
            statement.setInt(6, Math.max(0, storedAmount));
            statement.setString(7, status);
            statement.setLong(8, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("记录生产统计失败: " + e.getMessage());
        }
        cleanupIfDue();
    }

    public static Map<Integer, Stats> getRecentStats(int villageId, long sinceMillis) {
        Map<Integer, Stats> result = new HashMap<>();
        String sql = "SELECT villager_id, COUNT(*) AS attempts, " +
                "SUM(CASE WHEN stored_amount > 0 THEN 1 ELSE 0 END) AS successes, " +
                "SUM(stored_amount) AS stored, " +
                "SUM(CASE WHEN status = 'MISS' THEN 1 ELSE 0 END) AS misses, " +
                "SUM(CASE WHEN status IN ('WAREHOUSE_FULL', 'DROPPED', 'PRODUCTION_DISABLED', 'INVALID_CONFIG', " +
                "'MISSING_INPUT', 'DATABASE_ERROR') " +
                "THEN 1 ELSE 0 END) AS blocked, MAX(created_at_ms) AS last_event " +
                "FROM production_events WHERE village_id = ? AND created_at_ms >= ? GROUP BY villager_id";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setLong(2, sinceMillis);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.put(resultSet.getInt("villager_id"), new Stats(
                        resultSet.getInt("attempts"), resultSet.getInt("successes"),
                        resultSet.getInt("stored"), resultSet.getInt("misses"),
                        resultSet.getInt("blocked"), resultSet.getLong("last_event")));
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取生产统计失败: " + e.getMessage());
        }
        return result;
    }

    private static void cleanupIfDue() {
        long now = System.currentTimeMillis();
        long previous = lastCleanup.get();
        if (now - previous < 6L * 60 * 60 * 1000 || !lastCleanup.compareAndSet(previous, now)) {
            return;
        }
        int retentionDays = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("production_stats.retention_days", 7));
        long cutoff = now - retentionDays * 24L * 60 * 60 * 1000;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM production_events WHERE created_at_ms < ?")) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("清理生产统计失败: " + e.getMessage());
        }
    }
}
