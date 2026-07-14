package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.ProductionStatsManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillageOrderManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.WarehouseManager;
import cn.popcraft.villagerpro.managers.WarehouseRuleManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillageOrder;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.models.WarehouseItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class VillageOperationsGUI {
    public static final String ORDER_TITLE = "每日订单";
    public static final String STATS_TITLE = "生产统计";
    public static final String RULES_TITLE = "仓库规则";

    private VillageOperationsGUI() {
    }

    public static void openOrderBoard(Player player) {
        if (!VillagerPro.getInstance().getConfig().getBoolean("orders.enabled", true)) {
            player.sendMessage("§c每日订单功能当前未启用");
            return;
        }
        Village village = ownedVillage(player);
        if (village == null) return;
        Inventory gui = Bukkit.createInventory(null, 54, GUIManager.getGUIPrefix() + ORDER_TITLE);
        List<VillageOrder> orders = VillageOrderManager.getTodayOrders(village);
        for (int index = 0; index < orders.size() && index < 45; index++) {
            VillageOrder order = orders.get(index);
            Material material = Material.getMaterial(order.getItemType());
            ItemStack icon = new ItemStack(material == null ? Material.BARRIER : material);
            ItemMeta meta = icon.getItemMeta();
            if (order.isCompleted()) {
                meta.setDisplayName("§a已完成：" + order.getItemType());
            } else if (order.isPayoutPending()) {
                meta.setDisplayName("§6待发金币：" + order.getItemType());
            } else if (order.isPayoutProcessing()) {
                meta.setDisplayName("§c结算确认中：" + order.getItemType());
            } else {
                meta.setDisplayName("§e交付：" + order.getItemType());
            }
            int stored = warehouseAmount(village.getId(), order.getItemType());
            int reserve = WarehouseRuleManager.getItemRule(
                    village.getId(), order.getItemType()).getReserveAmount();
            int available = WarehouseManager.getExtractableAmount(village.getId(), order.getItemType());
            List<String> lore = new ArrayList<>();
            lore.add("§7需求: §f" + order.getAmountRequired());
            lore.add("§7库存: §f" + stored + " §8(保留 " + reserve + ")");
            lore.add("§7可交付: §f" + available);
            double effectiveMoney = order.isPayoutPending() || order.isPayoutProcessing()
                    ? order.getPayoutMoney()
                    : VillageOrderManager.getEffectiveRewardMoney(village, order);
            lore.add("§7金币奖励: §6" + formatMoney(effectiveMoney));
            if (Double.compare(effectiveMoney, order.getRewardMoney()) != 0) {
                lore.add("§8配置基础奖励: " + formatMoney(order.getRewardMoney())
                        + "（受市场清算上限影响）");
            }
            lore.add("§7繁荣度奖励: §d" + order.getRewardProsperity());
            lore.add("");
            if (order.isCompleted()) {
                lore.add("§a今日已完成");
            } else if (order.isPayoutPending()) {
                lore.add("§e点击重试金币发放（不会再次扣货）");
            } else if (order.isPayoutProcessing()) {
                lore.add("§cVault 结果待管理员核对，为防重复发放已锁定");
            } else {
                lore.add("§e点击提交订单");
            }
            lore.add("§8订单ID: " + order.getId());
            meta.setLore(lore);
            icon.setItemMeta(meta);
            gui.setItem(index, icon);
        }

        boolean autoSubmit = WarehouseRuleManager.isAutoSubmitOrders(village.getId());
        ItemStack autoButton = new ItemStack(Material.HOPPER);
        ItemMeta autoMeta = autoButton.getItemMeta();
        autoMeta.setDisplayName("§b自动交付: " + (autoSubmit ? "§a开启" : "§c关闭"));
        autoMeta.setLore(Arrays.asList("§7仅使用超过保留量的库存", "§e点击切换"));
        autoButton.setItemMeta(autoMeta);
        gui.setItem(49, autoButton);
        addNavigation(gui);
        player.openInventory(gui);
    }

    public static void openProductionStats(Player player) {
        Village village = ownedVillage(player);
        if (village == null) return;
        Inventory gui = Bukkit.createInventory(null, 54, GUIManager.getGUIPrefix() + STATS_TITLE);
        int windowMinutes = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("production_stats.window_minutes", 60));
        Map<Integer, ProductionStatsManager.Stats> stats = ProductionStatsManager.getRecentStats(
                village.getId(), System.currentTimeMillis() - windowMinutes * 60_000L);
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        for (int index = 0; index < villagers.size() && index < 45; index++) {
            VillagerData villager = villagers.get(index);
            String iconName = VillagerPro.getInstance().getConfig().getString(
                    "villager.professions." + villager.getProfession() + ".icon", "PLAYER_HEAD");
            Material material = Material.getMaterial(iconName);
            ItemStack icon = new ItemStack(material == null ? Material.PLAYER_HEAD : material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§e" + VillagerManager.getProfessionDisplayName(villager.getProfession())
                    + " §7(ID: " + villager.getId() + ")");
            ProductionStatsManager.Stats summary = stats.getOrDefault(villager.getId(),
                    new ProductionStatsManager.Stats(0, 0, 0, 0, 0, 0));
            List<String> lore = new ArrayList<>();
            lore.add("§7统计窗口: §f最近 " + windowMinutes + " 分钟");
            lore.add("§7到期尝试: §f" + summary.getAttempts());
            lore.add("§7成功入库: §a" + summary.getSuccesses() + " 次 / "
                    + summary.getStoredAmount() + " 个");
            lore.add("§7概率未命中: §e" + summary.getMisses());
            lore.add("§7规则/容量阻塞: §c" + summary.getBlocked());
            lore.add("§7生活需求效率: §f" + String.format("%.0f%%",
                    cn.popcraft.villagerpro.managers.NeedsManager
                            .getProductionMultiplier(villager) * 100));
            lore.add("§7当前状态: §f" + ProductionStatsManager.getDiagnostic(villager.getId()));
            lore.add("§7最近结果: §f" + ProductionStatsManager.getLastOutcome(villager.getId()));
            if (summary.getLastEventAt() > 0) {
                lore.add("§7最近记录: §f" + new SimpleDateFormat("HH:mm:ss")
                        .format(new Date(summary.getLastEventAt())));
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
            gui.setItem(index, icon);
        }
        addNavigation(gui);
        player.openInventory(gui);
    }

    public static void openWarehouseRules(Player player) {
        Village village = ownedVillage(player);
        if (village == null) return;
        Inventory gui = Bukkit.createInventory(null, 54, GUIManager.getGUIPrefix() + RULES_TITLE);
        List<String> itemTypes = WarehouseRuleManager.getControllableItemTypes(village.getId());
        for (int index = 0; index < itemTypes.size() && index < 45; index++) {
            String itemType = itemTypes.get(index);
            Material material = Material.getMaterial(itemType);
            ItemStack icon = new ItemStack(material == null ? Material.BARRIER : material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§e" + itemType);
            WarehouseRuleManager.ItemRule rule = WarehouseRuleManager.getItemRule(
                    village.getId(), itemType);
            int stored = warehouseAmount(village.getId(), itemType);
            int available = WarehouseManager.getExtractableAmount(village.getId(), itemType);
            int effectiveReserve = Math.max(0, stored - available);
            meta.setLore(Arrays.asList(
                    "§7库存: §f" + stored,
                    "§7手动保留量: §f" + rule.getReserveAmount(),
                    "§7当前有效保护量: §f" + effectiveReserve,
                    "§7可提取/交付: §f" + available,
                    "§7生产: " + (rule.isProductionEnabled() ? "§a开启" : "§c停产"),
                    "",
                    "§e左键§7调整保留量",
                    "§e右键§7切换生产开关"));
            icon.setItemMeta(meta);
            gui.setItem(index, icon);
        }

        boolean autoSubmit = WarehouseRuleManager.isAutoSubmitOrders(village.getId());
        ItemStack auto = new ItemStack(Material.HOPPER);
        ItemMeta autoMeta = auto.getItemMeta();
        autoMeta.setDisplayName("§b自动交付订单: " + (autoSubmit ? "§a开启" : "§c关闭"));
        autoMeta.setLore(Arrays.asList("§7仅消耗超过保留量的库存", "§e点击切换"));
        auto.setItemMeta(autoMeta);
        gui.setItem(48, auto);

        WarehouseRuleManager.OverflowMode overflow =
                WarehouseRuleManager.getOverflowMode(village.getId());
        ItemStack overflowButton = new ItemStack(Material.DROPPER);
        ItemMeta overflowMeta = overflowButton.getItemMeta();
        overflowMeta.setDisplayName("§b溢出余量: "
                + (overflow == WarehouseRuleManager.OverflowMode.DROP ? "§e掉落" : "§c丢弃"));
        overflowMeta.setLore(Arrays.asList(
                "§7仓库仍会先存入剩余容量",
                "§7掉落模式会把装不下的余量掉在村民脚下",
                "§e点击切换"));
        overflowButton.setItemMeta(overflowMeta);
        gui.setItem(50, overflowButton);
        addNavigation(gui);
        player.openInventory(gui);
    }

    public static void handleOrderClick(Player player, ItemStack item) {
        if (handleNavigation(player, item)) return;
        Village village = ownedVillage(player);
        if (village == null || !item.hasItemMeta()) return;
        if (item.getType() == Material.HOPPER) {
            boolean current = WarehouseRuleManager.isAutoSubmitOrders(village.getId());
            WarehouseRuleManager.setAutoSubmitOrders(village.getId(), !current);
            openOrderBoard(player);
            return;
        }
        Integer orderId = readOrderId(item.getItemMeta());
        if (orderId != null) {
            VillageOrderManager.submitOrder(player, village, orderId, false);
            openOrderBoard(player);
        }
    }

    public static void handleStatsClick(Player player, ItemStack item) {
        handleNavigation(player, item);
    }

    public static void handleRulesClick(Player player, ItemStack item, ClickType clickType) {
        if (handleNavigation(player, item)) return;
        Village village = ownedVillage(player);
        if (village == null || !item.hasItemMeta()) return;
        if (item.getType() == Material.HOPPER) {
            boolean current = WarehouseRuleManager.isAutoSubmitOrders(village.getId());
            WarehouseRuleManager.setAutoSubmitOrders(village.getId(), !current);
            openWarehouseRules(player);
            return;
        }
        if (item.getType() == Material.DROPPER) {
            WarehouseRuleManager.OverflowMode current =
                    WarehouseRuleManager.getOverflowMode(village.getId());
            WarehouseRuleManager.setOverflowMode(village.getId(),
                    current == WarehouseRuleManager.OverflowMode.DROP
                            ? WarehouseRuleManager.OverflowMode.DISCARD
                            : WarehouseRuleManager.OverflowMode.DROP);
            openWarehouseRules(player);
            return;
        }
        String displayName = item.getItemMeta().getDisplayName();
        if (displayName == null || !displayName.startsWith("§e")) return;
        String itemType = displayName.substring(2);
        WarehouseRuleManager.ItemRule rule = WarehouseRuleManager.getItemRule(
                village.getId(), itemType);
        if (clickType.isLeftClick()) {
            WarehouseRuleManager.setReserveAmount(village.getId(), itemType,
                    nextReserve(rule.getReserveAmount()));
        } else if (clickType.isRightClick()) {
            WarehouseRuleManager.setProductionEnabled(
                    village.getId(), itemType, !rule.isProductionEnabled());
        }
        openWarehouseRules(player);
    }

    private static int nextReserve(int current) {
        int[] values = {0, 16, 32, 64, 128};
        for (int index = 0; index < values.length; index++) {
            if (current < values[index]) return values[index];
            if (current == values[index]) return values[(index + 1) % values.length];
        }
        return 0;
    }

    private static Integer readOrderId(ItemMeta meta) {
        if (meta == null || meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            if (line.startsWith("§8订单ID: ")) {
                try {
                    return Integer.parseInt(line.substring("§8订单ID: ".length()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void addNavigation(Inventory gui) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c返回村庄");
        back.setItemMeta(backMeta);
        gui.setItem(gui.getSize() - 9, back);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        close.setItemMeta(closeMeta);
        gui.setItem(gui.getSize() - 1, close);
    }

    private static boolean handleNavigation(Player player, ItemStack item) {
        if (item.getType() == Material.ARROW) {
            GUIManager.openVillageGUI(player);
            return true;
        }
        if (item.getType() == Material.BARRIER) {
            player.closeInventory();
            return true;
        }
        return false;
    }

    private static Village ownedVillage(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) player.sendMessage("§c你还没有创建村庄！");
        return village;
    }

    private static int warehouseAmount(int villageId, String itemType) {
        WarehouseItem item = WarehouseManager.getWarehouseItem(villageId, itemType);
        return item == null ? 0 : item.getAmount();
    }

    private static String formatMoney(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.format("%.2f", amount);
    }
}
