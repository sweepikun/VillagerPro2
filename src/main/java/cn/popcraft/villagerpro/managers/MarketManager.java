package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.economy.EconomyManager;
import cn.popcraft.villagerpro.models.Village;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class MarketManager {
    private static final Map<String, Double> DEFAULT_PRICES = Map.ofEntries(
            Map.entry("WHEAT", 2.0), Map.entry("CARROT", 1.5), Map.entry("POTATO", 1.5),
            Map.entry("COD", 4.0), Map.entry("SALMON", 5.0), Map.entry("WHITE_WOOL", 5.0),
            Map.entry("BOOK", 12.0), Map.entry("POTION", 20.0), Map.entry("MAP", 15.0),
            Map.entry("OAK_PLANKS", 1.0), Map.entry("EMERALD", 25.0),
            Map.entry("BREAD", 8.0), Map.entry("WHITE_CARPET", 10.0),
            Map.entry("COOKIE", 6.0), Map.entry("PAINTING", 80.0));
    private static final AtomicLong LAST_CLEANUP = new AtomicLong();

    private MarketManager() {
    }

    public static void showPrices(Player player) {
        if (!isAvailable(player)) return;
        List<MarketQuote> quotes = getQuotes();
        Village village = VillageManager.getVillage(player.getUniqueId());
        player.sendMessage("§6§l村庄市场行情 §7（全服供需）");
        for (MarketQuote quote : quotes) {
            double sellPrice = MarketMath.roundCurrency(quote.sellPrice()
                    * (village == null ? 1.0 : PolicyManager.getMarketSellMultiplier(village.getId())
                    * CrisisManager.getMarketSellMultiplier(village.getId())));
            double buyPrice = MarketMath.roundCurrency(quote.buyPrice()
                    * (village == null ? 1.0 : PolicyManager.getMarketBuyMultiplier(village.getId())
                    * CrisisManager.getMarketBuyMultiplier(village.getId())));
            player.sendMessage("§e" + quote.itemType() + " §7收购 §a"
                    + formatMoney(sellPrice) + " §7出售 §c"
                    + formatMoney(buyPrice) + " §8/个");
        }
        if (village != null) {
            player.sendMessage("§7今日交易量：" + getDailyVolume(village.getId()) + "/"
                    + getDailyLimit(village));
        }
    }

    public static boolean sell(Player player, String itemInput, int amount) {
        Village village = ownedVillage(player);
        String itemType = normalizeConfiguredItem(itemInput);
        if (village == null || !validateTrade(player, village, itemType, amount)) return false;
        int available = WarehouseManager.getExtractableAmount(village.getId(), itemType);
        if (available < amount) {
            player.sendMessage("§c可出售库存不足，当前可用 " + available + "；仓库保留量不会被出售");
            return false;
        }

        MarketQuote quote = getQuote(itemType);
        double unitPrice = MarketMath.roundCurrency(executionUnitPrice(
                itemType, quote.state(), false, amount)
                * PolicyManager.getMarketSellMultiplier(village.getId())
                * CrisisManager.getMarketSellMultiplier(village.getId()));
        double total = MarketMath.roundCurrency(unitPrice * amount);
        if (!EconomyManager.addVaultBalance(player, total)) {
            player.sendMessage("§c经济系统结算失败，仓库物品未扣除");
            return false;
        }
        if (!saveTrade(village, player, itemType, false, amount, unitPrice, quote.state())) {
            boolean moneyReverted = EconomyManager.removeVaultBalance(player, total);
            if (!moneyReverted) {
                VillagerPro.getInstance().getLogger().severe(
                        "市场出售退款失败，玩家=" + player.getName() + "，物品=" + itemType + "，数量=" + amount);
            }
            player.sendMessage("§c市场状态或交易额度已经变化，本次未扣货，金币已尝试收回");
            return false;
        }
        cleanupTradeHistoryIfDue();
        player.sendMessage("§a已出售 " + amount + " 个 " + itemType + "，获得 "
                + formatMoney(total) + " 金币");
        return true;
    }

    public static boolean buy(Player player, String itemInput, int amount) {
        Village village = ownedVillage(player);
        String itemType = normalizeConfiguredItem(itemInput);
        if (village == null || !validateTrade(player, village, itemType, amount)) return false;
        int currentStorage = WarehouseManager.getCurrentStorage(village.getId());
        if (GameplayMath.storableAmount(amount, village.getWarehouseCapacity(), currentStorage) < amount) {
            player.sendMessage("§c仓库空间不足，购买不会把物品掉落到地面");
            return false;
        }

        MarketQuote quote = getQuote(itemType);
        double unitPrice = MarketMath.roundCurrency(executionUnitPrice(
                itemType, quote.state(), true, amount)
                * PolicyManager.getMarketBuyMultiplier(village.getId())
                * CrisisManager.getMarketBuyMultiplier(village.getId()));
        double total = MarketMath.roundCurrency(unitPrice * amount);
        if (!EconomyManager.hasVaultBalance(player, total)
                || !EconomyManager.removeVaultBalance(player, total)) {
            player.sendMessage("§c金币不足或经济系统扣款失败，需要 " + formatMoney(total));
            return false;
        }
        if (!saveTrade(village, player, itemType, true, amount, unitPrice, quote.state())) {
            boolean moneyRestored = EconomyManager.addVaultBalance(player, total);
            if (!moneyRestored) {
                VillagerPro.getInstance().getLogger().severe(
                        "市场购买退款失败，玩家=" + player.getName() + "，物品=" + itemType + "，数量=" + amount);
            }
            player.sendMessage("§c市场状态、交易额度或仓库空间已经变化，金币已尝试退回");
            return false;
        }
        cleanupTradeHistoryIfDue();
        player.sendMessage("§a已购买 " + amount + " 个 " + itemType + "，花费 "
                + formatMoney(total) + " 金币，物品已进入村庄仓库");
        return true;
    }

    public static List<String> getConfiguredItems() {
        ConfigurationSection section = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("market.items");
        if (section == null) {
            return DEFAULT_PRICES.keySet().stream().sorted().toList();
        }
        List<String> items = new ArrayList<>();
        for (String item : section.getKeys(false)) {
            if (Material.matchMaterial(item) != null && section.getDouble(item + ".base_price") > 0) {
                items.add(item.toUpperCase(Locale.ROOT));
            }
        }
        items.sort(String::compareTo);
        return items;
    }

    public static double getConfiguredBasePrice(String itemInput) {
        if (itemInput == null) return 0;
        String item = itemInput.toUpperCase(Locale.ROOT);
        return Math.max(0, VillagerPro.getInstance().getConfig()
                .getDouble("market.items." + item + ".base_price",
                        DEFAULT_PRICES.getOrDefault(item, 0.0)));
    }

    public static double getLiquidationValue(Village village, String itemType, int amount) {
        if (village == null || itemType == null || amount <= 0) return 0;
        String normalized = itemType.toUpperCase(Locale.ROOT);
        if (!getConfiguredItems().contains(normalized)) return 0;
        MarketQuote quote = getQuote(normalized);
        double unitPrice = MarketMath.roundCurrency(executionUnitPrice(
                normalized, quote.state(), false, amount)
                * PolicyManager.getMarketSellMultiplier(village.getId())
                * CrisisManager.getMarketSellMultiplier(village.getId()));
        return MarketMath.roundCurrency(unitPrice * amount);
    }

    public static List<MarketQuote> getQuotes() {
        return getConfiguredItems().stream().map(MarketManager::getQuote)
                .sorted(Comparator.comparing(MarketQuote::itemType)).toList();
    }

    public static MarketQuote getQuote(String itemType) {
        String normalized = itemType.toUpperCase(Locale.ROOT);
        double basePrice = VillagerPro.getInstance().getConfig()
                .getDouble("market.items." + normalized + ".base_price",
                        DEFAULT_PRICES.getOrDefault(normalized, 0.0));
        MarketState stored = loadState(normalized);
        long now = System.currentTimeMillis();
        double pressure = MarketMath.decayPressure(stored.pressure(),
                Math.max(0, now - stored.lastUpdatedMs()), VillagerPro.getInstance().getConfig()
                        .getDouble("market.pressure_half_life_hours", 24));
        double spot = MarketMath.spotPrice(basePrice, pressure,
                VillagerPro.getInstance().getConfig().getDouble("market.price_sensitivity", 1.0),
                VillagerPro.getInstance().getConfig().getDouble("market.minimum_price_multiplier", 0.5),
                VillagerPro.getInstance().getConfig().getDouble("market.maximum_price_multiplier", 2.0));
        double sell = MarketMath.roundCurrency(MarketMath.tradePrice(spot, false,
                VillagerPro.getInstance().getConfig().getDouble("market.sell_factor", 0.9),
                VillagerPro.getInstance().getConfig().getDouble("market.buy_factor", 1.1)));
        double buy = MarketMath.roundCurrency(MarketMath.tradePrice(spot, true,
                VillagerPro.getInstance().getConfig().getDouble("market.sell_factor", 0.9),
                VillagerPro.getInstance().getConfig().getDouble("market.buy_factor", 1.1)));
        return new MarketQuote(normalized, sell, buy,
                new MarketState(pressure, stored.tradedVolume(), stored.lastUpdatedMs()));
    }

    private static boolean validateTrade(Player player, Village village, String itemType, int amount) {
        if (!isAvailable(player)) return false;
        if (itemType == null) {
            player.sendMessage("§c该物品不在市场交易清单中");
            return false;
        }
        int maxTransaction = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("market.maximum_transaction_amount", 64));
        if (amount <= 0 || amount > maxTransaction) {
            player.sendMessage("§c单次交易数量必须在 1-" + maxTransaction + " 之间");
            return false;
        }
        int remaining = getDailyLimit(village) - getDailyVolume(village.getId());
        if (remaining < amount) {
            player.sendMessage("§c今日市场交易额度不足，剩余 " + Math.max(0, remaining));
            return false;
        }
        return true;
    }

    private static double executionUnitPrice(String itemType, MarketState state,
                                             boolean buying, int amount) {
        double liquidity = VillagerPro.getInstance().getConfig().getDouble("market.liquidity", 256);
        double maximumPressure = VillagerPro.getInstance().getConfig()
                .getDouble("market.maximum_pressure", 3);
        double nextPressure = MarketMath.pressureAfterTrade(
                state.pressure(), amount, buying, liquidity, maximumPressure);
        double midpointPressure = (state.pressure() + nextPressure) / 2.0;
        double basePrice = VillagerPro.getInstance().getConfig()
                .getDouble("market.items." + itemType + ".base_price",
                        DEFAULT_PRICES.getOrDefault(itemType, 0.0));
        double spot = MarketMath.spotPrice(basePrice, midpointPressure,
                VillagerPro.getInstance().getConfig().getDouble("market.price_sensitivity", 1.0),
                VillagerPro.getInstance().getConfig().getDouble("market.minimum_price_multiplier", 0.5),
                VillagerPro.getInstance().getConfig().getDouble("market.maximum_price_multiplier", 2.0));
        return MarketMath.roundCurrency(MarketMath.tradePrice(spot, buying,
                VillagerPro.getInstance().getConfig().getDouble("market.sell_factor", 0.85),
                VillagerPro.getInstance().getConfig().getDouble("market.buy_factor", 1.15)));
    }

    private static boolean saveTrade(Village village, Player player, String itemType,
                                     boolean buying, int amount, double unitPrice,
                                     MarketState currentState) {
        long now = System.currentTimeMillis();
        double nextPressure = MarketMath.pressureAfterTrade(currentState.pressure(), amount, buying,
                VillagerPro.getInstance().getConfig().getDouble("market.liquidity", 256),
                VillagerPro.getInstance().getConfig().getDouble("market.maximum_pressure", 3));
        try (Connection connection = DatabaseManager.getConnection()) {
            int reserve = buying ? 0 : WarehouseRuleManager.getItemRule(
                    village.getId(), itemType).getReserveAmount();
            long dailyStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            return OperationTransactions.completeMarketTrade(connection,
                    DatabaseManager.getDialect(), village.getId(),
                    player.getUniqueId().toString(), itemType, buying, amount, unitPrice,
                    reserve, PolicyManager.getFrozenStockFraction(village.getId()),
                    village.getWarehouseCapacity(), dailyStart, getDailyLimit(village),
                    currentState.tradedVolume(), currentState.lastUpdatedMs(), nextPressure, now);
        } catch (SQLException exception) {
            logFailure("保存市场成交", exception);
            return false;
        }
    }

    private static void cleanupTradeHistoryIfDue() {
        long now = System.currentTimeMillis();
        long previous = LAST_CLEANUP.get();
        if (now - previous < 6L * 60 * 60 * 1000
                || !LAST_CLEANUP.compareAndSet(previous, now)) return;
        int retentionDays = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("market.trade_history_retention_days", 30));
        long cutoff = now - retentionDays * 24L * 60 * 60 * 1000;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM market_trades WHERE traded_at_ms < ?")) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("清理市场成交历史", exception);
        }
    }

    private static MarketState loadState(String itemType) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT pressure, traded_volume, last_updated_ms FROM market_state WHERE item_type = ?")) {
            statement.setString(1, itemType);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new MarketState(resultSet.getDouble("pressure"),
                            resultSet.getLong("traded_volume"), resultSet.getLong("last_updated_ms"));
                }
            }
        } catch (SQLException exception) {
            logFailure("读取市场状态", exception);
        }
        return new MarketState(0, 0, 0);
    }

    private static int getDailyVolume(int villageId) {
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM market_trades WHERE village_id = ? AND traded_at_ms >= ?")) {
            statement.setInt(1, villageId);
            statement.setLong(2, start);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            logFailure("读取每日市场额度", exception);
            return Integer.MAX_VALUE;
        }
    }

    private static int getDailyLimit(Village village) {
        int base = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("market.daily_volume_base", 128));
        int perLevel = Math.max(0, VillagerPro.getInstance().getConfig()
                .getInt("market.daily_volume_per_village_level", 32));
        return base + Math.max(0, village.getLevel() - 1) * perLevel;
    }

    private static Village ownedVillage(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) player.sendMessage("§c请先创建村庄");
        return village;
    }

    private static boolean isAvailable(Player player) {
        if (!VillagerPro.getInstance().getConfig().getBoolean("features.market", true)
                || !VillagerPro.getInstance().getConfig().getBoolean("market.enabled", true)) {
            player.sendMessage("§c动态市场未启用");
            return false;
        }
        if (!EconomyManager.isVaultAvailable()) {
            player.sendMessage("§cVault 经济系统不可用");
            return false;
        }
        return true;
    }

    private static String normalizeConfiguredItem(String input) {
        if (input == null) return null;
        Material material = Material.matchMaterial(input);
        if (material == null) return null;
        String normalized = material.name();
        ConfigurationSection items = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("market.items");
        boolean configured = items == null ? DEFAULT_PRICES.containsKey(normalized)
                : items.contains(normalized + ".base_price");
        return configured ? normalized : null;
    }

    private static String formatMoney(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount)
                : String.format(Locale.ROOT, "%.2f", amount);
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }

    public record MarketQuote(String itemType, double sellPrice, double buyPrice, MarketState state) {
    }

    public record MarketState(double pressure, long tradedVolume, long lastUpdatedMs) {
    }
}
