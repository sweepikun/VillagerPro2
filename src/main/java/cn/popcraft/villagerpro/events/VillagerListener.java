package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.FollowManager;
import cn.popcraft.villagerpro.managers.DefenseManager;
import cn.popcraft.villagerpro.managers.VisitorManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.managers.PersonalityManager;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class VillagerListener implements Listener {
    
    /**
     * 处理玩家与村民交互事件（用于切换跟随模式、送礼、赞扬）
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
        VillagerData villagerData = VillagerManager.getVillager(entity.getUniqueId());
        if (villagerData == null) {
            return;
        }
        cn.popcraft.villagerpro.models.Village village =
                cn.popcraft.villagerpro.managers.VillageManager.getVillage(player.getUniqueId());
        if (village == null || villagerData.getVillageId() != village.getId()) {
            player.sendMessage("§c你只能管理自己村庄的村民");
            event.setCancelled(true);
            return;
        }
        
        ItemStack handItem = player.getInventory().getItemInMainHand();
        Material handType = handItem != null ? handItem.getType() : Material.AIR;
        boolean personalityEnabled = PersonalityManager.isEnabled();
        
        if (player.isSneaking()) {
            // 潜行 + 手持礼物 = 送礼
            if (personalityEnabled
                    && (handType == Material.CAKE || handType == Material.DANDELION)) {
                if (PersonalityManager.getInstance().interactWithVillager(player, villagerData, "gift")) {
                    player.sendMessage("§a你送给 " + villagerData.getProfession() + " 一份礼物，忠诚度和心情提升了！");
                } else {
                    player.sendMessage("§c你需要持有蛋糕或蒲公英才能送礼");
                }
                event.setCancelled(true);
                return;
            }
            
            // 潜行 + 空手 = 赞扬
            if (personalityEnabled && handType == Material.AIR) {
                if (PersonalityManager.getInstance().interactWithVillager(player, villagerData, "praise")) {
                    player.sendMessage("§a你赞扬了 " + villagerData.getProfession() + "，心情提升了！");
                }
                event.setCancelled(true);
                return;
            }
            
            // 潜行 + 其他物品 = 切换跟随模式
            if (handType != Material.AIR) {
                if (FollowManager.toggleFollowMode(villagerData)) {
                    player.sendMessage("§a跟随模式已切换为: " + villagerData.getFollowMode());
                } else {
                    player.sendMessage("§c跟随模式保存失败，请重试");
                }
            }
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
            FollowManager.stopFollowing(villagerData);
            if (automaticCleanupEnabled()) {
                VillagerManager.removeVillager(villagerData.getId());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof Villager)
                || event.getTransformedEntity() instanceof Villager) return;
        VillagerData villagerData = VillagerManager.getVillager(event.getEntity().getUniqueId());
        if (villagerData == null) return;
        FollowManager.stopFollowing(villagerData);
        if (automaticCleanupEnabled()) {
            VillagerManager.removeVillager(villagerData.getId());
        }
    }

    private static boolean automaticCleanupEnabled() {
        return VillagerPro.getInstance().getConfig()
                .getBoolean("compatibility.auto_cleanup_dead_villagers", true);
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
            // 获取村庄拥有者
            cn.popcraft.villagerpro.models.Village village =
                cn.popcraft.villagerpro.managers.VillageManager.getVillageById(villagerData.getVillageId());
            if (village == null) return;

            // 如果伤害来源是其他玩家（非村庄拥有者），取消伤害
            if (event.getDamager() instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player damager = (org.bukkit.entity.Player) event.getDamager();
                if (!damager.getUniqueId().equals(village.getOwnerUUID())
                    && !damager.hasPermission("villagerpro.admin")) {
                    event.setCancelled(true);
                    damager.sendMessage("§c你不能伤害其他村庄的村民！");
                }
            } else if (event.getDamager() instanceof org.bukkit.entity.Monster) {
                if (!PersonalityManager.isEnabled()) return;
                org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(village.getOwnerUUID());
                if (owner != null) {
                    PersonalityManager.getInstance()
                            .interactWithVillager(owner, villagerData, "protect");
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (VillagerPro.getInstance().getConfig().getBoolean("features.defense", true)) {
                DefenseManager.getInstance().restoreEntityState(entity);
            }
            if (VillagerPro.getInstance().getConfig().getBoolean("features.visitors", true)) {
                VisitorManager.getInstance().restoreEntityState(entity);
            }
            if (!(entity instanceof Villager)) continue;
            VillagerData villager = VillagerManager.getVillager(entity.getUniqueId());
            if (villager != null) {
                FollowManager.applyModeState(villager);
            }
        }
    }
}
