package cn.popcraft.villagerpro.database;

import java.sql.SQLException;
import java.sql.Statement;

final class SchemaStatement {
    private final Statement delegate;
    private final DatabaseDialect dialect;

    SchemaStatement(Statement delegate, DatabaseDialect dialect) {
        this.delegate = delegate;
        this.dialect = dialect;
    }

    boolean execute(String sql) throws SQLException {
        try {
            return delegate.execute(dialect.adaptDdl(sql));
        } catch (SQLException exception) {
            String normalized = sql.toUpperCase(java.util.Locale.ROOT);
            if ((normalized.startsWith("CREATE INDEX") || normalized.startsWith("CREATE UNIQUE INDEX"))
                    && dialect.isDuplicateIndexError(exception)) {
                return false;
            }
            throw exception;
        }
    }
}
