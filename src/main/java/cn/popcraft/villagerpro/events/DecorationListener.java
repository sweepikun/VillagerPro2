package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.DecorationManager;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
}
