package cn.popcraft.villagerpro.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationManagerTest {
    @Test
    void copiesEveryRegisteredTableInOneTransaction() throws Exception {
        try (Connection source = DriverManager.getConnection("jdbc:sqlite::memory:");
             Connection target = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMinimalSchema(source);
            createMinimalSchema(target);
            execute(source, "INSERT INTO villages(id, value) VALUES (1, 'main')");
            execute(source, "INSERT INTO villagers(id, value) VALUES (7, 'farmer')");

            DatabaseMigrationManager.MigrationResult result =
                    DatabaseMigrationManager.copyAll(source, target);

            assertEquals(DatabaseMigrationManager.tableNames().size(), result.getTableCount());
            assertEquals(2, result.getTotalRows());
            assertEquals("main", queryString(target, "SELECT value FROM villages WHERE id = 1"));
            assertEquals("farmer", queryString(target, "SELECT value FROM villagers WHERE id = 7"));
        }
    }

    @Test
    void migrationIncludesVisitorShopSales() {
        assertTrue(DatabaseMigrationManager.tableNames().contains("visitor_shop_sales"));
    }

    @Test
    void refusesToMergeIntoNonEmptyTarget() throws Exception {
        try (Connection source = DriverManager.getConnection("jdbc:sqlite::memory:");
             Connection target = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMinimalSchema(source);
            createMinimalSchema(target);
            execute(source, "INSERT INTO villages(id, value) VALUES (1, 'source')");
            execute(target, "INSERT INTO warehouse(id, value) VALUES (9, 'existing')");

            SQLException exception = assertThrows(SQLException.class,
                    () -> DatabaseMigrationManager.copyAll(source, target));
            assertTrue(exception.getMessage().contains("warehouse"));
            assertEquals(0, queryInt(target, "SELECT COUNT(*) FROM villages"));
            assertEquals(1, queryInt(target, "SELECT COUNT(*) FROM warehouse"));
        }
    }

    @Test
    void confirmedMigrationReplacesExistingTargetRows() throws Exception {
        try (Connection source = DriverManager.getConnection("jdbc:sqlite::memory:");
             Connection target = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMinimalSchema(source);
            createMinimalSchema(target);
            execute(source, "INSERT INTO villages(id, value) VALUES (1, 'current')");
            execute(target, "INSERT INTO villages(id, value) VALUES (9, 'old-backup')");
            execute(target, "INSERT INTO warehouse(id, value) VALUES (5, 'old-stock')");

            DatabaseMigrationManager.MigrationResult result =
                    DatabaseMigrationManager.copyAll(source, target, true);

            assertEquals(1, result.getTotalRows());
            assertEquals(1, queryInt(target, "SELECT COUNT(*) FROM villages"));
            assertEquals("current", queryString(target, "SELECT value FROM villages WHERE id = 1"));
            assertEquals(0, queryInt(target, "SELECT COUNT(*) FROM warehouse"));
        }
    }

    @Test
    void confirmedMigrationRestoresTargetWhenCopyFails() throws Exception {
        try (Connection source = DriverManager.getConnection("jdbc:sqlite::memory:");
             Connection target = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMinimalSchema(source);
            createMinimalSchema(target);
            execute(source, "INSERT INTO villages(id, value) VALUES (1, 'current')");
            execute(source, "INSERT INTO villagers(id, value) VALUES (2, 'will-fail')");
            execute(target, "INSERT INTO villages(id, value) VALUES (9, 'old-backup')");
            execute(target, "ALTER TABLE villagers RENAME TO villagers_old");
            execute(target, "CREATE TABLE villagers (id INTEGER)");

            assertThrows(SQLException.class,
                    () -> DatabaseMigrationManager.copyAll(source, target, true));

            assertEquals(1, queryInt(target, "SELECT COUNT(*) FROM villages"));
            assertEquals("old-backup", queryString(target, "SELECT value FROM villages WHERE id = 9"));
        }
    }

    @Test
    void changesOnlyDatabaseTypeLineInConfiguration() throws Exception {
        List<String> original = List.of(
                "debug: false",
                "",
                "database:",
                "  # storage backend",
                "  type: sqlite # keep this note",
                "  mysql:",
                "    host: localhost",
                "economy:",
                "  use_vault: true");

        List<String> updated = DatabaseMigrationManager.replaceDatabaseType(original, "mysql");

        assertEquals("  type: mysql # keep this note", updated.get(4));
        assertEquals(original.get(0), updated.get(0));
        assertEquals(original.get(6), updated.get(6));
        assertEquals(original.size(), updated.size());
    }

    @Test
    void rejectsConfigurationWithoutDatabaseType() {
        assertThrows(IOException.class, () -> DatabaseMigrationManager.replaceDatabaseType(
                List.of("database:", "  mysql:", "    host: localhost"), "mysql"));
    }

    private static void createMinimalSchema(Connection connection) throws SQLException {
        for (String table : DatabaseMigrationManager.tableNames()) {
            execute(connection, "CREATE TABLE `" + table + "` (id INTEGER, value TEXT)");
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
