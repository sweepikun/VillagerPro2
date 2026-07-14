package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.models.BuildingType;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CrisisManager {
    public enum CrisisType {
        HARVEST_FAILURE("harvest_failure", "歉收", "BREAD", BuildingType.GRANARY),
        EPIDEMIC("epidemic", "疫病", "POTION", BuildingType.CLINIC),
        FIRE("fire", "火灾", "OAK_PLANKS", BuildingType.WORKSHOP),
        TRADE_BLOCKADE("trade_blockade", "贸易封锁", "EMERALD", BuildingType.GRANARY);

        private final String id;
        private final String displayName;
        private final String defaultItem;
        private final BuildingType mitigationBuilding;

        CrisisType(String id, String displayName, String defaultItem, BuildingType mitigationBuilding) {
            this.id = id;
            this.displayName = displayName;
            this.defaultItem = defaultItem;
            this.mitigationBuilding = mitigationBuilding;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDefaultItem() { return defaultItem; }
        public BuildingType getMitigationBuilding() { return mitigationBuilding; }

        public static CrisisType fromInput(String input) {
            if (input == null) return null;
            String normalized = input.toLowerCase(Locale.ROOT);
            for (CrisisType type : values()) {
                if (type.id.equals(normalized)) return type;
            }
            return null;
        }
    }

    public record ActiveCrisis(int villageId, CrisisType type, String requiredItem,
                               int requiredAmount, int contributedAmount,
                               long startedAtMs, long expiresAtMs) {
    }

    private static final Map<Integer, ActiveCrisis> ACTIVE = new HashMap<>();
    private static final Map<Integer, Long> NEXT_ROLL = new HashMap<>();
    private static BukkitTask task;

    private CrisisManager() {
    }

    public static void initialize() {
        ACTIVE.clear();
        NEXT_ROLL.clear();
        if (!isEnabled()) return;
        loadAll();
        long interval = Math.max(200L, VillagerPro.getInstance().getConfig()
                .getLong("crises.check_interval_ticks", 1200L));
        task = VillagerPro.getInstance().getServer().getScheduler().runTaskTimer(
                VillagerPro.getInstance(), CrisisManager::tick, interval, interval);
    }

    public static void shutdown() {
        if (task != null) task.cancel();
        task = null;
        ACTIVE.clear();
        NEXT_ROLL.clear();
    }

    public static ActiveCrisis getActiveCrisis(int villageId) {
        if (!isEnabled()) return null;
        ActiveCrisis crisis = ACTIVE.get(villageId);
        if (crisis != null && crisis.expiresAtMs() <= System.currentTimeMillis()) {
            expireCrisis(crisis);
            return null;
        }
        return crisis;
    }

    public static void showStatus(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return;
        }
        ActiveCrisis crisis = getActiveCrisis(village.getId());
        if (crisis == null) {
            player.sendMessage("§a村庄当前没有危机");
            return;
        }
        player.sendMessage("§c§l村庄危机：" + crisis.type().getDisplayName());
        player.sendMessage("§e救援物资：" + crisis.requiredItem() + " "
                + crisis.contributedAmount() + "/" + crisis.requiredAmount());
        player.sendMessage("§7剩余时间：" + formatRemaining(crisis.expiresAtMs() - System.currentTimeMillis()));
        player.sendMessage("§7当前影响：" + effectDescription(village.getId(), crisis.type()));
    }

    public static boolean contribute(Player player, int requestedAmount) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        ActiveCrisis crisis = getActiveCrisis(village.getId());
        if (crisis == null) {
            player.sendMessage("§c村庄当前没有需要处理的危机");
            return false;
        }
        if (requestedAmount <= 0) {
            player.sendMessage("§c提交数量必须大于 0");
            return false;
        }
        int remaining = crisis.requiredAmount() - crisis.contributedAmount();
        if (remaining <= 0) {
            completeCrisis(village, crisis, player);
            return true;
        }
        int amount = Math.min(requestedAmount, remaining);
        int available = WarehouseManager.getExtractableAmount(village.getId(), crisis.requiredItem());
        if (available < amount) {
            player.sendMessage("§c可用 " + crisis.requiredItem() + " 不足，需要 " + amount
                    + "，当前可用 " + available + "；保留量和政策冻结库存不会被消耗");
            return false;
        }
        ActiveCrisis updated = new ActiveCrisis(crisis.villageId(), crisis.type(), crisis.requiredItem(),
                crisis.requiredAmount(), crisis.contributedAmount() + amount,
                crisis.startedAtMs(), crisis.expiresAtMs());
        int configuredReserve = WarehouseRuleManager.getItemRule(
                village.getId(), crisis.requiredItem()).getReserveAmount();
        double frozenFraction = PolicyManager.getFrozenStockFraction(village.getId());
        try (Connection connection = DatabaseManager.getConnection()) {
            if (!OperationTransactions.contributeToCrisis(connection, village.getId(),
                    crisis.requiredItem(), amount, configuredReserve, frozenFraction,
                    crisis.contributedAmount(),
                    updated.contributedAmount())) {
                player.sendMessage("§c库存或危机进度已经变化，本次没有扣除物资");
                return false;
            }
        } catch (SQLException exception) {
            logFailure("提交危机救援物资", exception);
            player.sendMessage("§c救援提交失败，库存和进度均未改变");
            return false;
        }
        ACTIVE.put(village.getId(), updated);
        player.sendMessage("§a已提交 " + amount + " 个 " + crisis.requiredItem() + "，进度 "
                + updated.contributedAmount() + "/" + updated.requiredAmount());
        if (updated.contributedAmount() >= updated.requiredAmount()) {
            completeCrisis(village, updated, player);
        }
        return true;
    }

    public static boolean forceStart(Player player, CrisisType type) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        if (getActiveCrisis(village.getId()) != null) {
            player.sendMessage("§c必须先处理或等待当前危机结束");
            return false;
        }
        return startCrisis(village, type, player);
    }

    public static double getProductionMultiplier(int villageId) {
        ActiveCrisis crisis = getActiveCrisis(villageId);
        if (crisis == null || crisis.type() == CrisisType.TRADE_BLOCKADE) return 1.0;
        double base = config(path(crisis.type(), "production_multiplier"), switch (crisis.type()) {
            case HARVEST_FAILURE -> 0.65;
            case EPIDEMIC -> 0.80;
            case FIRE -> 0.75;
            case TRADE_BLOCKADE -> 1.0;
        });
        return mitigate(villageId, crisis.type(), base);
    }

    public static double getNeedsDecayMultiplier(int villageId) {
        ActiveCrisis crisis = getActiveCrisis(villageId);
        if (crisis == null || crisis.type() != CrisisType.EPIDEMIC) return 1.0;
        return mitigate(villageId, crisis.type(),
                config(path(crisis.type(), "needs_decay_multiplier"), 1.50));
    }

    public static double getMarketSellMultiplier(int villageId) {
        ActiveCrisis crisis = getActiveCrisis(villageId);
        if (crisis == null || crisis.type() != CrisisType.TRADE_BLOCKADE) return 1.0;
        return mitigate(villageId, crisis.type(),
                config(path(crisis.type(), "market_sell_multiplier"), 0.65));
    }

    public static double getMarketBuyMultiplier(int villageId) {
        ActiveCrisis crisis = getActiveCrisis(villageId);
        if (crisis == null || crisis.type() != CrisisType.TRADE_BLOCKADE) return 1.0;
        return mitigate(villageId, crisis.type(),
                config(path(crisis.type(), "market_buy_multiplier"), 1.35));
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        for (Village village : VillageManager.getAllVillages()) {
            ActiveCrisis active = ACTIVE.get(village.getId());
            if (active != null) {
                if (active.expiresAtMs() <= now) expireCrisis(active);
                continue;
            }
            Long storedNext = NEXT_ROLL.get(village.getId());
            long next;
            if (storedNext == null) {
                next = now + hours("crises.initial_delay_hours", 6);
                NEXT_ROLL.put(village.getId(), next);
                saveWaiting(village.getId(), next);
            } else {
                next = storedNext;
            }
            if (next > now) continue;
            double chance = Math.max(0, Math.min(1, config("crises.trigger_chance_per_roll", 0.25)));
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                CrisisType[] values = CrisisType.values();
                startCrisis(village, values[ThreadLocalRandom.current().nextInt(values.length)], null);
            } else {
                saveWaiting(village.getId(), scheduleNext(village.getId(), now));
            }
        }
    }

    private static boolean startCrisis(Village village, CrisisType type, Player feedback) {
        long now = System.currentTimeMillis();
        int base = (int) config(path(type, "required_amount"), switch (type) {
            case HARVEST_FAILURE -> 24;
            case EPIDEMIC -> 8;
            case FIRE -> 32;
            case TRADE_BLOCKADE -> 16;
        });
        int perLevel = (int) config(path(type, "required_per_level"), switch (type) {
            case HARVEST_FAILURE, FIRE -> 8;
            case EPIDEMIC -> 2;
            case TRADE_BLOCKADE -> 4;
        });
        String item = VillagerPro.getInstance().getConfig().getString(
                path(type, "required_item"), type.getDefaultItem()).toUpperCase(Locale.ROOT);
        ActiveCrisis crisis = new ActiveCrisis(village.getId(), type, item,
                GameplayMath.scaleByVillageLevel(Math.max(1, base), Math.max(0, perLevel), village.getLevel()),
                0, now, now + hours("crises.duration_hours", 24));
        if (!saveActive(crisis)) return false;
        ACTIVE.put(village.getId(), crisis);
        NEXT_ROLL.remove(village.getId());
        Player owner = feedback != null ? feedback
                : VillagerPro.getInstance().getServer().getPlayer(village.getOwnerUUID());
        if (owner != null) {
            owner.sendMessage("§c村庄发生“" + type.getDisplayName() + "”！使用 /village crisis status 查看详情");
        }
        return true;
    }

    private static void completeCrisis(Village village, ActiveCrisis crisis, Player player) {
        long next = System.currentTimeMillis() + hours("crises.roll_interval_hours", 12);
        int reward = Math.max(0, VillagerPro.getInstance().getConfig()
                .getInt("crises.resolution_prosperity_reward", 10));
        int nextProsperity = village.getProsperity() + reward;
        if (!settleCrisis(village, crisis, next, nextProsperity)) {
            player.sendMessage("§c危机进度已完成，但原子结算失败；状态和繁荣度均未改变，请重试");
            return;
        }
        player.sendMessage("§a“" + crisis.type().getDisplayName() + "”已解除，村庄获得 "
                + reward + " 繁荣度");
    }

    private static void expireCrisis(ActiveCrisis crisis) {
        long next = System.currentTimeMillis() + hours("crises.roll_interval_hours", 12);
        Village village = VillageManager.getVillageById(crisis.villageId());
        if (village == null) {
            ACTIVE.remove(crisis.villageId());
            NEXT_ROLL.remove(crisis.villageId());
            return;
        }
        int penalty = Math.max(0, VillagerPro.getInstance().getConfig()
                .getInt("crises.failure_prosperity_penalty", 15));
        int nextProsperity = Math.max(0, village.getProsperity() - penalty);
        if (!settleCrisis(village, crisis, next, nextProsperity)) return;
        Player owner = VillagerPro.getInstance().getServer().getPlayer(village.getOwnerUUID());
        if (owner != null) owner.sendMessage("§c“" + crisis.type().getDisplayName()
                + "”未能及时处理，村庄损失 " + penalty + " 繁荣度");
    }

    private static boolean settleCrisis(Village village, ActiveCrisis crisis,
                                        long nextRoll, int nextProsperity) {
        try (Connection connection = DatabaseManager.getConnection()) {
            if (!OperationTransactions.settleCrisis(connection, village.getId(),
                    crisis.type().getId(), nextRoll, nextProsperity)) {
                return false;
            }
        } catch (SQLException exception) {
            logFailure("结算村庄危机", exception);
            return false;
        }
        village.setProsperity(nextProsperity);
        ACTIVE.remove(village.getId());
        NEXT_ROLL.put(village.getId(), nextRoll);
        return true;
    }

    private static double mitigate(int villageId, CrisisType type, double multiplier) {
        int level = BuildingManager.getActiveLevel(villageId, type.getMitigationBuilding());
        return GameplayMath.mitigatedMultiplier(multiplier, level,
                config("crises.building_mitigation_per_level", 0.15));
    }

    private static String effectDescription(int villageId, CrisisType type) {
        return switch (type) {
            case HARVEST_FAILURE, EPIDEMIC, FIRE -> "生产倍率 "
                    + formatPercent(getProductionMultiplier(villageId))
                    + (type == CrisisType.EPIDEMIC ? "，需求衰减 "
                    + formatPercent(getNeedsDecayMultiplier(villageId)) : "");
            case TRADE_BLOCKADE -> "市场卖价 " + formatPercent(getMarketSellMultiplier(villageId))
                    + "，买价 " + formatPercent(getMarketBuyMultiplier(villageId));
        };
    }

    private static void loadAll() {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT village_id, crisis_id, status, required_item, required_amount, "
                             + "contributed_amount, started_at_ms, expires_at_ms, next_roll_at_ms "
                             + "FROM village_crises");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                int villageId = resultSet.getInt("village_id");
                NEXT_ROLL.put(villageId, resultSet.getLong("next_roll_at_ms"));
                if (!"active".equalsIgnoreCase(resultSet.getString("status"))) continue;
                CrisisType type = CrisisType.fromInput(resultSet.getString("crisis_id"));
                if (type == null) continue;
                ACTIVE.put(villageId, new ActiveCrisis(villageId, type,
                        resultSet.getString("required_item"), resultSet.getInt("required_amount"),
                        resultSet.getInt("contributed_amount"), resultSet.getLong("started_at_ms"),
                        resultSet.getLong("expires_at_ms")));
            }
        } catch (SQLException exception) {
            logFailure("加载村庄危机", exception);
        }
    }

    private static boolean saveActive(ActiveCrisis crisis) {
        String sql = DatabaseManager.upsert("INSERT INTO village_crises "
                        + "(village_id, crisis_id, status, required_item, required_amount, contributed_amount, "
                        + "started_at_ms, expires_at_ms, next_roll_at_ms) VALUES (?, ?, 'active', ?, ?, ?, ?, ?, 0)",
                new String[]{"village_id"}, "crisis_id", "status", "required_item", "required_amount",
                "contributed_amount", "started_at_ms", "expires_at_ms", "next_roll_at_ms");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, crisis.villageId());
            statement.setString(2, crisis.type().getId());
            statement.setString(3, crisis.requiredItem());
            statement.setInt(4, crisis.requiredAmount());
            statement.setInt(5, crisis.contributedAmount());
            statement.setLong(6, crisis.startedAtMs());
            statement.setLong(7, crisis.expiresAtMs());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            logFailure("保存村庄危机", exception);
            return false;
        }
    }

    private static boolean saveWaiting(int villageId, long nextRoll) {
        String sql = DatabaseManager.upsert("INSERT INTO village_crises "
                        + "(village_id, crisis_id, status, required_item, required_amount, contributed_amount, "
                        + "started_at_ms, expires_at_ms, next_roll_at_ms) VALUES (?, '', 'waiting', '', 0, 0, 0, 0, ?)",
                new String[]{"village_id"}, "crisis_id", "status", "required_item", "required_amount",
                "contributed_amount", "started_at_ms", "expires_at_ms", "next_roll_at_ms");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setLong(2, nextRoll);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            logFailure("保存危机周期", exception);
            return false;
        }
    }

    private static long scheduleNext(int villageId, long now) {
        long next = now + hours("crises.roll_interval_hours", 12);
        NEXT_ROLL.put(villageId, next);
        return next;
    }

    private static long hours(String path, long fallback) {
        return Math.max(1, VillagerPro.getInstance().getConfig().getLong(path, fallback)) * 3_600_000L;
    }

    private static String path(CrisisType type, String key) {
        return "crises.types." + type.getId() + "." + key;
    }

    private static double config(String path, double fallback) {
        return VillagerPro.getInstance().getConfig().getDouble(path, fallback);
    }

    private static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.crises", true)
                && VillagerPro.getInstance().getConfig().getBoolean("crises.enabled", true);
    }

    private static String formatRemaining(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        return minutes >= 60 ? (minutes / 60) + "小时" + (minutes % 60) + "分钟" : minutes + "分钟";
    }

    private static String formatPercent(double multiplier) {
        return String.format(Locale.ROOT, "%.0f%%", multiplier * 100);
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
