package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.database.DatabaseDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

final class StoredRecipeTransaction {
    private StoredRecipeTransaction() {
    }

    static boolean apply(Connection connection, DatabaseDialect dialect, int villageId,
                         Map<String, Integer> consumed, Map<String, Integer> produced,
                         Map<String, Integer> reserves) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Map.Entry<String, Integer> input : consumed.entrySet()) {
                int reserve = reserves.getOrDefault(input.getKey(), 0);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE warehouse SET amount = amount - ? WHERE village_id = ? "
                                + "AND item_type = ? AND amount >= ?")) {
                    statement.setInt(1, input.getValue());
                    statement.setInt(2, villageId);
                    statement.setString(3, input.getKey());
                    statement.setInt(4, input.getValue() + reserve);
                    if (statement.executeUpdate() <= 0) {
                        connection.rollback();
                        return false;
                    }
                }
            }
            String outputSql = dialect.additiveUpsert(
                    "INSERT INTO warehouse (village_id, item_type, amount) VALUES (?, ?, ?)",
                    "village_id, item_type", "amount");
            for (Map.Entry<String, Integer> output : produced.entrySet()) {
                try (PreparedStatement statement = connection.prepareStatement(outputSql)) {
                    statement.setInt(1, villageId);
                    statement.setString(2, output.getKey());
                    statement.setInt(3, output.getValue());
                    statement.executeUpdate();
                }
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
