package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VisitorManager;
import cn.popcraft.villagerpro.models.VisitorData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 访客事件处理
 * 处理玩家与访客的交互、靠近检测等事件
 */
public class VisitorListener implements Listener {
    
    private final VisitorManager visitorManager;
    private final VillagerPro plugin;
    
    public VisitorListener() {
        this.visitorManager = VisitorManager.getInstance();
        this.plugin = VillagerPro.getInstance();
    }
    
    /**
     * 玩家右键交互访客事件
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) {
            return;
        }
        
        Player player = event.getPlayer();
        Villager villager = (Villager) event.getRightClicked();
        
        // 检查是否是访客村民
        if (!isVisitorVillager(villager)) {
            return;
        }
        
        // 获取访客数据
        VisitorData visitor = getVisitorByEntity(villager);
        if (visitor == null) {
            player.sendMessage("§c访客数据异常，请联系管理员");
            return;
        }
        
        // 检查访客是否过期
        if (visitor.isExpired()) {
            player.sendMessage("§c这位访客已经离开了");
            visitor.removeEntity();
            return;
        }
        
        // 阻止默认的村民交易界面
        event.setCancelled(true);
        
        // 显示访客交互界面
        visitorManager.interactWithVisitor(player, visitor);
    }
    
    /**
     * 玩家靠近访客时的提示
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // 检查玩家是否在移动（避免过于频繁的检查）
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // 检查附近是否有访客
        checkNearbyVisitors(player);
    }
    
    /**
     * 访客受到攻击时的处理
     */
    @EventHandler
    public void onVisitorDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager)) {
            return;
        }
        
        Villager villager = (Villager) event.getEntity();
        
        // 检查是否是访客村民
        if (!isVisitorVillager(villager)) {
            return;
        }
        
        // 访客无敌
        event.setCancelled(true);
        
        // 如果是玩家攻击，发送提示
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            player.sendMessage("§c你不能攻击访客！");
        }
    }
    
    /**
     * 检查附近的访客
     */
    private void checkNearbyVisitors(Player player) {
        // 获取附近5格内的访客
        for (VisitorData visitor : visitorManager.getAllActiveVisitors()) {
            if (!visitor.isActive() || visitor.getEntity() == null) {
                continue;
            }
            
            double distance = player.getLocation().distance(visitor.getEntity().getLocation());
            if (distance <= 5.0) {
                // 显示提示信息（只显示一次）
                showVisitorTip(player, visitor);
                break;
            }
        }
    }
    
    /**
     * 显示访客提示
     */
    private void showVisitorTip(Player player, VisitorData visitor) {
        // 使用延迟来避免频繁显示
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    String tip = "§e右键访客 §f" + visitor.getName() + " §e进行交互";
                    player.sendMessage(tip);
                }
            }
        }.runTaskLater(plugin, 100L); // 5秒后显示
    }
    
    /**
     * 检查村民是否是访客
     */
    private boolean isVisitorVillager(Villager villager) {
        // 检查村民是否有访客的显示名称
        if (villager.getCustomName() == null) {
            return false;
        }
        
        return villager.getCustomName().contains("§e[访客]");
    }
    
    /**
     * 根据实体获取访客数据
     */
    private VisitorData getVisitorByEntity(Villager villager) {
        for (VisitorData visitor : visitorManager.getAllActiveVisitors()) {
            if (visitor.getEntity() != null && 
                visitor.getEntity().getUniqueId().equals(villager.getUniqueId())) {
                return visitor;
            }
        }
        return null;
    }
    
    /**
     * 当访客死亡时的处理
     */
    public void onVisitorDeath(VisitorData visitor) {
        if (visitor != null) {
            visitorManager.removeVisitor(visitor.getId());
            VillagerPro.getInstance().getLogger().info("访客 " + visitor.getName() + " 已被移除");
        }
    }
    
    /**
     * 清理访客实体的定时任务
     */
    public void createCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (VisitorData visitor : visitorManager.getAllActiveVisitors()) {
                    if (visitor.isExpired()) {
                        visitorManager.removeVisitor(visitor.getId());
                    }
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // 每分钟检查一次
    }
}