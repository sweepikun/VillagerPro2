package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PolicyManager {
    public enum PolicyType {
        OVERTIME("overtime", "加班生产"),
        WELFARE("welfare", "福利供给"),
        EXPORT_FOCUS("export", "出口导向"),
        RESERVE_FOCUS("reserve", "储备制度");

        private final String id;
        private final String displayName;

        PolicyType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }

        public static PolicyType fromInput(String input) {
            if (input == null) return null;
            String normalized = input.toLowerCase(Locale.ROOT);
            for (PolicyType type : values()) {
                if (type.id.equals(normalized)) return type;
            }
            return null;
        }
    }

    public record ActivePolicy(int villageId, PolicyType type, long activatedAtMs, long expiresAtMs) {
    }

    private static final Map<Integer, ActivePolicy> POLICIES = new HashMap<>();

    private PolicyManager() {
    }

    public static void initialize() {
        POLICIES.clear();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT village_id, policy_id, activated_at_ms, expires_at_ms FROM village_policies");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                PolicyType type = PolicyType.fromInput(resultSet.getString("policy_id"));
                if (type == null) continue;
                ActivePolicy policy = new ActivePolicy(resultSet.getInt("village_id"), type,
                        resultSet.getLong("activated_at_ms"), resultSet.getLong("expires_at_ms"));
                if (policy.expiresAtMs() > System.currentTimeMillis()) {
                    POLICIES.put(policy.villageId(), policy);
                }
            }
        } catch (SQLException exception) {
            logFailure("加载村庄政策", exception);
        }
        cleanupExpiredRows();
    }

    public static void shutdown() {
        POLICIES.clear();
    }

    public static ActivePolicy getActivePolicy(int villageId) {
        if (!isEnabled()) return null;
        ActivePolicy policy = POLICIES.get(villageId);
        if (policy == null) return null;
        if (policy.expiresAtMs() <= System.currentTimeMillis()) {
            POLICIES.remove(villageId);
            deletePolicy(villageId);
            return null;
        }
        return policy;
    }

    public static boolean selectPolicy(Player player, PolicyType type) {
        if (!isEnabled()) {
            player.sendMessage("§c村庄政策系统未启用");
            return false;
        }
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        ActivePolicy current = getActivePolicy(village.getId());
        if (current != null) {
            player.sendMessage("§c当前政策“" + current.type().getDisplayName() + "”仍在锁定期，剩余 "
                    + formatRemaining(current.expiresAtMs() - System.currentTimeMillis()));
            return false;
        }

        long now = System.currentTimeMillis();
        long durationHours = Math.max(1, VillagerPro.getInstance().getConfig()
                .getLong("policies.duration_hours", 24));
        long durationMillis = durationHours > Long.MAX_VALUE / (60L * 60L * 1000L)
                ? Long.MAX_VALUE : durationHours * 60L * 60L * 1000L;
        long expiresAt = durationMillis > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + durationMillis;
        ActivePolicy selected = new ActivePolicy(village.getId(), type, now,
                expiresAt);
        String sql = DatabaseManager.upsert(
                "INSERT INTO village_policies (village_id, policy_id, activated_at_ms, expires_at_ms) "
                        + "VALUES (?, ?, ?, ?)", new String[]{"village_id"},
                "policy_id", "activated_at_ms", "expires_at_ms");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, selected.villageId());
            statement.setString(2, selected.type().getId());
            statement.setLong(3, selected.activatedAtMs());
            statement.setLong(4, selected.expiresAtMs());
            if (statement.executeUpdate() <= 0) return false;
            POLICIES.put(village.getId(), selected);
            player.sendMessage("§a已实行“" + type.getDisplayName() + "”，持续 " + durationHours + " 小时");
            player.sendMessage("§7" + getEffectDescription(type));
            return true;
        } catch (SQLException exception) {
            logFailure("保存村庄政策", exception);
            player.sendMessage("§c政策保存失败，请查看控制台日志");
            return false;
        }
    }

    public static void showPolicies(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return;
        }
        ActivePolicy active = getActivePolicy(village.getId());
        player.sendMessage("§6§l村庄政策");
        if (active == null) {
            player.sendMessage("§7当前没有政策，可选择以下一项：");
        } else {
            player.sendMessage("§a当前：" + active.type().getDisplayName() + " §7剩余 "
                    + formatRemaining(active.expiresAtMs() - System.currentTimeMillis()));
        }
        for (PolicyType type : PolicyType.values()) {
            player.sendMessage("§e- " + type.getId() + " §f" + type.getDisplayName()
                    + " §7" + getEffectDescription(type));
        }
    }

    public static double getProductionMultiplier(int villageId) {
        return isPolicy(villageId, PolicyType.OVERTIME)
                ? config("policies.overtime.production_multiplier", 1.20) : 1.0;
    }

    public static double getNeedsDecayMultiplier(int villageId) {
        if (isPolicy(villageId, PolicyType.OVERTIME)) {
            return config("policies.overtime.needs_decay_multiplier", 1.25);
        }
        if (isPolicy(villageId, PolicyType.WELFARE)) {
            return config("policies.welfare.needs_decay_multiplier", 0.75);
        }
        return 1.0;
    }

    public static double getSupplyRestoreMultiplier(int villageId) {
        return isPolicy(villageId, PolicyType.WELFARE)
                ? config("policies.welfare.supply_restore_multiplier", 1.20) : 1.0;
    }

    public static double getConsumeThresholdBonus(int villageId) {
        return isPolicy(villageId, PolicyType.WELFARE)
                ? config("policies.welfare.consume_threshold_bonus", 15) : 0;
    }

    public static double getMarketSellMultiplier(int villageId) {
        return isPolicy(villageId, PolicyType.EXPORT_FOCUS)
                ? config("policies.export.market_sell_multiplier", 1.10) : 1.0;
    }

    public static double getMarketBuyMultiplier(int villageId) {
        return isPolicy(villageId, PolicyType.EXPORT_FOCUS)
                ? config("policies.export.market_buy_multiplier", 1.05) : 1.0;
    }

    public static double getOrderRewardMultiplier(int villageId) {
        return isPolicy(villageId, PolicyType.EXPORT_FOCUS)
                ? config("policies.export.order_reward_multiplier", 0.85) : 1.0;
    }

    public static int applyWarehouseCapacity(int villageId, int baseCapacity) {
        int safeBase = Math.max(0, baseCapacity);
        if (!isPolicy(villageId, PolicyType.RESERVE_FOCUS)) return safeBase;
        double multiplier = config("policies.reserve.capacity_multiplier", 1.20);
        if (!Double.isFinite(multiplier) || multiplier <= 0) return safeBase;
        double adjusted = Math.floor(safeBase * multiplier);
        return adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) adjusted;
    }

    public static double getFrozenStockFraction(int villageId) {
        return isPolicy(villageId, PolicyType.RESERVE_FOCUS)
                ? Math.max(0, Math.min(0.90, config("policies.reserve.frozen_stock_fraction", 0.15))) : 0;
    }

    private static boolean isPolicy(int villageId, PolicyType expected) {
        ActivePolicy policy = getActivePolicy(villageId);
        return policy != null && policy.type() == expected;
    }

    private static String getEffectDescription(PolicyType type) {
        return switch (type) {
            case OVERTIME -> "产量提高，但温饱、舒适和健康衰减更快";
            case WELFARE -> "需求衰减降低且补给恢复更多，但村民会更早消耗仓库补给";
            case EXPORT_FOCUS -> "市场出售更有利、购买更贵，同时订单金币奖励降低";
            case RESERVE_FOCUS -> "仓库容量提高，但每类库存会冻结一部分且不能消耗";
        };
    }

    private static double config(String path, double fallback) {
        return VillagerPro.getInstance().getConfig().getDouble(path, fallback);
    }

    private static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.policies", true)
                && VillagerPro.getInstance().getConfig().getBoolean("policies.enabled", true);
    }

    private static void cleanupExpiredRows() {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM village_policies WHERE expires_at_ms <= ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("清理过期政策", exception);
        }
    }

    private static void deletePolicy(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM village_policies WHERE village_id = ?")) {
            statement.setInt(1, villageId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("清理过期政策", exception);
        }
    }

    private static String formatRemaining(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        return minutes >= 60 ? (minutes / 60) + "小时" + (minutes % 60) + "分钟" : minutes + "分钟";
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
