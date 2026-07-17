package cn.popcraft.villagerpro.database;

import cn.popcraft.villagerpro.VillagerPro;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DatabaseMigrationManager {
    private static final int BATCH_SIZE = 500;
    private static final AtomicBoolean MIGRATING = new AtomicBoolean();
    private static final List<String> TABLES = List.of(
            "villages",
            "village_buildings",
            "village_policies",
            "village_crises",
            "caravan_routes",
            "market_state",
            "market_trades",
            "villagers",
            "warehouse",
            "village_upgrades",
            "villager_upgrades",
            "visitors",
            "visitor_shop_sales",
            "active_guards",
            "visitor_deals",
            "decorations",
            "villager_personality",
            "events",
            "eco_chains",
            "alliances",
            "alliance_members",
            "chain_activities",
            "legacy_records",
            "visitor_quests",
            "festival_claims",
            "festival_boosts",
            "village_orders",
            "production_events",
            "warehouse_rules",
            "village_operations",
            "villager_specializations",
            "villager_needs",
            "villager_workstations"
    );

    private DatabaseMigrationManager() {
    }

    static List<String> tableNames() {
        return TABLES;
    }

    public static MigrationResult migrate(DatabaseDialect targetDialect) throws SQLException, IOException {
        return migrate(targetDialect, false);
    }

    public static MigrationResult migrate(DatabaseDialect targetDialect, boolean replaceTarget)
            throws SQLException, IOException {
        DatabaseDialect sourceDialect = DatabaseManager.getDialect();
        if (sourceDialect == targetDialect) {
            throw new IllegalArgumentException("当前已经在使用 " + targetDialect.name());
        }
        if (!MIGRATING.compareAndSet(false, true)) {
            throw new IllegalStateException("已有数据库迁移正在执行");
        }

        try (HikariDataSource targetDataSource = DatabaseManager.createConfiguredDataSource(
                targetDialect, "migration")) {
            DatabaseManager.createTables(targetDataSource, targetDialect);
            MigrationResult result;
            try (Connection source = DatabaseManager.getConnection();
                 Connection target = targetDataSource.getConnection()) {
                result = copyAll(source, target, replaceTarget);
            }
            switchConfiguredDatabase(targetDialect);
            return result;
        } finally {
            MIGRATING.set(false);
        }
    }

    static MigrationResult copyAll(Connection source, Connection target) throws SQLException {
        return copyAll(source, target, false);
    }

    static MigrationResult copyAll(Connection source, Connection target, boolean replaceTarget) throws SQLException {
        boolean sourceAutoCommit = source.getAutoCommit();
        boolean targetAutoCommit = target.getAutoCommit();
        source.setAutoCommit(false);
        target.setAutoCommit(false);

        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        try {
            if (replaceTarget) {
                clearTarget(target);
            } else {
                ensureTargetEmpty(target);
            }
            for (String table : TABLES) {
                rowCounts.put(table, copyTable(source, target, table));
            }
            target.commit();
            return new MigrationResult(rowCounts);
        } catch (SQLException exception) {
            target.rollback();
            throw exception;
        } finally {
            source.rollback();
            source.setAutoCommit(sourceAutoCommit);
            target.setAutoCommit(targetAutoCommit);
        }
    }

    private static void ensureTargetEmpty(Connection target) throws SQLException {
        for (String table : TABLES) {
            try (Statement statement = target.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                if (resultSet.next() && resultSet.getLong(1) > 0) {
                    throw new SQLException("目标数据库不是空库，表 " + table
                            + " 已有数据；迁移已取消。如需用当前数据覆盖目标，请在命令末尾添加 confirm");
                }
            }
        }
    }

    private static void clearTarget(Connection target) throws SQLException {
        for (int index = TABLES.size() - 1; index >= 0; index--) {
            try (Statement statement = target.createStatement()) {
                statement.executeUpdate("DELETE FROM `" + TABLES.get(index) + "`");
            }
        }
    }

    static int copyTable(Connection source, Connection target, String table) throws SQLException {
        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery("SELECT * FROM `" + table + "`")) {
            ResultSetMetaData metadata = rows.getMetaData();
            int columnCount = metadata.getColumnCount();
            String insertSql = buildInsertSql(table, metadata);
            int copied = 0;
            int pending = 0;
            try (PreparedStatement insert = target.prepareStatement(insertSql)) {
                while (rows.next()) {
                    for (int column = 1; column <= columnCount; column++) {
                        insert.setObject(column, rows.getObject(column));
                    }
                    insert.addBatch();
                    copied++;
                    pending++;
                    if (pending >= BATCH_SIZE) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    insert.executeBatch();
                }
            }
            return copied;
        }
    }

    private static String buildInsertSql(String table, ResultSetMetaData metadata) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            String name = metadata.getColumnLabel(column);
            if (!name.matches("[A-Za-z0-9_]+")) {
                throw new SQLException("不安全的数据库列名: " + name);
            }
            columns.add("`" + name + "`");
            placeholders.add("?");
        }
        return "INSERT INTO `" + table + "` (" + String.join(", ", columns) + ") VALUES ("
                + String.join(", ", placeholders) + ")";
    }

    private static void switchConfiguredDatabase(DatabaseDialect targetDialect) throws IOException {
        VillagerPro plugin = VillagerPro.getInstance();
        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        List<String> updated = replaceDatabaseType(lines, targetDialect.name().toLowerCase(java.util.Locale.ROOT));
        Path temporary = configPath.resolveSibling("config.yml.migration.tmp");
        Files.write(temporary, updated, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static List<String> replaceDatabaseType(List<String> lines, String targetType) throws IOException {
        List<String> updated = new ArrayList<>(lines);
        boolean inDatabaseSection = false;
        for (int index = 0; index < updated.size(); index++) {
            String line = updated.get(index);
            String trimmed = line.trim();
            if (!inDatabaseSection) {
                inDatabaseSection = "database:".equals(trimmed) && !startsWithWhitespace(line);
                continue;
            }
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !startsWithWhitespace(line)) {
                break;
            }
            if (trimmed.startsWith("type:")) {
                int colon = line.indexOf(':');
                int comment = line.indexOf('#', colon + 1);
                String suffix = comment >= 0 ? " " + line.substring(comment).trim() : "";
                updated.set(index, line.substring(0, colon + 1) + " " + targetType + suffix);
                return Collections.unmodifiableList(updated);
            }
        }
        throw new IOException("config.yml 中缺少 database.type 配置");
    }

    private static boolean startsWithWhitespace(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(0));
    }

    public static final class MigrationResult {
        private final Map<String, Integer> rowCounts;

        private MigrationResult(Map<String, Integer> rowCounts) {
            this.rowCounts = Collections.unmodifiableMap(new LinkedHashMap<>(rowCounts));
        }

        public int getTotalRows() {
            return rowCounts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getTableCount() {
            return rowCounts.size();
        }

        public Map<String, Integer> getRowCounts() {
            return rowCounts;
        }
    }
}
