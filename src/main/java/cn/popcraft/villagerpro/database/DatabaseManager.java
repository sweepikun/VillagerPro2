package cn.popcraft.villagerpro.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import cn.popcraft.villagerpro.VillagerPro;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static HikariDataSource dataSource;
    
    /**
     * 初始化数据库连接
     */
    public static void initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + new File("plugins/VillagerPro/database.db").getAbsolutePath());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            createTables();
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().severe("数据库初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建数据表
     */
    private static void createTables() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // 创建村庄表
            statement.execute("CREATE TABLE IF NOT EXISTS villages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "owner_uuid TEXT NOT NULL UNIQUE, " +
                    "name TEXT NOT NULL DEFAULT 'My Village', " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "experience INTEGER NOT NULL DEFAULT 0, " +
                    "prosperity INTEGER NOT NULL DEFAULT 0, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            
            // 创建村民表
            statement.execute("CREATE TABLE IF NOT EXISTS villagers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "entity_uuid TEXT NOT NULL, " +
                    "profession TEXT NOT NULL, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "experience INTEGER NOT NULL DEFAULT 0, " +
                    "follow_mode TEXT NOT NULL DEFAULT 'FREE', " +
                    "recruited_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建仓库表
            statement.execute("CREATE TABLE IF NOT EXISTS warehouse (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "item_type TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL DEFAULT 0, " +
                    "UNIQUE(village_id, item_type) ON CONFLICT REPLACE, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建村庄升级表
            statement.execute("CREATE TABLE IF NOT EXISTS village_upgrades (" +
                    "village_id INTEGER NOT NULL, " +
                    "upgrade_id TEXT NOT NULL, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "PRIMARY KEY (village_id, upgrade_id), " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建村民升级表
            statement.execute("CREATE TABLE IF NOT EXISTS villager_upgrades (" +
                    "villager_id INTEGER NOT NULL, " +
                    "skill_id TEXT NOT NULL, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "PRIMARY KEY (villager_id, skill_id), " +
                    "FOREIGN KEY (villager_id) REFERENCES villagers(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建访客表
            statement.execute("CREATE TABLE IF NOT EXISTS visitors (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "display_name TEXT NOT NULL, " +
                    "location_x REAL NOT NULL, " +
                    "location_y REAL NOT NULL, " +
                    "location_z REAL NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "spawned_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "expires_at DATETIME NOT NULL, " +
                    "active BOOLEAN DEFAULT 1, " +
                    "custom_data TEXT, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建访客交易表
            statement.execute("CREATE TABLE IF NOT EXISTS visitor_deals (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "visitor_id INTEGER NOT NULL, " +
                    "deal_type TEXT NOT NULL, " +
                    "item_type TEXT NOT NULL, " +
                    "amount_required INTEGER NOT NULL, " +
                    "amount_delivered INTEGER DEFAULT 0, " +
                    "price REAL DEFAULT 0.0, " +
                    "reward_type TEXT, " +
                    "reward_amount INTEGER DEFAULT 0, " +
                    "reward_item TEXT, " +
                    "player_name TEXT NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'pending', " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "completed_at DATETIME, " +
                    "expires_at DATETIME NOT NULL, " +
                    "FOREIGN KEY (visitor_id) REFERENCES visitors(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建村庄装饰表
            statement.execute("CREATE TABLE IF NOT EXISTS decorations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "decoration_type TEXT NOT NULL, " +
                    "item_type TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL DEFAULT 1, " +
                    "location_x REAL NOT NULL, " +
                    "location_y REAL NOT NULL, " +
                    "location_z REAL NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "placed_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建村民个性表
            statement.execute("CREATE TABLE IF NOT EXISTS villager_personality (" +
                    "villager_id INTEGER PRIMARY KEY, " +
                    "loyalty INTEGER NOT NULL DEFAULT 50, " +
                    "mood INTEGER NOT NULL DEFAULT 70, " +
                    "last_interaction DATETIME, " +
                    "interaction_count INTEGER DEFAULT 0, " +
                    "FOREIGN KEY (villager_id) REFERENCES villagers(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建事件记录表
            statement.execute("CREATE TABLE IF NOT EXISTS events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "event_type TEXT NOT NULL, " +
                    "event_data TEXT, " +
                    "triggered_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "expires_at DATETIME, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建生态链配置表
            statement.execute("CREATE TABLE IF NOT EXISTS eco_chains (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "chain_type TEXT NOT NULL, " +
                    "producer_profession TEXT NOT NULL, " +
                    "consumer_profession TEXT NOT NULL, " +
                    "input_item TEXT NOT NULL, " +
                    "output_item TEXT NOT NULL, " +
                    "ratio INTEGER NOT NULL DEFAULT 1, " +
                    "efficiency REAL DEFAULT 1.0, " +
                    "enabled BOOLEAN DEFAULT 1, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建联盟表
            statement.execute("CREATE TABLE IF NOT EXISTS alliances (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "owner_village_id INTEGER NOT NULL, " +
                    "max_members INTEGER DEFAULT 5, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (owner_village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建联盟成员表
            statement.execute("CREATE TABLE IF NOT EXISTS alliance_members (" +
                    "alliance_id INTEGER NOT NULL, " +
                    "village_id INTEGER NOT NULL, " +
                    "joined_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (alliance_id, village_id), " +
                    "FOREIGN KEY (alliance_id) REFERENCES alliances(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建协作链活动记录表
            statement.execute("CREATE TABLE IF NOT EXISTS chain_activities (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "village_id INTEGER NOT NULL, " +
                    "chain_name TEXT NOT NULL, " +
                    "step_type TEXT NOT NULL, " +
                    "profession TEXT, " +
                    "item_type TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL, " +
                    "consumed_at DATETIME, " +
                    "produced_at DATETIME, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建传承记录表
            statement.execute("CREATE TABLE IF NOT EXISTS legacy_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "original_villager_id INTEGER NOT NULL, " +
                    "village_id INTEGER NOT NULL, " +
                    "profession TEXT NOT NULL, " +
                    "original_level INTEGER NOT NULL, " +
                    "skill_inheritance_percentage INTEGER NOT NULL, " +
                    "player_name TEXT NOT NULL, " +
                    "inherited_skills TEXT, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE" +
                    ")");
            
            // 创建访客委托表
            statement.execute("CREATE TABLE IF NOT EXISTS visitor_quests (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid TEXT NOT NULL, " +
                    "visitor_id INTEGER NOT NULL, " +
                    "quest_name TEXT NOT NULL, " +
                    "item_type TEXT NOT NULL, " +
                    "required_amount INTEGER NOT NULL, " +
                    "delivered_amount INTEGER DEFAULT 0, " +
                    "reward_type TEXT, " +
                    "reward_item TEXT, " +
                    "reward_amount INTEGER DEFAULT 0, " +
                    "status TEXT NOT NULL DEFAULT 'accepted', " +
                    "accepted_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "completed_at DATETIME, " +
                    "FOREIGN KEY (visitor_id) REFERENCES visitors(id) ON DELETE CASCADE" +
                    ")");
            
            VillagerPro.getInstance().getLogger().info("数据库表创建完成");

        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().severe("创建数据库表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }
    
    /**
     * 关闭数据库连接
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}