package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class CaravanManager {
    private static final AtomicLong LAST_CLEANUP = new AtomicLong();
    private static BukkitTask task;

    private CaravanManager() {
    }

    public static void initialize() {
        if (!isEnabled()) return;
        long interval = Math.max(200L, VillagerPro.getInstance().getConfig()
                .getLong("caravans.check_interval_ticks", 1200L));
        task = VillagerPro.getInstance().getServer().getScheduler().runTaskTimer(
                VillagerPro.getInstance(), CaravanManager::resolveArrivals, interval, interval);
    }

    public static void shutdown() {
        if (task != null) task.cancel();
        task = null;
        LAST_CLEANUP.set(0);
    }

    public static List<String> getDestinations() {
        ConfigurationSection section = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("caravans.destinations");
        if (section == null) return Collections.emptyList();
        return section.getKeys(false).stream().sorted().toList();
    }

    public static void showRoutes(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return;
        }
        resolveArrivals();
        List<Route> routes = loadRoutes(village.getId());
        player.sendMessage("§6§l商队贸易路线");
        if (routes.isEmpty()) player.sendMessage("§7暂无商队记录");
        for (Route route : routes) {
            String detail = switch (route.status()) {
                case "traveling" -> "§e运输中，剩余 " + formatRemaining(route.arrivesAtMs() - System.currentTimeMillis());
                case "ready" -> "§a已抵达，使用 /village caravan claim " + route.id();
                case "failed" -> "§c途中损失";
                case "claimed" -> "§7已领取";
                default -> "§7" + route.status();
            };
            player.sendMessage("§f#" + route.id() + " " + route.destinationId() + " §7"
                    + route.cargoAmount() + " " + route.cargoItem() + " -> "
                    + route.returnAmount() + " " + route.returnItem() + " " + detail);
        }
        player.sendMessage("§7可用目的地：" + String.join(", ", getDestinations()));
    }

    public static boolean dispatch(Player player, String destinationInput,
                                   String itemInput, int amount) {
        if (!isEnabled()) {
            player.sendMessage("§c商队系统未启用");
            return false;
        }
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        CrisisManager.ActiveCrisis crisis = CrisisManager.getActiveCrisis(village.getId());
        if (crisis != null && crisis.type() == CrisisManager.CrisisType.TRADE_BLOCKADE) {
            player.sendMessage("§c贸易封锁期间无法派出商队");
            return false;
        }
        String destination = destinationInput.toLowerCase(Locale.ROOT);
        String path = "caravans.destinations." + destination;
        if (!VillagerPro.getInstance().getConfig().isConfigurationSection(path)) {
            player.sendMessage("§c未知目的地，可用：" + String.join(", ", getDestinations()));
            return false;
        }
        String item = itemInput.toUpperCase(Locale.ROOT);
        List<String> accepted = VillagerPro.getInstance().getConfig().getStringList(path + ".accepted_items")
                .stream().map(value -> value.toUpperCase(Locale.ROOT)).toList();
        if (!accepted.contains(item)) {
            player.sendMessage("§c该路线不接收 " + item + "，可运送：" + String.join(", ", accepted));
            return false;
        }
        int minimum = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("caravans.minimum_cargo", 8));
        int maximum = Math.max(minimum, VillagerPro.getInstance().getConfig()
                .getInt("caravans.maximum_cargo", 64));
        if (amount < minimum || amount > maximum) {
            player.sendMessage("§c单次货物数量必须在 " + minimum + "-" + maximum + " 之间");
            return false;
        }
        int maxActive = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("caravans.max_active_routes", 2));
        if (countActive(village.getId()) >= maxActive) {
            player.sendMessage("§c同时运输或待领取的商队已达到上限 " + maxActive);
            return false;
        }
        if (WarehouseManager.getExtractableAmount(village.getId(), item) < amount) {
            player.sendMessage("§c可用货物不足；仓库保留量和政策冻结库存不会被运走");
            return false;
        }
        String returnItem = VillagerPro.getInstance().getConfig()
                .getString(path + ".return_item", "EMERALD").toUpperCase(Locale.ROOT);
        if (Material.getMaterial(returnItem) == null) {
            player.sendMessage("§c路线回程物品配置无效");
            return false;
        }
        double cargoPrice = MarketManager.getConfiguredBasePrice(item);
        double returnPrice = MarketManager.getConfiguredBasePrice(returnItem);
        double valueMultiplier = VillagerPro.getInstance().getConfig()
                .getDouble(path + ".return_value_multiplier", 0.75)
                * PolicyManager.getMarketSellMultiplier(village.getId());
        int returnAmount = GameplayMath.caravanReturnAmount(
                amount, cargoPrice, returnPrice, valueMultiplier);
        if (returnAmount <= 0) {
            player.sendMessage("§c货物价值不足以换回一个 " + returnItem);
            return false;
        }
        double risk = GameplayMath.caravanRisk(
                VillagerPro.getInstance().getConfig().getDouble(path + ".risk_chance", 0.10),
                village.getLevel(), VillagerPro.getInstance().getConfig()
                        .getDouble("caravans.risk_reduction_per_village_level", 0.02));
        boolean successful = ThreadLocalRandom.current().nextDouble() >= risk;
        long now = System.currentTimeMillis();
        long durationMinutes = Math.max(1, VillagerPro.getInstance().getConfig()
                .getLong(path + ".duration_minutes", 60));
        int configuredReserve = WarehouseRuleManager.getItemRule(
                village.getId(), item).getReserveAmount();
        double frozenFraction = PolicyManager.getFrozenStockFraction(village.getId());
        try (Connection connection = DatabaseManager.getConnection()) {
            if (!OperationTransactions.dispatchCaravan(connection, village.getId(), item, amount,
                    configuredReserve, frozenFraction,
                    destination, returnItem, returnAmount, successful, now,
                    now + durationMinutes * 60_000L)) {
                player.sendMessage("§c货物状态已经变化，商队未出发且没有扣除物品");
                return false;
            }
        } catch (SQLException exception) {
            logFailure("派出商队", exception);
            player.sendMessage("§c商队保存失败，货物没有被扣除");
            return false;
        }
        player.sendMessage("§a商队已出发，预计 " + durationMinutes + " 分钟后返回 "
                + returnAmount + " 个 " + returnItem + "；途中损失风险 "
                + String.format(Locale.ROOT, "%.0f%%", risk * 100));
        return true;
    }

    public static boolean claim(Player player, int routeId) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        resolveArrivals();
        Route route = loadRoute(routeId);
        if (route == null || route.villageId() != village.getId()) {
            player.sendMessage("§c商队记录不存在或不属于你的村庄");
            return false;
        }
        if (!"ready".equals(route.status())) {
            player.sendMessage("§c该商队当前不可领取，状态：" + route.status());
            return false;
        }
        int currentStorage = WarehouseManager.getCurrentStorage(village.getId());
        if (GameplayMath.storableAmount(route.returnAmount(), village.getWarehouseCapacity(), currentStorage)
                < route.returnAmount()) {
            player.sendMessage("§c仓库空间不足，回程货物会继续等待，不会丢失");
            return false;
        }
        try (Connection connection = DatabaseManager.getConnection()) {
            if (!OperationTransactions.claimCaravan(connection, DatabaseManager.getDialect(),
                    route.id(), village.getId(), route.returnItem(), route.returnAmount())) {
                player.sendMessage("§c该商队已经被领取或状态发生变化");
                return false;
            }
        } catch (SQLException exception) {
            logFailure("领取商队", exception);
            player.sendMessage("§c领取失败，路线状态和仓库均未改变");
            return false;
        }
        player.sendMessage("§a已领取 " + route.returnAmount() + " 个 " + route.returnItem());
        return true;
    }

    private static void resolveArrivals() {
        long now = System.currentTimeMillis();
        List<Route> due = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, destination_id, cargo_item, cargo_amount, return_item, "
                             + "return_amount, status, successful, departed_at_ms, arrives_at_ms "
                             + "FROM caravan_routes WHERE status = 'traveling' AND arrives_at_ms <= ?")) {
            statement.setLong(1, now);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) due.add(readRoute(resultSet));
        } catch (SQLException exception) {
            logFailure("读取到达商队", exception);
            return;
        }
        for (Route route : due) {
            String status = route.successful() ? "ready" : "failed";
            if (!transitionStatus(route.id(), "traveling", status)) continue;
            Village village = VillageManager.getVillageById(route.villageId());
            if (village == null) continue;
            Player owner = VillagerPro.getInstance().getServer().getPlayer(village.getOwnerUUID());
            if (owner != null) {
                owner.sendMessage(route.successful()
                        ? "§a商队 #" + route.id() + " 已抵达，可领取回程货物"
                        : "§c商队 #" + route.id() + " 在途中损失了货物");
            }
        }
        cleanupHistoryIfDue(now);
    }

    private static void cleanupHistoryIfDue(long now) {
        long previous = LAST_CLEANUP.get();
        if (now - previous < 86_400_000L || !LAST_CLEANUP.compareAndSet(previous, now)) return;
        int retentionDays = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("caravans.history_retention_days", 30));
        long cutoff = now - retentionDays * 86_400_000L;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM caravan_routes WHERE status IN ('claimed', 'failed') AND arrives_at_ms < ?")) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("清理商队历史", exception);
        }
    }

    private static int countActive(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM caravan_routes WHERE village_id = ? "
                             + "AND status IN ('traveling', 'ready')")) {
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException exception) {
            logFailure("统计在途商队", exception);
            return Integer.MAX_VALUE;
        }
    }

    private static List<Route> loadRoutes(int villageId) {
        List<Route> routes = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, destination_id, cargo_item, cargo_amount, return_item, "
                             + "return_amount, status, successful, departed_at_ms, arrives_at_ms "
                             + "FROM caravan_routes WHERE village_id = ? ORDER BY id DESC LIMIT 10")) {
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) routes.add(readRoute(resultSet));
        } catch (SQLException exception) {
            logFailure("读取商队列表", exception);
        }
        return routes;
    }

    private static Route loadRoute(int routeId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, destination_id, cargo_item, cargo_amount, return_item, "
                             + "return_amount, status, successful, departed_at_ms, arrives_at_ms "
                             + "FROM caravan_routes WHERE id = ?")) {
            statement.setInt(1, routeId);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next() ? readRoute(resultSet) : null;
        } catch (SQLException exception) {
            logFailure("读取商队", exception);
            return null;
        }
    }

    private static Route readRoute(ResultSet resultSet) throws SQLException {
        return new Route(resultSet.getInt("id"), resultSet.getInt("village_id"),
                resultSet.getString("destination_id"), resultSet.getString("cargo_item"),
                resultSet.getInt("cargo_amount"), resultSet.getString("return_item"),
                resultSet.getInt("return_amount"), resultSet.getString("status"),
                resultSet.getBoolean("successful"), resultSet.getLong("departed_at_ms"),
                resultSet.getLong("arrives_at_ms"));
    }

    private static boolean transitionStatus(int routeId, String expectedStatus, String nextStatus) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE caravan_routes SET status = ? WHERE id = ? AND status = ?")) {
            statement.setString(1, nextStatus);
            statement.setInt(2, routeId);
            statement.setString(3, expectedStatus);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            logFailure("更新商队状态", exception);
            return false;
        }
    }

    private static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.caravans", true)
                && VillagerPro.getInstance().getConfig().getBoolean("caravans.enabled", true);
    }

    private static String formatRemaining(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        return minutes >= 60 ? (minutes / 60) + "小时" + (minutes % 60) + "分钟" : minutes + "分钟";
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }

    private record Route(int id, int villageId, String destinationId, String cargoItem,
                         int cargoAmount, String returnItem, int returnAmount, String status,
                         boolean successful, long departedAtMs, long arrivesAtMs) {
    }
}
