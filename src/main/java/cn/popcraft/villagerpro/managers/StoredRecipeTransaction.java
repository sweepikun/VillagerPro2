package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.database.DatabaseDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

final class StoredRecipeTransaction {
    private StoredRecipeTransaction() {
    }

    static Result apply(Connection connection, DatabaseDialect dialect, int villageId,
                        Map<String, Integer> consumed, Map<String, Integer> produced,
                        Map<String, Integer> reserves, int warehouseCapacity) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement lock = connection.prepareStatement(
                    "UPDATE villages SET id = id WHERE id = ?")) {
                lock.setInt(1, villageId);
                lock.executeUpdate();
            }
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
                        return Result.MISSING_INPUT;
                    }
                }
            }
            int currentStorage;
            try (PreparedStatement total = connection.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM warehouse WHERE village_id = ?")) {
                total.setInt(1, villageId);
                try (ResultSet resultSet = total.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return Result.MISSING_INPUT;
                    }
                    currentStorage = resultSet.getInt(1);
                }
            }
            int producedTotal = produced.values().stream().mapToInt(Integer::intValue).sum();
            if (currentStorage + producedTotal > warehouseCapacity) {
                connection.rollback();
                return Result.WAREHOUSE_FULL;
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
            return Result.SUCCESS;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    enum Result {
        SUCCESS,
        MISSING_INPUT,
        WAREHOUSE_FULL
    }
}
