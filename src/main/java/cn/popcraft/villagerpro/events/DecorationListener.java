package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.DecorationManager;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class DecorationListener implements Listener {
    private final NamespacedKey decorationKey =
            new NamespacedKey(VillagerPro.getInstance(), "decoration_item");

    @EventHandler
    public void onDecorationPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String type = item.getItemMeta().getPersistentDataContainer()
                .get(decorationKey, PersistentDataType.STRING);
        if (type == null) return;

        event.setCancelled(true);
        if (DecorationManager.getInstance().placeDecoration(
                event.getPlayer(), type,
                event.getClickedBlock().getRelative(event.getBlockFace()))) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecorationBreak(BlockBreakEvent event) {
        if (!DecorationManager.getInstance().isManagedDecoration(event.getBlock())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§e村庄装饰请通过装饰管理界面移除");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecorationBurn(BlockBurnEvent event) {
        if (DecorationManager.getInstance().isManagedDecoration(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(DecorationManager.getInstance()::isManagedDecoration);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(DecorationManager.getInstance()::isManagedDecoration);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(DecorationManager.getInstance()::isManagedDecoration)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(DecorationManager.getInstance()::isManagedDecoration)) {
            event.setCancelled(true);
        }
    }
}
