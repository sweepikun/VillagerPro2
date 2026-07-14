package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.database.DatabaseDialect;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredRecipeTransactionTest {
    @Test
    void consumesInputsAboveReserveAndAddsOutputsAtomically() throws Exception {
        try (Connection connection = createWarehouse()) {
            insert(connection, "WHEAT", 10);
            insert(connection, "BREAD", 2);

            assertTrue(StoredRecipeTransaction.apply(connection, DatabaseDialect.SQLITE, 1,
                    Map.of("WHEAT", 6), Map.of("BREAD", 2), Map.of("WHEAT", 3)));

            assertEquals(4, amount(connection, "WHEAT"));
            assertEquals(4, amount(connection, "BREAD"));
        }
    }

    @Test
    void rollsBackEarlierInputsWhenAnyRecipeInputIsUnavailable() throws Exception {
        try (Connection connection = createWarehouse()) {
            insert(connection, "WHEAT", 10);
            insert(connection, "SUGAR", 1);
            Map<String, Integer> inputs = new LinkedHashMap<>();
            inputs.put("WHEAT", 6);
            inputs.put("SUGAR", 2);

            assertFalse(StoredRecipeTransaction.apply(connection, DatabaseDialect.SQLITE, 1,
                    inputs, Map.of("COOKIE", 4), Map.of("WHEAT", 0, "SUGAR", 0)));

            assertEquals(10, amount(connection, "WHEAT"));
            assertEquals(1, amount(connection, "SUGAR"));
            assertEquals(0, amount(connection, "COOKIE"));
        }
    }

    private static Connection createWarehouse() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE warehouse (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "village_id INTEGER NOT NULL, item_type TEXT NOT NULL, amount INTEGER NOT NULL, "
                    + "UNIQUE(village_id, item_type))");
        }
        return connection;
    }

    private static void insert(Connection connection, String item, int amount) throws Exception {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO warehouse(village_id, item_type, amount) VALUES (1, ?, ?)")) {
            statement.setString(1, item);
            statement.setInt(2, amount);
            statement.executeUpdate();
        }
    }

    private static int amount(Connection connection, String item) throws Exception {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM warehouse WHERE village_id = 1 AND item_type = ?")) {
            statement.setString(1, item);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }
}
