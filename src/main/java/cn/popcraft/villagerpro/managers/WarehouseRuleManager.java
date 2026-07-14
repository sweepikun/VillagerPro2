package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.WarehouseItem;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WarehouseRuleManager {
    public enum OverflowMode {
        DISCARD,
        DROP
    }

    public static final class ItemRule {
        private final int reserveAmount;
        private final boolean productionEnabled;

        public ItemRule(int reserveAmount, boolean productionEnabled) {
            this.reserveAmount = Math.max(0, reserveAmount);
            this.productionEnabled = productionEnabled;
        }

        public int getReserveAmount() { return reserveAmount; }
        public boolean isProductionEnabled() { return productionEnabled; }
    }

    private WarehouseRuleManager() {
    }

    public static ItemRule getItemRule(int villageId, String itemType) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT reserve_amount, production_enabled FROM warehouse_rules " +
                             "WHERE village_id = ? AND item_type = ?")) {
            statement.setInt(1, villageId);
            statement.setString(2, normalize(itemType));
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return new ItemRule(resultSet.getInt("reserve_amount"),
                        resultSet.getBoolean("production_enabled"));
            }
        } catch (SQLException e) {
            logFailure("读取仓库规则", e);
        }
        return new ItemRule(0, true);
    }

    public static boolean setReserveAmount(int villageId, String itemType, int reserveAmount) {
        String sql = DatabaseManager.upsert("INSERT INTO warehouse_rules " +
                "(village_id, item_type, reserve_amount, production_enabled) VALUES (?, ?, ?, 1)",
                new String[]{"village_id", "item_type"}, "reserve_amount");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setString(2, normalize(itemType));
            statement.setInt(3, Math.max(0, reserveAmount));
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logFailure("保存仓库保留量", e);
            return false;
        }
    }

    public static boolean setProductionEnabled(int villageId, String itemType, boolean enabled) {
        String sql = DatabaseManager.upsert("INSERT INTO warehouse_rules " +
                "(village_id, item_type, reserve_amount, production_enabled) VALUES (?, ?, 0, ?)",
                new String[]{"village_id", "item_type"}, "production_enabled");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setString(2, normalize(itemType));
            statement.setBoolean(3, enabled);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logFailure("保存生产开关", e);
            return false;
        }
    }

    public static boolean isProductionEnabled(int villageId, String itemType) {
        return getItemRule(villageId, itemType).isProductionEnabled();
    }

    public static boolean isAutoSubmitOrders(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT auto_submit_orders FROM village_operations WHERE village_id = ?")) {
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next() && resultSet.getBoolean("auto_submit_orders");
        } catch (SQLException e) {
            logFailure("读取自动交付设置", e);
            return false;
        }
    }

    public static boolean setAutoSubmitOrders(int villageId, boolean enabled) {
        String sql = DatabaseManager.upsert("INSERT INTO village_operations " +
                "(village_id, auto_submit_orders, overflow_mode) VALUES (?, ?, 'DISCARD')",
                new String[]{"village_id"}, "auto_submit_orders");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setBoolean(2, enabled);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logFailure("保存自动交付设置", e);
            return false;
        }
    }

    public static OverflowMode getOverflowMode(int villageId) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT overflow_mode FROM village_operations WHERE village_id = ?")) {
            statement.setInt(1, villageId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                try {
                    return OverflowMode.valueOf(resultSet.getString("overflow_mode"));
                } catch (IllegalArgumentException ignored) {
                    return OverflowMode.DISCARD;
                }
            }
        } catch (SQLException e) {
            logFailure("读取溢出设置", e);
        }
        return OverflowMode.DISCARD;
    }

    public static boolean setOverflowMode(int villageId, OverflowMode mode) {
        String sql = DatabaseManager.upsert("INSERT INTO village_operations " +
                "(village_id, auto_submit_orders, overflow_mode) VALUES (?, 0, ?)",
                new String[]{"village_id"}, "overflow_mode");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villageId);
            statement.setString(2, mode.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logFailure("保存溢出设置", e);
            return false;
        }
    }

    public static List<String> getControllableItemTypes(int villageId) {
        Set<String> items = new LinkedHashSet<>();
        ConfigurationSection professions = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("villager.professions");
        if (professions != null) {
            for (String profession : professions.getKeys(false)) {
                String path = "villager.professions." + profession;
                items.addAll(VillagerPro.getInstance().getConfig().getStringList(path + ".work_items"));
                addConfiguredItem(items, path + ".special_item");
                addConfiguredItem(items, path + ".treasure_item");
            }
        }
        ConfigurationSection chains = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("eco_chain.new_professions");
        if (chains != null) {
            for (String profession : chains.getKeys(false)) {
                addConfiguredItem(items, "eco_chain.new_professions." + profession + ".produces");
            }
        }
        ConfigurationSection recipes = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("eco_chain.processing_recipes");
        if (recipes != null) {
            for (String recipe : recipes.getKeys(false)) {
                ConfigurationSection outputs = recipes.getConfigurationSection(recipe + ".outputs");
                if (outputs != null) items.addAll(outputs.getKeys(false));
            }
        }
        for (WarehouseItem item : WarehouseManager.getWarehouseItems(villageId)) {
            items.add(item.getItemType());
        }
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.isBlank()) result.add(normalize(item));
        }
        Collections.sort(result);
        return result;
    }

    private static void addConfiguredItem(Set<String> items, String path) {
        String value = VillagerPro.getInstance().getConfig().getString(path);
        if (value != null && !value.isBlank()) items.add(value);
    }

    private static String normalize(String itemType) {
        return itemType == null ? "" : itemType.trim().toUpperCase();
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
