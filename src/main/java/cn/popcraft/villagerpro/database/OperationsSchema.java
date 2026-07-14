package cn.popcraft.villagerpro.database;

import java.sql.SQLException;
import java.sql.Statement;

public final class OperationsSchema {
    private OperationsSchema() {
    }

    public static void createTables(Statement statement) throws SQLException {
        createTables(statement, DatabaseDialect.SQLITE);
    }

    public static void createTables(Statement statement, DatabaseDialect dialect) throws SQLException {
        createTables(new SchemaStatement(statement, dialect));
    }

    static void createTables(SchemaStatement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS visitor_shop_sales (" +
                "visitor_id INTEGER NOT NULL, " +
                "product_id TEXT NOT NULL, " +
                "player_uuid TEXT NOT NULL, " +
                "purchase_count INTEGER NOT NULL DEFAULT 0, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (visitor_id, product_id, player_uuid), " +
                "FOREIGN KEY (visitor_id) REFERENCES visitors(id) ON DELETE CASCADE" +
                ")");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_visitor_shop_product " +
                "ON visitor_shop_sales(visitor_id, product_id)");

        statement.execute("CREATE TABLE IF NOT EXISTS village_buildings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "village_id INTEGER NOT NULL, " +
                "building_type TEXT NOT NULL, " +
                "world TEXT NOT NULL, " +
                "core_x INTEGER NOT NULL, " +
                "core_y INTEGER NOT NULL, " +
                "core_z INTEGER NOT NULL, " +
                "level INTEGER NOT NULL DEFAULT 0, " +
                "active BOOLEAN NOT NULL DEFAULT 0, " +
                "status TEXT NOT NULL DEFAULT '', " +
                "last_validated_ms INTEGER NOT NULL DEFAULT 0, " +
                "UNIQUE(village_id, building_type), " +
                "UNIQUE(world, core_x, core_y, core_z), " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS village_policies (" +
                "village_id INTEGER PRIMARY KEY, " +
                "policy_id TEXT NOT NULL, " +
                "activated_at_ms INTEGER NOT NULL, " +
                "expires_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS village_crises (" +
                "village_id INTEGER PRIMARY KEY, " +
                "crisis_id TEXT NOT NULL DEFAULT '', " +
                "status TEXT NOT NULL DEFAULT 'waiting', " +
                "required_item TEXT NOT NULL DEFAULT '', " +
                "required_amount INTEGER NOT NULL DEFAULT 0, " +
                "contributed_amount INTEGER NOT NULL DEFAULT 0, " +
                "started_at_ms INTEGER NOT NULL DEFAULT 0, " +
                "expires_at_ms INTEGER NOT NULL DEFAULT 0, " +
                "next_roll_at_ms INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS caravan_routes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "village_id INTEGER NOT NULL, " +
                "destination_id TEXT NOT NULL, " +
                "cargo_item TEXT NOT NULL, " +
                "cargo_amount INTEGER NOT NULL, " +
                "return_item TEXT NOT NULL, " +
                "return_amount INTEGER NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'traveling', " +
                "successful BOOLEAN NOT NULL DEFAULT 1, " +
                "departed_at_ms INTEGER NOT NULL, " +
                "arrives_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_caravan_routes_village_status " +
                "ON caravan_routes(village_id, status)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_caravan_routes_arrival " +
                "ON caravan_routes(status, arrives_at_ms)");

        statement.execute("CREATE TABLE IF NOT EXISTS market_state (" +
                "item_type TEXT PRIMARY KEY, " +
                "pressure REAL NOT NULL DEFAULT 0, " +
                "traded_volume INTEGER NOT NULL DEFAULT 0, " +
                "last_updated_ms INTEGER NOT NULL DEFAULT 0" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS market_trades (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "village_id INTEGER NOT NULL, " +
                "player_uuid TEXT NOT NULL, " +
                "item_type TEXT NOT NULL, " +
                "direction TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, " +
                "unit_price REAL NOT NULL, " +
                "total_price REAL NOT NULL, " +
                "traded_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_market_trades_village_time " +
                "ON market_trades(village_id, traded_at_ms)");

        statement.execute("CREATE TABLE IF NOT EXISTS village_orders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "village_id INTEGER NOT NULL, " +
                "day_key TEXT NOT NULL, " +
                "order_slot INTEGER NOT NULL, " +
                "item_type TEXT NOT NULL, " +
                "amount_required INTEGER NOT NULL, " +
                "reward_money REAL NOT NULL DEFAULT 0, " +
                "reward_prosperity INTEGER NOT NULL DEFAULT 0, " +
                "payout_money REAL NOT NULL DEFAULT 0, " +
                "status TEXT NOT NULL DEFAULT 'pending', " +
                "completed_at DATETIME, " +
                "UNIQUE(village_id, day_key, order_slot), " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS production_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "village_id INTEGER NOT NULL, " +
                "villager_id INTEGER NOT NULL, " +
                "profession TEXT NOT NULL, " +
                "item_type TEXT NOT NULL DEFAULT '', " +
                "attempted_amount INTEGER NOT NULL DEFAULT 0, " +
                "stored_amount INTEGER NOT NULL DEFAULT 0, " +
                "status TEXT NOT NULL, " +
                "created_at_ms INTEGER NOT NULL, " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (villager_id) REFERENCES villagers(id) ON DELETE CASCADE" +
                ")");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_production_events_village_time " +
                "ON production_events(village_id, created_at_ms)");

        statement.execute("CREATE TABLE IF NOT EXISTS warehouse_rules (" +
                "village_id INTEGER NOT NULL, " +
                "item_type TEXT NOT NULL, " +
                "reserve_amount INTEGER NOT NULL DEFAULT 0, " +
                "production_enabled BOOLEAN NOT NULL DEFAULT 1, " +
                "PRIMARY KEY (village_id, item_type), " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS village_operations (" +
                "village_id INTEGER PRIMARY KEY, " +
                "auto_submit_orders BOOLEAN NOT NULL DEFAULT 0, " +
                "overflow_mode TEXT NOT NULL DEFAULT 'DISCARD', " +
                "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS villager_specializations (" +
                "villager_id INTEGER PRIMARY KEY, " +
                "branch_id TEXT NOT NULL, " +
                "level INTEGER NOT NULL DEFAULT 1, " +
                "selected_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (villager_id) REFERENCES villagers(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS villager_needs (" +
                "villager_id INTEGER PRIMARY KEY, " +
                "hunger REAL NOT NULL DEFAULT 100, " +
                "comfort REAL NOT NULL DEFAULT 100, " +
                "health REAL NOT NULL DEFAULT 100, " +
                "last_updated_ms INTEGER NOT NULL, " +
                "last_consumed TEXT NOT NULL DEFAULT '', " +
                "FOREIGN KEY (villager_id) REFERENCES villagers(id) ON DELETE CASCADE" +
                ")");

        statement.execute("CREATE TABLE IF NOT EXISTS villager_workstations (" +
                "villager_id INTEGER PRIMARY KEY, " +
                "world TEXT NOT NULL, " +
                "block_x INTEGER NOT NULL, " +
                "block_y INTEGER NOT NULL, " +
                "block_z INTEGER NOT NULL, " +
                "material TEXT NOT NULL, " +
                "level INTEGER NOT NULL DEFAULT 1, " +
                "UNIQUE(world, block_x, block_y, block_z), " +
                "FOREIGN KEY (villager_id) REFERENCES villagers(id) ON DELETE CASCADE" +
                ")");
    }
}
