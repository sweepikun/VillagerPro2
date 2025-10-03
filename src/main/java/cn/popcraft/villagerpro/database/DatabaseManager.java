package cn.popcraft.villagerpro.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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
            e.printStackTrace();
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
                    
        } catch (SQLException e) {
            e.printStackTrace();
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