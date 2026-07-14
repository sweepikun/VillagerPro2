package cn.popcraft.villagerpro.database;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum DatabaseDialect {
    SQLITE,
    MYSQL;

    public static DatabaseDialect fromConfig(String value) {
        if (value == null || value.isBlank() || "sqlite".equalsIgnoreCase(value)) {
            return SQLITE;
        }
        if ("mysql".equalsIgnoreCase(value)) {
            return MYSQL;
        }
        throw new IllegalArgumentException("不支持的数据库类型: " + value + "（可选值: sqlite, mysql）");
    }

    public String adaptDdl(String sql) {
        if (this == SQLITE) {
            return sql;
        }

        String adapted = sql
                .replace("CREATE UNIQUE INDEX IF NOT EXISTS", "CREATE UNIQUE INDEX")
                .replace("CREATE INDEX IF NOT EXISTS", "CREATE INDEX")
                .replace("INTEGER PRIMARY KEY AUTOINCREMENT", "BIGINT AUTO_INCREMENT PRIMARY KEY")
                .replaceAll("\\bINTEGER\\b", "BIGINT")
                .replaceAll("\\bREAL\\b", "DOUBLE")
                .replaceAll("\\bTEXT\\b", "VARCHAR(255)")
                .replace(" ON CONFLICT REPLACE", "");

        // These fields can contain serialized or otherwise unbounded data and must not be VARCHARs.
        adapted = adapted
                .replace("custom_data VARCHAR(255)", "custom_data TEXT")
                .replace("event_data VARCHAR(255)", "event_data TEXT")
                .replace("inherited_skills VARCHAR(255)", "inherited_skills TEXT");
        if (adapted.startsWith("CREATE TABLE") && adapted.endsWith(")")) {
            adapted += " ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        }
        return adapted;
    }

    public String insertIgnore(String insertSql) {
        return insertSql.replaceFirst("(?i)^INSERT\\s+INTO", this == MYSQL ? "INSERT IGNORE INTO" : "INSERT OR IGNORE INTO");
    }

    public String upsert(String insertSql, String[] conflictColumns, String... updateColumns) {
        if (updateColumns.length == 0) {
            throw new IllegalArgumentException("UPSERT 至少需要一个更新列");
        }
        if (this == MYSQL) {
            String updates = Arrays.stream(updateColumns)
                    .map(column -> column + " = VALUES(" + column + ")")
                    .collect(Collectors.joining(", "));
            return insertSql + " ON DUPLICATE KEY UPDATE " + updates;
        }

        String conflict = String.join(", ", conflictColumns);
        String updates = Arrays.stream(updateColumns)
                .map(column -> column + " = excluded." + column)
                .collect(Collectors.joining(", "));
        return insertSql + " ON CONFLICT(" + conflict + ") DO UPDATE SET " + updates;
    }

    public String additiveUpsert(String insertSql, String conflictColumn, String amountColumn) {
        if (this == MYSQL) {
            return insertSql + " ON DUPLICATE KEY UPDATE " + amountColumn + " = "
                    + amountColumn + " + VALUES(" + amountColumn + ")";
        }
        return insertSql + " ON CONFLICT(" + conflictColumn + ") DO UPDATE SET " + amountColumn
                + " = " + amountColumn + " + excluded." + amountColumn;
    }

    public boolean isDuplicateIndexError(java.sql.SQLException exception) {
        return this == MYSQL && (exception.getErrorCode() == 1061
                || "42000".equals(exception.getSQLState())
                && exception.getMessage() != null
                && exception.getMessage().toLowerCase(Locale.ROOT).contains("duplicate key name"));
    }

    public boolean isDuplicateColumnError(java.sql.SQLException exception) {
        if (this == MYSQL) {
            return exception.getErrorCode() == 1060;
        }
        return exception.getMessage() != null
                && exception.getMessage().toLowerCase(Locale.ROOT).contains("duplicate column name");
    }
}
