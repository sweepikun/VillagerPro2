package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.FollowManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class VillagerListener implements Listener {
    
    /**
     * 处理玩家与村民交互事件（用于切换跟随模式）
     * @param event 事件
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager)) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        
        // 检查是否是我们管理的村民
        VillagerData villagerData = VillagerManager.getVillager(entity.getUniqueId());
        if (villagerData != null) {
            // 切换跟随模式
            FollowManager.toggleFollowMode(villagerData);
            event.setCancelled(true);
        }
    }
    
    /**
     * 处理村民死亡事件
     * @param event 事件
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) {
            return;
        }
        
        // 检查是否是我们管理的村民
        VillagerData villagerData = VillagerManager.getVillager(entity.getUniqueId());
        if (villagerData != null) {
            // 从数据库中移除
            VillagerManager.removeVillager(villagerData.getId());
            
            // 停止跟随任务（如果有的话）
            // FollowManager.stopFollowing(villagerData); // 在removeVillager中应该已经处理了
        }
    }
    
    /**
     * 处理村民受到伤害事件
     * @param event 事件
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) {
            return;
        }
        
        // 检查是否是我们管理的村民
        VillagerData villagerData = VillagerManager.getVillager(entity.getUniqueId());
        if (villagerData != null) {
            // 可以在这里添加保护逻辑，例如只允许村庄拥有者伤害村民
            // 目前留空，后续可以扩展
        }
    }
}