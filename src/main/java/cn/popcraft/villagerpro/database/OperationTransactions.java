package cn.popcraft.villagerpro.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public final class OperationTransactions {
    public record NeedSupply(List<String> candidates, double restoration) {
    }

    public record NeedsSettlement(boolean saved, double hunger, double comfort, double health,
                                  String consumed) {
    }

    public record CrisisSettlement(boolean settled, int prosperity) {
    }

    public record ProsperityAdjustment(boolean adjusted, int prosperity) {
    }

    private OperationTransactions() {
    }

    public static boolean dispatchCaravan(Connection connection, int villageId,
                                          String cargoItem, int cargoAmount,
                                          int configuredReserve, double frozenFraction,
                                          String destination, String returnItem, int returnAmount,
                                          boolean successful, long departedAt, long arrivesAt,
                                          int maxActiveRoutes)
            throws SQLException {
        return inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            try (PreparedStatement activeRoutes = connection.prepareStatement(
                    "SELECT COUNT(*) FROM caravan_routes WHERE village_id = ? "
                            + "AND status IN ('traveling', 'ready')")) {
                activeRoutes.setInt(1, villageId);
                try (ResultSet resultSet = activeRoutes.executeQuery()) {
                    if (!resultSet.next()
                            || resultSet.getInt(1) >= Math.max(1, maxActiveRoutes)) return false;
                }
            }
            try (PreparedStatement remove = connection.prepareStatement(
                    "UPDATE warehouse SET amount = amount - ? WHERE village_id = ? AND item_type = ? "
                            + "AND amount >= ? AND amount - ? >= ? AND amount - ? >= amount * ?")) {
                remove.setInt(1, cargoAmount);
                remove.setInt(2, villageId);
                remove.setString(3, cargoItem);
                remove.setInt(4, cargoAmount);
                remove.setInt(5, cargoAmount);
                remove.setInt(6, Math.max(0, configuredReserve));
                remove.setInt(7, cargoAmount);
                remove.setDouble(8, Math.max(0, Math.min(1, frozenFraction)));
                if (remove.executeUpdate() != 1) return false;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO caravan_routes (village_id, destination_id, cargo_item, cargo_amount, "
                            + "return_item, return_amount, status, successful, departed_at_ms, arrives_at_ms) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'traveling', ?, ?, ?)")) {
                insert.setInt(1, villageId);
                insert.setString(2, destination);
                insert.setString(3, cargoItem);
                insert.setInt(4, cargoAmount);
                insert.setString(5, returnItem);
                insert.setInt(6, returnAmount);
                insert.setBoolean(7, successful);
                insert.setLong(8, departedAt);
                insert.setLong(9, arrivesAt);
                return insert.executeUpdate() == 1;
            }
        });
    }

    public static boolean claimCaravan(Connection connection, DatabaseDialect dialect,
                                       int routeId, int villageId, String returnItem,
                                       int returnAmount, int warehouseCapacity) throws SQLException {
        return inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            try (PreparedStatement claim = connection.prepareStatement(
                    "UPDATE caravan_routes SET status = 'claimed' "
                            + "WHERE id = ? AND village_id = ? AND status = 'ready'")) {
                claim.setInt(1, routeId);
                claim.setInt(2, villageId);
                if (claim.executeUpdate() != 1) return false;
            }
            if (!hasSpaceFor(connection, villageId, returnAmount, warehouseCapacity)) return false;
            String sql = dialect.additiveUpsert(
                    "INSERT INTO warehouse (village_id, item_type, amount) VALUES (?, ?, ?)",
                    "village_id, item_type", "amount");
            try (PreparedStatement store = connection.prepareStatement(sql)) {
                store.setInt(1, villageId);
                store.setString(2, returnItem);
                store.setInt(3, returnAmount);
                return store.executeUpdate() > 0;
            }
        });
    }

    public static boolean contributeToCrisis(Connection connection, int villageId,
                                             String itemType, int amount,
                                             int configuredReserve, double frozenFraction,
                                             int expectedProgress, int nextProgress) throws SQLException {
        return inTransaction(connection, () -> {
            try (PreparedStatement remove = connection.prepareStatement(
                    "UPDATE warehouse SET amount = amount - ? WHERE village_id = ? AND item_type = ? "
                            + "AND amount >= ? AND amount - ? >= ? AND amount - ? >= amount * ?")) {
                remove.setInt(1, amount);
                remove.setInt(2, villageId);
                remove.setString(3, itemType);
                remove.setInt(4, amount);
                remove.setInt(5, amount);
                remove.setInt(6, Math.max(0, configuredReserve));
                remove.setInt(7, amount);
                remove.setDouble(8, Math.max(0, Math.min(1, frozenFraction)));
                if (remove.executeUpdate() != 1) return false;
            }
            try (PreparedStatement progress = connection.prepareStatement(
                    "UPDATE village_crises SET contributed_amount = ? "
                            + "WHERE village_id = ? AND status = 'active' AND contributed_amount = ?")) {
                progress.setInt(1, nextProgress);
                progress.setInt(2, villageId);
                progress.setInt(3, expectedProgress);
                return progress.executeUpdate() == 1;
            }
        });
    }

    public static CrisisSettlement settleCrisis(Connection connection, int villageId, String crisisId,
                                                 long nextRollAt, int prosperityDelta)
            throws SQLException {
        int[] resultingProsperity = {-1};
        boolean settled = inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            try (PreparedStatement state = connection.prepareStatement(
                    "UPDATE village_crises SET crisis_id = '', status = 'waiting', required_item = '', "
                            + "required_amount = 0, contributed_amount = 0, started_at_ms = 0, "
                            + "expires_at_ms = 0, next_roll_at_ms = ? "
                            + "WHERE village_id = ? AND status = 'active' AND crisis_id = ?")) {
                state.setLong(1, nextRollAt);
                state.setInt(2, villageId);
                state.setString(3, crisisId);
                if (state.executeUpdate() != 1) return false;
            }
            int currentProsperity;
            try (PreparedStatement village = connection.prepareStatement(
                    "SELECT prosperity FROM villages WHERE id = ?")) {
                village.setInt(1, villageId);
                try (ResultSet resultSet = village.executeQuery()) {
                    if (!resultSet.next()) return false;
                    currentProsperity = resultSet.getInt(1);
                }
            }
            resultingProsperity[0] = Math.max(0, currentProsperity + prosperityDelta);
            try (PreparedStatement prosperity = connection.prepareStatement(
                    "UPDATE villages SET prosperity = ? WHERE id = ?")) {
                prosperity.setInt(1, resultingProsperity[0]);
                prosperity.setInt(2, villageId);
                return prosperity.executeUpdate() == 1;
            }
        });
        return new CrisisSettlement(settled, settled ? resultingProsperity[0] : -1);
    }

    public static ProsperityAdjustment adjustVillageProsperity(Connection connection, int villageId,
                                                                int delta) throws SQLException {
        int[] resultingProsperity = {-1};
        boolean adjusted = inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            int currentProsperity;
            try (PreparedStatement village = connection.prepareStatement(
                    "SELECT prosperity FROM villages WHERE id = ?")) {
                village.setInt(1, villageId);
                try (ResultSet resultSet = village.executeQuery()) {
                    if (!resultSet.next()) return false;
                    currentProsperity = resultSet.getInt(1);
                }
            }
            resultingProsperity[0] = Math.max(0, currentProsperity + delta);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE villages SET prosperity = ? WHERE id = ?")) {
                update.setInt(1, resultingProsperity[0]);
                update.setInt(2, villageId);
                return update.executeUpdate() == 1;
            }
        });
        return new ProsperityAdjustment(adjusted, adjusted ? resultingProsperity[0] : -1);
    }

    public static NeedsSettlement settleVillagerNeeds(Connection connection, int villageId,
                                                       int villagerId, long expectedLastUpdatedMs,
                                                       long updatedAtMs, double hunger, double comfort,
                                                       double health, String previousConsumed,
                                                       double threshold, NeedSupply food,
                                                       NeedSupply comfortSupply, NeedSupply healthSupply,
                                                       Map<String, Integer> reserves,
                                                       double frozenFraction) throws SQLException {
        double[] values = {clampNeed(hunger), clampNeed(comfort), clampNeed(health)};
        String[] consumed = {""};
        boolean saved = inTransaction(connection, () -> {
            if (values[0] < threshold) {
                String item = removeFirstProtectedStock(connection, villageId, food.candidates(), reserves,
                        frozenFraction);
                if (item != null) {
                    values[0] = clampNeed(values[0] + Math.max(0, food.restoration()));
                    consumed[0] = appendConsumed(consumed[0], item);
                }
            }
            if (values[1] < threshold) {
                String item = removeFirstProtectedStock(connection, villageId, comfortSupply.candidates(), reserves,
                        frozenFraction);
                if (item != null) {
                    values[1] = clampNeed(values[1] + Math.max(0, comfortSupply.restoration()));
                    consumed[0] = appendConsumed(consumed[0], item);
                }
            }
            if (values[2] < threshold) {
                String item = removeFirstProtectedStock(connection, villageId, healthSupply.candidates(), reserves,
                        frozenFraction);
                if (item != null) {
                    values[2] = clampNeed(values[2] + Math.max(0, healthSupply.restoration()));
                    consumed[0] = appendConsumed(consumed[0], item);
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE villager_needs SET hunger = ?, comfort = ?, health = ?, last_updated_ms = ?, "
                            + "last_consumed = ? WHERE villager_id = ? AND last_updated_ms = ?")) {
                update.setDouble(1, values[0]);
                update.setDouble(2, values[1]);
                update.setDouble(3, values[2]);
                update.setLong(4, updatedAtMs);
                update.setString(5, consumed[0].isBlank() ? previousConsumed : consumed[0]);
                update.setInt(6, villagerId);
                update.setLong(7, expectedLastUpdatedMs);
                return update.executeUpdate() == 1;
            }
        });
        return new NeedsSettlement(saved, values[0], values[1], values[2], consumed[0]);
    }

    public static boolean acceptOrder(Connection connection, int orderId, int villageId,
                                      String itemType, int amount,
                                      int configuredReserve, double frozenFraction,
                                      double payoutMoney, int prosperityReward) throws SQLException {
        return inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            try (PreparedStatement remove = connection.prepareStatement(
                    "UPDATE warehouse SET amount = amount - ? WHERE village_id = ? AND item_type = ? "
                            + "AND amount >= ? AND amount - ? >= ? AND amount - ? >= amount * ?")) {
                remove.setInt(1, amount);
                remove.setInt(2, villageId);
                remove.setString(3, itemType);
                remove.setInt(4, amount);
                remove.setInt(5, amount);
                remove.setInt(6, Math.max(0, configuredReserve));
                remove.setInt(7, amount);
                remove.setDouble(8, Math.max(0, Math.min(1, frozenFraction)));
                if (remove.executeUpdate() != 1) return false;
            }
            try (PreparedStatement order = connection.prepareStatement(
                    "UPDATE village_orders SET status = 'payout_pending', payout_money = ?, "
                            + "completed_at = NULL WHERE id = ? AND village_id = ? AND status = 'pending'")) {
                order.setDouble(1, Math.max(0, payoutMoney));
                order.setInt(2, orderId);
                order.setInt(3, villageId);
                if (order.executeUpdate() != 1) return false;
            }
            int safeProsperityReward = Math.max(0, prosperityReward);
            try (PreparedStatement prosperity = connection.prepareStatement(
                    "UPDATE villages SET prosperity = CASE WHEN prosperity > ? THEN ? "
                            + "ELSE prosperity + ? END WHERE id = ?")) {
                prosperity.setInt(1, Integer.MAX_VALUE - safeProsperityReward);
                prosperity.setInt(2, Integer.MAX_VALUE);
                prosperity.setInt(3, safeProsperityReward);
                prosperity.setInt(4, villageId);
                return prosperity.executeUpdate() == 1;
            }
        });
    }

    public static boolean completeVisitorQuest(Connection connection, DatabaseDialect dialect,
                                               String playerUuid, int visitorId, String questName,
                                               int villageId, String inputItem, int inputAmount,
                                               String rewardItem, int rewardAmount,
                                               int configuredReserve, double frozenFraction,
                                               int warehouseCapacity) throws SQLException {
        return inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            if (!removeProtectedStock(connection, villageId, inputItem, inputAmount,
                    configuredReserve, frozenFraction)) return false;
            if (!hasSpaceFor(connection, villageId, rewardAmount, warehouseCapacity)) return false;
            if (!storeStock(connection, dialect, villageId, rewardItem, rewardAmount)) return false;
            try (PreparedStatement quest = connection.prepareStatement(
                    "UPDATE visitor_quests SET status = 'completed', delivered_amount = ?, "
                            + "completed_at = CURRENT_TIMESTAMP WHERE player_uuid = ? AND visitor_id = ? "
                            + "AND quest_name = ? AND status = 'accepted'")) {
                quest.setInt(1, inputAmount);
                quest.setString(2, playerUuid);
                quest.setInt(3, visitorId);
                quest.setString(4, questName);
                return quest.executeUpdate() == 1;
            }
        });
    }

    public static boolean claimFestivalReward(Connection connection, DatabaseDialect dialect,
                                              String playerUuid, String festivalClaimKey,
                                              int villageId, String rewardItem, int rewardAmount,
                                              int warehouseCapacity) throws SQLException {
        return claimFestivalReward(connection, dialect, playerUuid, festivalClaimKey,
                villageId, rewardItem, rewardAmount, warehouseCapacity,
                null, 0).claimed();
    }

    public static FestivalClaimResult claimFestivalReward(Connection connection,
                                                          DatabaseDialect dialect,
                                                          String playerUuid,
                                                          String festivalClaimKey,
                                                          int villageId, String rewardItem,
                                                          int rewardAmount, int warehouseCapacity,
                                                          String boostActivationKey,
                                                          long requestedBoostExpiry)
            throws SQLException {
        long[] boostExpiry = {0};
        boolean claimed = inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            try (PreparedStatement claim = connection.prepareStatement(dialect.insertIgnore(
                    "INSERT INTO festival_claims (player_uuid, festival_name) VALUES (?, ?)"))) {
                claim.setString(1, playerUuid);
                claim.setString(2, festivalClaimKey);
                if (claim.executeUpdate() != 1) return false;
            }
            if (rewardItem != null && !rewardItem.isBlank() && rewardAmount > 0) {
                if (!hasSpaceFor(connection, villageId, rewardAmount, warehouseCapacity)) return false;
                if (!storeStock(connection, dialect, villageId, rewardItem, rewardAmount)) return false;
            }
            if (boostActivationKey == null || boostActivationKey.isBlank()
                    || requestedBoostExpiry <= 0) return true;
            try (PreparedStatement boost = connection.prepareStatement(dialect.insertIgnore(
                    "INSERT INTO festival_boosts (festival_name, expires_at) VALUES (?, ?)"))) {
                boost.setString(1, boostActivationKey);
                boost.setLong(2, requestedBoostExpiry);
                if (boost.executeUpdate() == 1) {
                    boostExpiry[0] = requestedBoostExpiry;
                    return true;
                }
            }
            try (PreparedStatement existing = connection.prepareStatement(
                    "SELECT expires_at FROM festival_boosts WHERE festival_name = ?")) {
                existing.setString(1, boostActivationKey);
                try (ResultSet resultSet = existing.executeQuery()) {
                    if (!resultSet.next()) return false;
                    boostExpiry[0] = resultSet.getLong(1);
                    return true;
                }
            }
        });
        return new FestivalClaimResult(claimed, claimed ? boostExpiry[0] : 0);
    }

    public record FestivalClaimResult(boolean claimed, long boostExpiry) {
    }

    public static boolean completeMarketTrade(Connection connection, DatabaseDialect dialect,
                                              int villageId, String playerUuid, String itemType,
                                              boolean buying, int amount, double unitPrice,
                                              int configuredReserve, double frozenFraction,
                                              int warehouseCapacity, long dailyStartMs, int dailyLimit,
                                              long expectedTradedVolume, long expectedStateUpdatedAt,
                                              double nextPressure, long tradedAtMs) throws SQLException {
        if (amount <= 0 || unitPrice < 0 || itemType == null || itemType.isBlank()) return false;
        return inTransaction(connection, () -> {
            // Serializes the daily quota per village on both SQLite and MySQL/InnoDB.
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE villages SET id = id WHERE id = ?")) {
                lock.setInt(1, villageId);
                lock.executeUpdate();
            }
            try (PreparedStatement volume = connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM market_trades "
                            + "WHERE village_id = ? AND traded_at_ms >= ?")) {
                volume.setInt(1, villageId);
                volume.setLong(2, dailyStartMs);
                try (ResultSet resultSet = volume.executeQuery()) {
                    if (!resultSet.next() || resultSet.getLong(1) + amount > dailyLimit) return false;
                }
            }

            long nextVolume = expectedTradedVolume + amount;
            int updated;
            try (PreparedStatement state = connection.prepareStatement(
                    "UPDATE market_state SET pressure = ?, traded_volume = ?, last_updated_ms = ? "
                            + "WHERE item_type = ? AND traded_volume = ? AND last_updated_ms = ?")) {
                state.setDouble(1, nextPressure);
                state.setLong(2, nextVolume);
                state.setLong(3, tradedAtMs);
                state.setString(4, itemType);
                state.setLong(5, expectedTradedVolume);
                state.setLong(6, expectedStateUpdatedAt);
                updated = state.executeUpdate();
            }
            if (updated == 0 && expectedTradedVolume == 0 && expectedStateUpdatedAt == 0) {
                try (PreparedStatement state = connection.prepareStatement(dialect.insertIgnore(
                        "INSERT INTO market_state (item_type, pressure, traded_volume, last_updated_ms) "
                                + "VALUES (?, ?, ?, ?)"))) {
                    state.setString(1, itemType);
                    state.setDouble(2, nextPressure);
                    state.setLong(3, nextVolume);
                    state.setLong(4, tradedAtMs);
                    updated = state.executeUpdate();
                }
            }
            if (updated != 1) return false;

            if (buying) {
                if (!hasSpaceFor(connection, villageId, amount, warehouseCapacity)
                        || !storeStock(connection, dialect, villageId, itemType, amount)) return false;
            } else if (!removeProtectedStock(connection, villageId, itemType, amount,
                    configuredReserve, frozenFraction)) {
                return false;
            }

            try (PreparedStatement trade = connection.prepareStatement(
                    "INSERT INTO market_trades (village_id, player_uuid, item_type, direction, amount, "
                            + "unit_price, total_price, traded_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                trade.setInt(1, villageId);
                trade.setString(2, playerUuid);
                trade.setString(3, itemType);
                trade.setString(4, buying ? "BUY" : "SELL");
                trade.setInt(5, amount);
                trade.setDouble(6, unitPrice);
                trade.setDouble(7, unitPrice * amount);
                trade.setLong(8, tradedAtMs);
                return trade.executeUpdate() == 1;
            }
        });
    }

    public static boolean reserveVisitorProduct(Connection connection, DatabaseDialect dialect,
                                                int visitorId, String productId, String playerUuid,
                                                int stockLimit, int perPlayerLimit) throws SQLException {
        if (visitorId <= 0 || productId == null || productId.isBlank()
                || playerUuid == null || playerUuid.isBlank()
                || stockLimit <= 0 || perPlayerLimit <= 0) return false;
        return inTransaction(connection, () -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE visitors SET id = id WHERE id = ? AND active = 1")) {
                lock.setInt(1, visitorId);
                if (lock.executeUpdate() == 0 && !visitorExists(connection, visitorId)) return false;
            }

            try (PreparedStatement counts = connection.prepareStatement(
                    "SELECT COALESCE(SUM(purchase_count), 0), "
                            + "COALESCE(MAX(CASE WHEN player_uuid = ? THEN purchase_count ELSE 0 END), 0) "
                            + "FROM visitor_shop_sales WHERE visitor_id = ? AND product_id = ?")) {
                counts.setString(1, playerUuid);
                counts.setInt(2, visitorId);
                counts.setString(3, productId);
                try (ResultSet resultSet = counts.executeQuery()) {
                    if (!resultSet.next() || resultSet.getInt(1) >= stockLimit
                            || resultSet.getInt(2) >= perPlayerLimit) return false;
                }
            }

            try (PreparedStatement reserve = connection.prepareStatement(dialect.additiveUpsert(
                    "INSERT INTO visitor_shop_sales "
                            + "(visitor_id, product_id, player_uuid, purchase_count) VALUES (?, ?, ?, 1)",
                    "visitor_id, product_id, player_uuid", "purchase_count"))) {
                reserve.setInt(1, visitorId);
                reserve.setString(2, productId);
                reserve.setString(3, playerUuid);
                return reserve.executeUpdate() > 0;
            }
        });
    }

    public static boolean releaseVisitorProduct(Connection connection, int visitorId,
                                                String productId, String playerUuid) throws SQLException {
        return inTransaction(connection, () -> {
            try (PreparedStatement release = connection.prepareStatement(
                    "UPDATE visitor_shop_sales SET purchase_count = purchase_count - 1, "
                            + "updated_at = CURRENT_TIMESTAMP WHERE visitor_id = ? AND product_id = ? "
                            + "AND player_uuid = ? AND purchase_count > 0")) {
                release.setInt(1, visitorId);
                release.setString(2, productId);
                release.setString(3, playerUuid);
                if (release.executeUpdate() != 1) return false;
            }
            try (PreparedStatement cleanup = connection.prepareStatement(
                    "DELETE FROM visitor_shop_sales WHERE visitor_id = ? AND product_id = ? "
                            + "AND player_uuid = ? AND purchase_count = 0")) {
                cleanup.setInt(1, visitorId);
                cleanup.setString(2, productId);
                cleanup.setString(3, playerUuid);
                cleanup.executeUpdate();
            }
            return true;
        });
    }

    public static boolean upgradeVillageSkill(Connection connection, DatabaseDialect dialect,
                                              int villageId, String upgradeId,
                                              int expectedLevel, int maxLevel) throws SQLException {
        if (villageId <= 0 || upgradeId == null || upgradeId.isBlank()
                || expectedLevel < 0 || expectedLevel >= maxLevel) return false;
        return inTransaction(connection, () -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE villages SET id = id WHERE id = ?")) {
                lock.setInt(1, villageId);
                lock.executeUpdate();
            }
            int villageLevel;
            try (PreparedStatement village = connection.prepareStatement(
                    "SELECT level FROM villages WHERE id = ?")) {
                village.setInt(1, villageId);
                try (ResultSet resultSet = village.executeQuery()) {
                    if (!resultSet.next()) return false;
                    villageLevel = resultSet.getInt(1);
                }
            }
            int spentPoints;
            try (PreparedStatement spent = connection.prepareStatement(
                    "SELECT COALESCE(SUM(level), 0) FROM village_upgrades WHERE village_id = ?")) {
                spent.setInt(1, villageId);
                try (ResultSet resultSet = spent.executeQuery()) {
                    if (!resultSet.next()) return false;
                    spentPoints = resultSet.getInt(1);
                }
            }
            if (villageLevel - 1 - spentPoints <= 0) return false;
            if (expectedLevel == 0) {
                try (PreparedStatement insert = connection.prepareStatement(dialect.insertIgnore(
                        "INSERT INTO village_upgrades (village_id, upgrade_id, level) VALUES (?, ?, 1)"))) {
                    insert.setInt(1, villageId);
                    insert.setString(2, upgradeId);
                    return insert.executeUpdate() == 1;
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE village_upgrades SET level = level + 1 "
                            + "WHERE village_id = ? AND upgrade_id = ? AND level = ? AND level < ?")) {
                update.setInt(1, villageId);
                update.setString(2, upgradeId);
                update.setInt(3, expectedLevel);
                update.setInt(4, maxLevel);
                return update.executeUpdate() == 1;
            }
        });
    }

    public static boolean upgradeVillagerSkill(Connection connection, DatabaseDialect dialect,
                                               int villagerId, String skillId,
                                               int expectedLevel, int maxLevel) throws SQLException {
        if (villagerId <= 0 || skillId == null || skillId.isBlank()
                || expectedLevel < 0 || expectedLevel >= maxLevel) return false;
        return inTransaction(connection, () -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE villagers SET id = id WHERE id = ?")) {
                lock.setInt(1, villagerId);
                lock.executeUpdate();
            }
            if (expectedLevel == 0) {
                try (PreparedStatement insert = connection.prepareStatement(dialect.insertIgnore(
                        "INSERT INTO villager_upgrades (villager_id, skill_id, level) VALUES (?, ?, 1)"))) {
                    insert.setInt(1, villagerId);
                    insert.setString(2, skillId);
                    return insert.executeUpdate() == 1;
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE villager_upgrades SET level = level + 1 "
                            + "WHERE villager_id = ? AND skill_id = ? AND level = ? AND level < ?")) {
                update.setInt(1, villagerId);
                update.setString(2, skillId);
                update.setInt(3, expectedLevel);
                update.setInt(4, maxLevel);
                return update.executeUpdate() == 1;
            }
        });
    }

    public static boolean upgradeSpecialization(Connection connection, DatabaseDialect dialect,
                                                int villagerId, String branchId,
                                                int expectedLevel, int maxLevel) throws SQLException {
        if (villagerId <= 0 || branchId == null || branchId.isBlank()
                || expectedLevel < 0 || expectedLevel >= maxLevel) return false;
        return inTransaction(connection, () -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE villagers SET id = id WHERE id = ?")) {
                lock.setInt(1, villagerId);
                lock.executeUpdate();
            }
            if (expectedLevel == 0) {
                try (PreparedStatement insert = connection.prepareStatement(dialect.insertIgnore(
                        "INSERT INTO villager_specializations (villager_id, branch_id, level) "
                                + "VALUES (?, ?, 1)"))) {
                    insert.setInt(1, villagerId);
                    insert.setString(2, branchId);
                    return insert.executeUpdate() == 1;
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE villager_specializations SET level = level + 1 "
                            + "WHERE villager_id = ? AND branch_id = ? AND level = ? AND level < ?")) {
                update.setInt(1, villagerId);
                update.setString(2, branchId);
                update.setInt(3, expectedLevel);
                update.setInt(4, maxLevel);
                return update.executeUpdate() == 1;
            }
        });
    }

    public static boolean removeSpecialization(Connection connection, int villagerId,
                                               String expectedBranch, int expectedLevel)
            throws SQLException {
        if (villagerId <= 0 || expectedBranch == null || expectedBranch.isBlank()
                || expectedLevel <= 0) return false;
        return inTransaction(connection, () -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM villager_specializations "
                            + "WHERE villager_id = ? AND branch_id = ? AND level = ?")) {
                delete.setInt(1, villagerId);
                delete.setString(2, expectedBranch);
                delete.setInt(3, expectedLevel);
                return delete.executeUpdate() == 1;
            }
        });
    }

    public static int recruitVillager(Connection connection, DatabaseDialect dialect,
                                      int villageId, String entityUuid, String profession,
                                      int villagerLimit, String requiredProfession)
            throws SQLException {
        if (villageId <= 0 || entityUuid == null || entityUuid.isBlank()
                || profession == null || profession.isBlank() || villagerLimit <= 0) return -1;
        int[] generatedId = {-1};
        boolean recruited = inTransaction(connection, () -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE villages SET id = id WHERE id = ?")) {
                lock.setInt(1, villageId);
                lock.executeUpdate();
            }
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM villagers WHERE village_id = ?")) {
                count.setInt(1, villageId);
                try (ResultSet resultSet = count.executeQuery()) {
                    if (!resultSet.next() || resultSet.getInt(1) >= villagerLimit) return false;
                }
            }
            if (requiredProfession != null && !requiredProfession.isBlank()) {
                try (PreparedStatement prerequisite = connection.prepareStatement(
                        "SELECT 1 FROM villagers WHERE village_id = ? AND profession = ? LIMIT 1")) {
                    prerequisite.setInt(1, villageId);
                    prerequisite.setString(2, requiredProfession);
                    if (!prerequisite.executeQuery().next()) return false;
                }
            }
            String sql = dialect.insertIgnore(
                    "INSERT INTO villagers (village_id, entity_uuid, profession, level, experience, "
                            + "follow_mode) VALUES (?, ?, ?, 1, 0, 'FREE')");
            try (PreparedStatement insert = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, villageId);
                insert.setString(2, entityUuid);
                insert.setString(3, profession);
                if (insert.executeUpdate() != 1) return false;
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) return false;
                    generatedId[0] = keys.getInt(1);
                    return generatedId[0] > 0;
                }
            }
        });
        return recruited ? generatedId[0] : -1;
    }

    public static int storeWarehouseStock(Connection connection, DatabaseDialect dialect,
                                          int villageId, String itemType, int requestedAmount,
                                          int warehouseCapacity) throws SQLException {
        if (villageId <= 0 || itemType == null || itemType.isBlank()
                || requestedAmount <= 0 || warehouseCapacity <= 0) return 0;
        int[] storedAmount = {0};
        boolean stored = inTransaction(connection, () -> {
            if (!lockVillage(connection, villageId)) return false;
            long currentStorage;
            try (PreparedStatement total = connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM warehouse WHERE village_id = ?")) {
                total.setInt(1, villageId);
                try (ResultSet resultSet = total.executeQuery()) {
                    if (!resultSet.next()) return false;
                    currentStorage = Math.max(0L, resultSet.getLong(1));
                }
            }
            long remainingCapacity = Math.max(0L, (long) warehouseCapacity - currentStorage);
            storedAmount[0] = (int) Math.min(requestedAmount, remainingCapacity);
            return storedAmount[0] > 0
                    && storeStock(connection, dialect, villageId, itemType, storedAmount[0]);
        });
        return stored ? storedAmount[0] : 0;
    }

    private static boolean visitorExists(Connection connection, int visitorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM visitors WHERE id = ? AND active = 1")) {
            statement.setInt(1, visitorId);
            return statement.executeQuery().next();
        }
    }

    private static boolean removeProtectedStock(Connection connection, int villageId,
                                                String itemType, int amount,
                                                int configuredReserve, double frozenFraction)
            throws SQLException {
        if (amount <= 0) return false;
        try (PreparedStatement remove = connection.prepareStatement(
                "UPDATE warehouse SET amount = amount - ? WHERE village_id = ? AND item_type = ? "
                        + "AND amount >= ? AND amount - ? >= ? AND amount - ? >= amount * ?")) {
            remove.setInt(1, amount);
            remove.setInt(2, villageId);
            remove.setString(3, itemType);
            remove.setInt(4, amount);
            remove.setInt(5, amount);
            remove.setInt(6, Math.max(0, configuredReserve));
            remove.setInt(7, amount);
            remove.setDouble(8, Math.max(0, Math.min(1, frozenFraction)));
            return remove.executeUpdate() == 1;
        }
    }

    private static boolean hasSpaceFor(Connection connection, int villageId,
                                       int amount, int warehouseCapacity) throws SQLException {
        if (amount <= 0) return true;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM warehouse WHERE village_id = ?")) {
            statement.setInt(1, villageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return false;
                long current = Math.max(0L, resultSet.getLong(1));
                return current + (long) amount <= Math.max(0L, warehouseCapacity);
            }
        }
    }

    private static boolean storeStock(Connection connection, DatabaseDialect dialect,
                                      int villageId, String itemType, int amount) throws SQLException {
        if (amount <= 0 || itemType == null || itemType.isBlank()) return false;
        try (PreparedStatement store = connection.prepareStatement(dialect.additiveUpsert(
                "INSERT INTO warehouse (village_id, item_type, amount) VALUES (?, ?, ?)",
                "village_id, item_type", "amount"))) {
            store.setInt(1, villageId);
            store.setString(2, itemType);
            store.setInt(3, amount);
            return store.executeUpdate() > 0;
        }
    }

    private static boolean lockVillage(Connection connection, int villageId) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement(
                "UPDATE villages SET id = id WHERE id = ?")) {
            lock.setInt(1, villageId);
            lock.executeUpdate();
        }
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT 1 FROM villages WHERE id = ?")) {
            exists.setInt(1, villageId);
            return exists.executeQuery().next();
        }
    }

    private static String removeFirstProtectedStock(Connection connection, int villageId,
                                                    List<String> candidates,
                                                    Map<String, Integer> reserves,
                                                    double frozenFraction) throws SQLException {
        for (String item : candidates) {
            if (item == null || item.isBlank()) continue;
            int reserve = Math.max(0, reserves.getOrDefault(item, 0));
            if (removeProtectedStock(connection, villageId, item, 1, reserve, frozenFraction)) {
                return item;
            }
        }
        return null;
    }

    private static double clampNeed(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String appendConsumed(String current, String item) {
        return current == null || current.isBlank() ? item : current + ", " + item;
    }

    private static boolean inTransaction(Connection connection, TransactionWork work)
            throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        if (!originalAutoCommit) {
            throw new SQLException("Operation transaction requires an auto-commit connection");
        }
        connection.setAutoCommit(false);
        try {
            boolean success = work.execute();
            if (success) {
                connection.commit();
            } else {
                connection.rollback();
            }
            return success;
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @FunctionalInterface
    private interface TransactionWork {
        boolean execute() throws SQLException;
    }
}
