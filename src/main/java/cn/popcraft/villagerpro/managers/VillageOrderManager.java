package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.economy.EconomyManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillageOrder;
import cn.popcraft.villagerpro.util.GameplayMath;
import cn.popcraft.villagerpro.util.MarketMath;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class VillageOrderManager {
    private enum PayoutResult {
        COMPLETED,
        PENDING,
        UNCERTAIN
    }

    private static final class OrderTemplate {
        private final String itemType;
        private final int amount;
        private final int amountPerLevel;
        private final double rewardMoney;
        private final double rewardMoneyPerLevel;
        private final int rewardProsperity;

        private OrderTemplate(String itemType, int amount, int amountPerLevel,
                              double rewardMoney, double rewardMoneyPerLevel,
                              int rewardProsperity) {
            this.itemType = itemType;
            this.amount = amount;
            this.amountPerLevel = amountPerLevel;
            this.rewardMoney = rewardMoney;
            this.rewardMoneyPerLevel = rewardMoneyPerLevel;
            this.rewardProsperity = rewardProsperity;
        }
    }

    private VillageOrderManager() {
    }

    public static List<VillageOrder> getTodayOrders(Village village) {
        if (!VillagerPro.getInstance().getConfig().getBoolean("orders.enabled", true)) {
            return Collections.emptyList();
        }
        String dayKey = currentDayKey();
        List<VillageOrder> orders = loadOrders(village.getId(), dayKey);
        if (orders.isEmpty()) {
            generateOrders(village, dayKey);
            orders = loadOrders(village.getId(), dayKey);
        }
        List<VillageOrder> board = loadOutstandingPayouts(village.getId());
        for (VillageOrder order : orders) {
            if (board.stream().noneMatch(existing -> existing.getId() == order.getId())) {
                board.add(order);
            }
        }
        return board;
    }

    public static boolean submitOrder(Player player, Village village, int orderId, boolean automatic) {
        if (village == null || !village.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c你无权提交这个村庄的订单");
            return false;
        }
        VillageOrder order = getOrder(orderId);
        if (order == null || order.getVillageId() != village.getId()) {
            if (!automatic) player.sendMessage("§c订单不存在或不属于这个村庄");
            return false;
        }
        if (order.isCompleted()) {
            if (!automatic) player.sendMessage("§c订单已经完成");
            return false;
        }
        if (order.isPayoutProcessing()) {
            if (!automatic) player.sendMessage("§e该订单金币处于结算确认中，为防止重复发放已暂停重试；请联系管理员核对");
            return false;
        }
        if (order.isPayoutPending()) {
            PayoutResult payout = payOrder(player, order.getId(), order.getPayoutMoney());
            if (!automatic) sendPayoutResult(player, payout, order.getPayoutMoney());
            return payout != PayoutResult.PENDING;
        }
        if (!order.isPending() || !currentDayKey().equals(order.getDayKey())) {
            if (!automatic) player.sendMessage("§c订单状态无效或已经过期");
            return false;
        }
        double effectiveRewardMoney = getEffectiveRewardMoney(village, order);
        int available = WarehouseManager.getExtractableAmount(village.getId(), order.getItemType());
        if (available < order.getAmountRequired()) {
            if (!automatic) {
                player.sendMessage("§c可交付库存不足，需要 " + order.getAmountRequired()
                        + "，当前可用 " + available + "（保留量不会被消耗）");
            }
            return false;
        }
        if (effectiveRewardMoney > 0 && !EconomyManager.isVaultAvailable()) {
            if (!automatic) player.sendMessage("§c经济系统不可用，暂时无法结算该订单");
            return false;
        }
        int configuredReserve = WarehouseRuleManager.getItemRule(
                village.getId(), order.getItemType()).getReserveAmount();
        double frozenFraction = PolicyManager.getFrozenStockFraction(village.getId());
        int nextProsperity = village.getProsperity() + order.getRewardProsperity();
        try (Connection connection = DatabaseManager.getConnection()) {
            if (!OperationTransactions.acceptOrder(connection, order.getId(), village.getId(),
                    order.getItemType(), order.getAmountRequired(), configuredReserve, frozenFraction,
                    effectiveRewardMoney, nextProsperity)) {
                if (!automatic) player.sendMessage("§c库存或订单状态已经变化，本次交付未生效");
                return false;
            }
        } catch (SQLException exception) {
            VillagerPro.getInstance().getLogger().warning("原子提交订单失败: " + exception.getMessage());
            if (!automatic) player.sendMessage("§c订单提交失败，库存、繁荣度和状态均未改变");
            return false;
        }
        village.setProsperity(nextProsperity);

        PayoutResult payout = payOrder(player, order.getId(), effectiveRewardMoney);
        if (payout != PayoutResult.COMPLETED) {
            if (!automatic) sendPayoutResult(player, payout, effectiveRewardMoney);
            return true;
        }

        player.sendMessage("§a订单完成：交付 " + order.getAmountRequired() + " 个 "
                + order.getItemType() + "，获得 " + formatMoney(effectiveRewardMoney)
                + (Double.compare(effectiveRewardMoney, order.getRewardMoney()) == 0
                ? "" : "（基础奖励 " + formatMoney(order.getRewardMoney()) + "）")
                + " 金币和 " + order.getRewardProsperity() + " 繁荣度");
        return true;
    }

    private static PayoutResult payOrder(Player player, int orderId, double payoutMoney) {
        if (payoutMoney <= 0) {
            return transitionOrderStatus(orderId, "payout_pending", "completed")
                    ? PayoutResult.COMPLETED : PayoutResult.PENDING;
        }
        if (!EconomyManager.isVaultAvailable()) return PayoutResult.PENDING;
        if (!transitionOrderStatus(orderId, "payout_pending", "payout_processing")) {
            VillageOrder latest = getOrder(orderId);
            return latest != null && latest.isCompleted()
                    ? PayoutResult.COMPLETED : PayoutResult.UNCERTAIN;
        }
        if (!EconomyManager.addVaultBalance(player, payoutMoney)) {
            transitionOrderStatus(orderId, "payout_processing", "payout_pending");
            return PayoutResult.PENDING;
        }
        if (!transitionOrderStatus(orderId, "payout_processing", "completed")) {
            VillagerPro.getInstance().getLogger().severe("订单金币已发放但状态确认失败，订单ID="
                    + orderId + "，玩家=" + player.getName() + "，金额=" + payoutMoney);
            return PayoutResult.UNCERTAIN;
        }
        return PayoutResult.COMPLETED;
    }

    private static void sendPayoutResult(Player player, PayoutResult result, double payoutMoney) {
        switch (result) {
            case COMPLETED -> player.sendMessage("§a订单金币已补发：" + formatMoney(payoutMoney));
            case PENDING -> player.sendMessage("§e物资与繁荣度已结算，金币 "
                    + formatMoney(payoutMoney) + " 待发；稍后再次点击订单即可重试");
            case UNCERTAIN -> player.sendMessage("§c金币发放结果无法确认，为防止重复发放已锁定订单；请联系管理员核对");
        }
    }

    public static void tryAutoSubmit(Player player, Village village) {
        if (player == null || village == null
                || !WarehouseRuleManager.isAutoSubmitOrders(village.getId())) {
            return;
        }
        for (VillageOrder order : getTodayOrders(village)) {
            if (order.isPayoutPending()
                    || order.isPending()
                    && WarehouseManager.getExtractableAmount(village.getId(), order.getItemType())
                    >= order.getAmountRequired()) {
                submitOrder(player, village, order.getId(), true);
            }
        }
    }

    public static double getEffectiveRewardMoney(Village village, VillageOrder order) {
        if (village == null || order == null) return 0;
        double liquidationValue = MarketManager.getLiquidationValue(
                village, order.getItemType(), order.getAmountRequired());
        double capMultiplier = VillagerPro.getInstance().getConfig()
                .getDouble("orders.market_value_cap_multiplier", 1.0);
        return MarketMath.cappedOrderReward(order.getRewardMoney(),
                PolicyManager.getOrderRewardMultiplier(village.getId()),
                liquidationValue, capMultiplier);
    }

    public static boolean resolvePayoutState(Player administrator, int orderId, String targetStatus) {
        if (!"completed".equalsIgnoreCase(targetStatus)
                && !"pending".equalsIgnoreCase(targetStatus)) {
            administrator.sendMessage("§c只能确认 completed（已到账）或 pending（未到账，允许重试）");
            return false;
        }
        VillageOrder order = getOrder(orderId);
        if (order == null || !order.isPayoutProcessing()) {
            administrator.sendMessage("§c该订单不存在或不处于结算确认中");
            return false;
        }
        String next = "completed".equalsIgnoreCase(targetStatus) ? "completed" : "payout_pending";
        if (!transitionOrderStatus(orderId, "payout_processing", next)) {
            administrator.sendMessage("§c订单状态已经变化，未执行人工确认");
            return false;
        }
        administrator.sendMessage(next.equals("completed")
                ? "§a已确认订单金币到账并完成订单"
                : "§e已确认金币未到账，订单恢复为可重试发放");
        return true;
    }

    private static void generateOrders(Village village, String dayKey) {
        List<OrderTemplate> templates = loadTemplates();
        if (templates.isEmpty()) return;
        Collections.shuffle(templates, new Random(
                31L * village.getId() + LocalDate.parse(dayKey).toEpochDay()));
        int count = Math.min(Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("orders.daily_count", 3)), templates.size());
        String sql = DatabaseManager.insertIgnore("INSERT INTO village_orders " +
                "(village_id, day_key, order_slot, item_type, amount_required, " +
                "reward_money, reward_prosperity, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int slot = 0; slot < count; slot++) {
                OrderTemplate template = templates.get(slot);
                int amount = GameplayMath.scaleByVillageLevel(
                        template.amount, template.amountPerLevel, village.getLevel());
                double money = Math.max(0, template.rewardMoney
                        + Math.max(0, village.getLevel() - 1) * template.rewardMoneyPerLevel);
                statement.setInt(1, village.getId());
                statement.setString(2, dayKey);
                statement.setInt(3, slot);
                statement.setString(4, template.itemType);
                statement.setInt(5, amount);
                statement.setDouble(6, money);
                statement.setInt(7, template.rewardProsperity);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("生成每日订单失败: " + e.getMessage());
        }
    }

    private static List<OrderTemplate> loadTemplates() {
        List<OrderTemplate> templates = new ArrayList<>();
        ConfigurationSection pool = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("orders.pool");
        if (pool == null) return templates;
        for (String key : pool.getKeys(false)) {
            String path = "orders.pool." + key;
            String item = VillagerPro.getInstance().getConfig().getString(path + ".item", "");
            if (Material.getMaterial(item) == null) {
                VillagerPro.getInstance().getLogger().warning("订单 " + key + " 配置了无效物品: " + item);
                continue;
            }
            templates.add(new OrderTemplate(item,
                    Math.max(1, VillagerPro.getInstance().getConfig().getInt(path + ".amount", 1)),
                    Math.max(0, VillagerPro.getInstance().getConfig().getInt(path + ".amount_per_level", 0)),
                    Math.max(0, VillagerPro.getInstance().getConfig().getDouble(path + ".reward_money", 0)),
                    Math.max(0, VillagerPro.getInstance().getConfig().getDouble(path + ".reward_money_per_level", 0)),
                    Math.max(0, VillagerPro.getInstance().getConfig().getInt(path + ".reward_prosperity", 0))));
        }
        return templates;
    }

    private static List<VillageOrder> loadOrders(int villageId, String dayKey) {
        List<VillageOrder> orders = new ArrayList<>();
        String sql = "SELECT id, village_id, day_key, order_slot, item_type, amount_required, " +
                "reward_money, reward_prosperity, payout_money, status FROM village_orders " +
                "WHERE village_id = ? AND day_key = ? ORDER BY order_slot";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setString(2, dayKey);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) orders.add(readOrder(resultSet));
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取每日订单失败: " + e.getMessage());
        }
        return orders;
    }

    private static List<VillageOrder> loadOutstandingPayouts(int villageId) {
        List<VillageOrder> orders = new ArrayList<>();
        String sql = "SELECT id, village_id, day_key, order_slot, item_type, amount_required, "
                + "reward_money, reward_prosperity, payout_money, status FROM village_orders "
                + "WHERE village_id = ? AND status IN ('payout_pending', 'payout_processing') "
                + "ORDER BY day_key, order_slot";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) orders.add(readOrder(resultSet));
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取未结清订单失败: " + e.getMessage());
        }
        return orders;
    }

    private static VillageOrder getOrder(int orderId) {
        String sql = "SELECT id, village_id, day_key, order_slot, item_type, amount_required, " +
                "reward_money, reward_prosperity, payout_money, status FROM village_orders WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) return readOrder(resultSet);
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取订单失败: " + e.getMessage());
        }
        return null;
    }

    private static VillageOrder readOrder(ResultSet resultSet) throws SQLException {
        return new VillageOrder(resultSet.getInt("id"), resultSet.getInt("village_id"),
                resultSet.getString("day_key"), resultSet.getInt("order_slot"),
                resultSet.getString("item_type"), resultSet.getInt("amount_required"),
                resultSet.getDouble("reward_money"), resultSet.getInt("reward_prosperity"),
                resultSet.getDouble("payout_money"),
                resultSet.getString("status"));
    }

    private static boolean transitionOrderStatus(int orderId, String expectedStatus, String nextStatus) {
        String sql = "UPDATE village_orders SET status = ?, completed_at = " +
                "CASE WHEN ? = 'completed' THEN CURRENT_TIMESTAMP ELSE NULL END " +
                "WHERE id = ? AND status = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nextStatus);
            statement.setString(2, nextStatus);
            statement.setInt(3, orderId);
            statement.setString(4, expectedStatus);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("更新订单状态失败: " + e.getMessage());
            return false;
        }
    }

    private static String currentDayKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private static String formatMoney(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }
}
