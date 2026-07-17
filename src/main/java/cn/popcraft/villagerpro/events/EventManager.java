package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.ExperienceManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;

public class EventManager implements Listener {
    
    /**
     * 初始化事件管理器
     */
    public static void initialize() {
        // 注册防御事件监听器
        if (VillagerPro.getInstance().getConfig().getBoolean("features.defense", true)) {
            VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new DefenseListener(), VillagerPro.getInstance());
        }
        
        // 注册GUI事件监听器
        VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new GUIListener(), VillagerPro.getInstance());
        VillagerPro.getInstance().getServer().getPluginManager()
                .registerEvents(new WorkstationListener(), VillagerPro.getInstance());

        if (VillagerPro.getInstance().getConfig().getBoolean("features.buildings", true)) {
            VillagerPro.getInstance().getServer().getPluginManager()
                    .registerEvents(new BuildingListener(), VillagerPro.getInstance());
        }

        if (VillagerPro.getInstance().getConfig().getBoolean("features.decorations", true)) {
            VillagerPro.getInstance().getServer().getPluginManager()
                    .registerEvents(new DecorationListener(), VillagerPro.getInstance());
        }
        
        // 死亡清理与跟随属于核心村民逻辑，个性分支在监听器内部单独判断开关。
        VillagerPro.getInstance().getServer().getPluginManager()
                .registerEvents(new VillagerListener(), VillagerPro.getInstance());
        
        // 注册访客事件监听器
        if (VillagerPro.getInstance().getConfig().getBoolean("features.visitors", true)) {
            VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new VisitorListener(), VillagerPro.getInstance());
        }
        
        // 注册事件管理器本身以监听玩家加入事件
        VillagerPro.getInstance().getServer().getPluginManager().registerEvents(new EventManager(), VillagerPro.getInstance());
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        cn.popcraft.villagerpro.models.Village village =
                VillageManager.getVillage(event.getPlayer().getUniqueId());
        if (village != null) ExperienceManager.checkVillageLevelUp(village);
        // 可以在这里添加玩家加入时的逻辑
        // 例如：检查玩家是否有村庄，发送欢迎消息等
    }
}
