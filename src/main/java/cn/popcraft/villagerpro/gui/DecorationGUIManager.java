package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.DecorationManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * 装饰系统GUI管理器
 * 负责处理装饰购买、放置、管理的所有界面
 */
public class DecorationGUIManager {

    private static final String GUI_PREFIX = "§f[VP] ";
    private static final NamespacedKey TAG_KEY = new NamespacedKey(VillagerPro.getInstance(), "deco_tag");
    private static final NamespacedKey ID_KEY = new NamespacedKey(VillagerPro.getInstance(), "deco_id");

    /**
     * 显示装饰商店主界面
     */
    public static void showDecorationShop(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有村庄！");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, GUI_PREFIX + "§6村庄装饰商店");

        fillBackground(gui);

        ItemStack villageInfo = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = villageInfo.getItemMeta();
        infoMeta.setDisplayName("§6" + village.getName() + " - 装饰商店");
        infoMeta.setLore(java.util.Arrays.asList(
            "§7等级: §e" + village.getLevel(),
            "§7繁荣度: §e" + village.getProsperity(),
            "§7装饰数: §e" + DecorationManager.getInstance().getVillageDecorations(village.getId()).size()
        ));
        villageInfo.setItemMeta(infoMeta);
        gui.setItem(4, villageInfo);

        addDecorationItems(gui, player, village);
        addCloseButton(gui, 53);

        player.openInventory(gui);
    }

    /**
     * 显示装饰管理界面
     */
    public static void showDecorationManagement(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有村庄！");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, GUI_PREFIX + "§6" + village.getName() + " - 装饰管理");

        fillBackground(gui);

        ItemStack villageInfo = new ItemStack(Material.MAP);
        ItemMeta infoMeta = villageInfo.getItemMeta();
        infoMeta.setDisplayName("§6" + village.getName() + " - 装饰管理");
        infoMeta.setLore(java.util.Arrays.asList(
            "§7总装饰数: §e" + DecorationManager.getInstance().getVillageDecorations(village.getId()).size()
        ));
        villageInfo.setItemMeta(infoMeta);
        gui.setItem(4, villageInfo);

        addDecorationList(gui, village, player);
        addManagementButtons(gui, village);
        addCloseButton(gui, 53);

        player.openInventory(gui);
    }

