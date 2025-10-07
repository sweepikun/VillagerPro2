package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;

public class EventManager implements Listener {
    
    /**
     * 初始化事件管理器
     */
    public static void initialize() {
        // 注册GUI事件监听器
        VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new GUIListener(), VillagerPro.getInstance());
        
        // 注册村民事件监听器
        VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new VillagerListener(), VillagerPro.getInstance());
        
        // 注册事件管理器本身以监听玩家加入事件
        VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new EventManager(), VillagerPro.getInstance());
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 可以在这里添加玩家加入时的逻辑
        // 例如：检查玩家是否有村庄，发送欢迎消息等
    }
}