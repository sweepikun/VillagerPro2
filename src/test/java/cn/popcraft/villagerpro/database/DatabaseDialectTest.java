package cn.popcraft.villagerpro.database;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDialectTest {
    @Test
    void parsesSupportedDatabaseTypes() {
        assertEquals(DatabaseDialect.SQLITE, DatabaseDialect.fromConfig(null));
        assertEquals(DatabaseDialect.SQLITE, DatabaseDialect.fromConfig("SQLite"));
        assertEquals(DatabaseDialect.MYSQL, DatabaseDialect.fromConfig("MYSQL"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseDialect.fromConfig("postgres"));
    }

    @Test
    void mysqlDdlUsesCompatibleTypesAndIndexSyntax() {
        String sql = DatabaseDialect.MYSQL.adaptDdl(
                "CREATE TABLE sample (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT UNIQUE, "
                        + "ratio REAL, custom_data TEXT, UNIQUE(id, owner_uuid) ON CONFLICT REPLACE)");

        assertTrue(sql.contains("id BIGINT AUTO_INCREMENT PRIMARY KEY"));
        assertTrue(sql.contains("owner_uuid VARCHAR(255) UNIQUE"));
        assertTrue(sql.contains("ratio DOUBLE"));
        assertTrue(sql.contains("custom_data TEXT"));
        assertTrue(sql.endsWith("ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"));
        assertFalse(sql.contains("AUTOINCREMENT"));
        assertFalse(sql.contains("ON CONFLICT"));
    }

    @Test
    void operationsSchemaGeneratesMysqlCompatibleStatements() throws Exception {
        List<String> statements = new ArrayList<>();
        Statement recorder = (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        statements.add((String) args[0]);
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        OperationsSchema.createTables(recorder, DatabaseDialect.MYSQL);

        assertFalse(statements.isEmpty());
        assertTrue(statements.stream().allMatch(sql -> !sql.contains("AUTOINCREMENT")));
        assertTrue(statements.stream().allMatch(sql -> !sql.contains("ON CONFLICT")));
        assertTrue(statements.stream().allMatch(sql -> !sql.contains("INDEX IF NOT EXISTS")));
        assertTrue(statements.stream().anyMatch(sql -> sql.contains("created_at_ms BIGINT")));
        assertTrue(statements.stream().anyMatch(sql -> sql.contains("visitor_shop_sales")));
        assertTrue(statements.stream().anyMatch(sql -> sql.contains("active_guards")));
    }

    @Test
    void sqliteUpsertsPreserveExistingBehavior() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE warehouse (village_id INTEGER, item_type TEXT, amount INTEGER, "
                    + "UNIQUE(village_id, item_type))");
            String additive = DatabaseDialect.SQLITE.additiveUpsert(
                    "INSERT INTO warehouse (village_id, item_type, amount) VALUES (?, ?, ?)",
                    "village_id, item_type", "amount");
            executeWarehouseUpsert(connection, additive, 3);
            executeWarehouseUpsert(connection, additive, 4);

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = 'WHEAT'")) {
                assertTrue(resultSet.next());
                assertEquals(7, resultSet.getInt(1));
            }
        }
    }

    @Test
    void dmlSyntaxIsGeneratedForBothDialects() {
        String insert = "INSERT INTO sample (id, value) VALUES (?, ?)";
        assertTrue(DatabaseDialect.SQLITE.insertIgnore(insert).startsWith("INSERT OR IGNORE INTO"));
        assertTrue(DatabaseDialect.MYSQL.insertIgnore(insert).startsWith("INSERT IGNORE INTO"));
        assertTrue(DatabaseDialect.SQLITE.upsert(insert, new String[]{"id"}, "value")
                .contains("ON CONFLICT(id) DO UPDATE SET value = excluded.value"));
        assertTrue(DatabaseDialect.MYSQL.upsert(insert, new String[]{"id"}, "value")
                .contains("ON DUPLICATE KEY UPDATE value = VALUES(value)"));
    }

    @Test
    void mysqlPersonalityMigrationUsesEpochMillisecondStorage() throws Exception {
        List<String> statements = new ArrayList<>();
        Statement recorder = (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        statements.add((String) args[0]);
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        DatabaseManager.migratePersonalityInteractionColumn(
                new SchemaStatement(recorder, DatabaseDialect.MYSQL), DatabaseDialect.MYSQL);

        assertTrue(statements.stream().anyMatch(sql -> sql.contains(
                "MODIFY COLUMN last_interaction BIGINT NOT NULL DEFAULT 0")));
        assertTrue(statements.stream().anyMatch(sql -> sql.contains(
                "last_interaction > 4102444800000")));
    }

    private static void executeWarehouseUpsert(Connection connection, String sql, int amount) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, 1);
            statement.setString(2, "WHEAT");
            statement.setInt(3, amount);
            statement.executeUpdate();
        }
    }
}