    /**
     * 添加装饰商品
     */
    private static void addDecorationItems(Inventory gui, Player player, Village village) {
        VillagerPro plugin = VillagerPro.getInstance();
        org.bukkit.configuration.ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("decorations.items");
        if (itemsSection == null) return;

        Map<String, Object> decorationItems = itemsSection.getValues(false);

        int slot = 9;
        for (Map.Entry<String, Object> entry : decorationItems.entrySet()) {
            if (slot >= 45) break;

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> decorationData = (Map<String, Object>) entry.getValue();
                String name = (String) decorationData.get("name");
                String description = (String) decorationData.getOrDefault("description", "");
                String material = (String) decorationData.get("material");
                int prosperityBoost = decorationData.containsKey("prosperity_boost") ? ((Number) decorationData.get("prosperity_boost")).intValue() : 0;

                Material decorationMaterial;
                try {
                    decorationMaterial = Material.valueOf(material);
                } catch (IllegalArgumentException e) {
                    decorationMaterial = Material.STONE;
                }

                ItemStack item = new ItemStack(decorationMaterial);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName("§6" + name);

                List<String> lore = new java.util.ArrayList<>();
                lore.add("§7" + description);
                lore.add("§7繁荣度: §e+" + prosperityBoost);
                lore.add("");
                lore.add("§7价格: 见聊天栏");
                lore.add("");
                lore.add("§a左键购买");
                itemMeta.setLore(lore);
                item.setItemMeta(itemMeta);

                setStringTag(item, TAG_KEY, entry.getKey());

                gui.setItem(slot, item);
                slot++;

            } catch (Exception e) {
                plugin.getLogger().warning("处理装饰物品时出错: " + e.getMessage());
            }
        }
    }

    /**
     * 添加装饰列表
     */
    private static void addDecorationList(Inventory gui, Village village, Player player) {
        List<DecorationManager.VillageDecoration> decorations =
            DecorationManager.getInstance().getVillageDecorations(village.getId());

        if (decorations.isEmpty()) {
            ItemStack emptyInfo = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = emptyInfo.getItemMeta();
            emptyMeta.setDisplayName("§c暂无装饰");
            emptyMeta.setLore(java.util.Arrays.asList("§7去装饰商店购买一些装饰吧！"));
            emptyInfo.setItemMeta(emptyMeta);
            gui.setItem(22, emptyInfo);
            return;
        }

        int slot = 9;
        for (DecorationManager.VillageDecoration decoration : decorations) {
            if (slot >= 45) break;

            Material material;
            try {
                material = Material.valueOf(decoration.getItemType());
            } catch (IllegalArgumentException e) {
                material = Material.STONE;
            }

            ItemStack item = new ItemStack(material);
            ItemMeta itemMeta = item.getItemMeta();
            itemMeta.setDisplayName("§e" + decoration.getDecorationType());

            org.bukkit.Location loc = decoration.getLocation();
            itemMeta.setLore(java.util.Arrays.asList(
                "§7类型: " + decoration.getDecorationType(),
                "§7数量: " + decoration.getAmount(),
                "§7位置: §fX:" + (int) loc.getX() + " Y:" + (int) loc.getY() + " Z:" + (int) loc.getZ(),
                "§7放置时间: §f" + decoration.getPlacedAt(),
                "",
                "§c右键移除此装饰"
            ));
            item.setItemMeta(itemMeta);

            setIntTag(item, ID_KEY, decoration.getId());

            gui.setItem(slot, item);
            slot++;
        }
    }

    /**
     * 添加管理按钮
     */
    private static void addManagementButtons(Inventory gui, Village village) {
        ItemStack clearButton = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta clearMeta = clearButton.getItemMeta();
        clearMeta.setDisplayName("§c清理所有装饰");
        clearMeta.setLore(java.util.Arrays.asList("§7移除所有装饰", "§7(不可恢复)", "§c危险操作"));
        clearButton.setItemMeta(clearMeta);
        gui.setItem(46, clearButton);
    }

    /**
     * 处理装饰GUI点击
     */
    public static boolean handleGUIClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null) return false;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return false;

        // 关闭按钮
        if (slot == 53 && "§c关闭".equals(meta.getDisplayName())) {
            player.closeInventory();
            return true;
        }

        // 购买装饰（通过装饰ID标签识别）
        String decorationKey = getStringTag(clickedItem, TAG_KEY);
        if (decorationKey != null) {
            handleDecorationPurchase(player, decorationKey);
            return true;
        }

        // 管理装饰（通过数据库ID标签识别）
        int decorationId = getIntTag(clickedItem, ID_KEY);
        if (decorationId > 0) {
            handleDecorationRemoval(player, clickedItem, decorationId);
            return true;
        }

        // 清理所有装饰
        if ("§c清理所有装饰".equals(meta.getDisplayName())) {
            handleClearAllDecorations(player);
            return true;
        }

        return false;
    }

    /**
     * 处理装饰购买
     */
    private static void handleDecorationPurchase(Player player, String decorationKey) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有村庄！");
            return;
        }

        boolean success = DecorationManager.getInstance().purchaseDecoration(player, village, decorationKey);
        if (success) {
            player.sendMessage("§a装饰购买成功！右键放置装饰物品");
        }
    }

    /**
     * 处理装饰移除
     */
    private static void handleDecorationRemoval(Player player, ItemStack item, int decorationId) {
        DecorationManager.VillageDecoration decoration = DecorationManager.getInstance().getDecorationById(decorationId);
        if (decoration == null) {
            player.sendMessage("§c找不到该装饰");
            return;
        }

        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null || village.getId() != decoration.getVillageId()) {
            player.sendMessage("§c你不能移除其他村庄的装饰");
            return;
        }

        org.bukkit.Location loc = decoration.getLocation();
        if (loc != null) {
            DecorationManager.getInstance().removeDecoration(player, loc.getBlock());
        }
    }

    /**
     * 处理清理所有装饰
     */
    private static void handleClearAllDecorations(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有村庄！");
            return;
        }

        if (DecorationManager.getInstance().clearAllDecorations(village.getId())) {
            player.sendMessage("§a已清理村庄所有装饰");
        } else {
            player.sendMessage("§c清理装饰失败");
        }
    }

    // ============== 工具方法 ==============

    private static void fillBackground(Inventory gui) {
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);

        for (int i = 0; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, background);
            }
        }
    }

    private static void addCloseButton(Inventory gui, int slot) {
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(slot, closeButton);
    }

    private static void setStringTag(ItemStack item, NamespacedKey key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    private static String getStringTag(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.STRING) ? container.get(key, PersistentDataType.STRING) : null;
    }

    private static void setIntTag(ItemStack item, NamespacedKey key, int value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    private static int getIntTag(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.INTEGER) ? container.get(key, PersistentDataType.INTEGER) : -1;
    }
}
