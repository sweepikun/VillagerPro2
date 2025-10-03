package cn.popcraft.villagerpro;

import cn.popcraft.villagerpro.commands.CommandManager;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.EconomyManager;
import cn.popcraft.villagerpro.events.EventManager;
import cn.popcraft.villagerpro.managers.FollowManager;
import cn.popcraft.villagerpro.scheduler.WorkScheduler;
import cn.popcraft.villagerpro.util.Messages;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.plugin.java.JavaPlugin;

public class VillagerPro extends JavaPlugin {

    private static VillagerPro instance;
    private Economy economy;
    private PlayerPoints playerPointsAPI;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置文件
        saveDefaultConfig();
        
        // 初始化消息系统
        Messages.initialize();
        
        // 初始化数据库管理器（现在依赖于 Paper 自动加载 JDBC 驱动）
        DatabaseManager.initialize();
        
        // 初始化经济系统
        EconomyManager.initialize();
        
        // 初始化命令
        CommandManager.initialize();
        
        // 初始化事件监听器
        EventManager.initialize();
        
        // 初始化工作调度器
        WorkScheduler.initialize();
        
        getLogger().info("VillagerPro has been enabled!");
    }
    
    @Override
    public void onDisable() {
        // 停止所有跟随任务
        FollowManager.shutdown();
        
        // 关闭工作调度器
        WorkScheduler.shutdown();
        
        // 关闭数据库连接池
        DatabaseManager.shutdown();
        
        getLogger().info("VillagerPro has been disabled!");
    }
    
    public static VillagerPro getInstance() {
        return instance;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    public void setEconomy(Economy economy) {
        this.economy = economy;
    }
    
    public PlayerPoints getPlayerPointsAPI() {
        return playerPointsAPI;
    }
    
    public void setPlayerPointsAPI(PlayerPoints playerPointsAPI) {
        this.playerPointsAPI = playerPointsAPI;
    }
}