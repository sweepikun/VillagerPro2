package cn.popcraft.villagerpro.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationsSchemaTest {
    @Test
    void operationsTablesPersistRulesAndEnforceOrderIdentity() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE villages (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE villagers (id INTEGER PRIMARY KEY, village_id INTEGER, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE visitors (id INTEGER PRIMARY KEY, village_id INTEGER, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE)");
            OperationsSchema.createTables(statement);
            statement.execute("INSERT INTO villages(id) VALUES (1)");
            statement.execute("INSERT INTO visitors(id, village_id) VALUES (9, 1)");
            statement.execute("INSERT INTO visitor_shop_sales " +
                    "(visitor_id, product_id, player_uuid, purchase_count) " +
                    "VALUES (9, 'seed', 'player', 2)");
            statement.execute("INSERT INTO village_buildings " +
                    "(village_id, building_type, world, core_x, core_y, core_z, level, active) " +
                    "VALUES (1, 'granary', 'world', 1, 64, 1, 2, 1)");
            statement.execute("INSERT INTO market_state " +
                    "(item_type, pressure, traded_volume, last_updated_ms) VALUES ('WHEAT', 0.2, 16, 1)");
            statement.execute("INSERT INTO market_trades " +
                    "(village_id, player_uuid, item_type, direction, amount, unit_price, total_price, traded_at_ms) " +
                    "VALUES (1, 'player', 'WHEAT', 'SELL', 16, 2, 32, 1)");
            statement.execute("INSERT INTO villagers(id, village_id) VALUES (7, 1)");
            statement.execute("INSERT INTO villagers(id, village_id) VALUES (8, 1)");
            statement.execute("INSERT INTO warehouse_rules " +
                    "(village_id, item_type, reserve_amount, production_enabled) " +
                    "VALUES (1, 'WHEAT', 32, 0)");
            statement.execute("INSERT INTO village_orders " +
                    "(village_id, day_key, order_slot, item_type, amount_required) " +
                    "VALUES (1, '2026-07-14', 0, 'WHEAT', 48)");
            statement.execute("INSERT INTO production_events " +
                    "(village_id, villager_id, profession, item_type, attempted_amount, " +
                    "stored_amount, status, created_at_ms) " +
                    "VALUES (1, 7, 'farmer', 'WHEAT', 4, 4, 'SUCCESS', 1)");
            statement.execute("INSERT INTO villager_specializations " +
                    "(villager_id, branch_id, level) VALUES (7, 'abundance', 2)");
            statement.execute("INSERT INTO villager_needs " +
                    "(villager_id, hunger, comfort, health, last_updated_ms) " +
                    "VALUES (7, 80, 70, 60, 1)");
            statement.execute("INSERT INTO villager_workstations " +
                    "(villager_id, world, block_x, block_y, block_z, material, level) " +
                    "VALUES (7, 'world', 1, 64, 1, 'COMPOSTER', 2)");
            statement.execute("INSERT INTO village_policies " +
                    "(village_id, policy_id, activated_at_ms, expires_at_ms) " +
                    "VALUES (1, 'overtime', 1, 86400001)");
            statement.execute("INSERT INTO village_crises " +
                    "(village_id, crisis_id, status, required_item, required_amount, contributed_amount, " +
                    "started_at_ms, expires_at_ms) " +
                    "VALUES (1, 'epidemic', 'active', 'POTION', 8, 3, 1, 86400001)");
            statement.execute("INSERT INTO caravan_routes " +
                    "(village_id, destination_id, cargo_item, cargo_amount, return_item, return_amount, " +
                    "status, successful, departed_at_ms, arrives_at_ms) " +
                    "VALUES (1, 'capital', 'BREAD', 24, 'EMERALD', 6, 'traveling', 1, 1, 3600001)");

            assertEquals(32, queryInt(statement,
                    "SELECT reserve_amount FROM warehouse_rules WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT payout_money FROM village_orders WHERE village_id = 1"));
            assertEquals(2, queryInt(statement,
                    "SELECT level FROM village_buildings WHERE village_id = 1"));
            assertEquals(16, queryInt(statement,
                    "SELECT traded_volume FROM market_state WHERE item_type = 'WHEAT'"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM market_trades WHERE village_id = 1"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM production_events WHERE village_id = 1"));
            assertEquals(2, queryInt(statement,
                    "SELECT level FROM villager_specializations WHERE villager_id = 7"));
            assertEquals(80, queryInt(statement,
                    "SELECT hunger FROM villager_needs WHERE villager_id = 7"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM village_policies WHERE village_id = 1"));
            assertEquals(3, queryInt(statement,
                    "SELECT contributed_amount FROM village_crises WHERE village_id = 1"));
            assertEquals(6, queryInt(statement,
                    "SELECT return_amount FROM caravan_routes WHERE village_id = 1"));
            assertEquals(2, queryInt(statement,
                    "SELECT purchase_count FROM visitor_shop_sales WHERE visitor_id = 9"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO village_orders " +
                            "(village_id, day_key, order_slot, item_type, amount_required) " +
                            "VALUES (1, '2026-07-14', 0, 'CARROT', 12)"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO villager_workstations " +
                            "(villager_id, world, block_x, block_y, block_z, material, level) " +
                            "VALUES (8, 'world', 1, 64, 1, 'COMPOSTER', 1)"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO villager_specializations " +
                            "(villager_id, branch_id, level) VALUES (7, 'horticulturist', 1)"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO village_buildings " +
                            "(village_id, building_type, world, core_x, core_y, core_z) " +
                            "VALUES (1, 'granary', 'world', 4, 64, 4)"));

            statement.execute("DELETE FROM villages WHERE id = 1");
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM warehouse_rules WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM village_buildings WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM market_trades WHERE village_id = 1"));
            assertEquals(1, queryInt(statement,
                    "SELECT COUNT(*) FROM market_state WHERE item_type = 'WHEAT'"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM production_events WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM villager_specializations WHERE villager_id = 7"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM villager_needs WHERE villager_id = 7"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM villager_workstations WHERE villager_id = 7"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM village_policies WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM village_crises WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM caravan_routes WHERE village_id = 1"));
            assertEquals(0, queryInt(statement,
                    "SELECT COUNT(*) FROM visitor_shop_sales WHERE visitor_id = 9"));
        }
    }

    @Test
    void legacyOrderTableReceivesPayoutColumnIdempotently() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE village_orders (id INTEGER PRIMARY KEY)");
            SchemaStatement schema = new SchemaStatement(statement, DatabaseDialect.SQLITE);
            DatabaseManager.migrateOrderPayoutColumns(schema, DatabaseDialect.SQLITE);
            DatabaseManager.migrateOrderPayoutColumns(schema, DatabaseDialect.SQLITE);
            statement.execute("INSERT INTO village_orders(id) VALUES (1)");
            assertEquals(0, queryInt(statement,
                    "SELECT payout_money FROM village_orders WHERE id = 1"));
        }
    }

    private static int queryInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
