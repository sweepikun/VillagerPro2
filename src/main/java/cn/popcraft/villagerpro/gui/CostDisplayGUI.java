package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * 消耗物品展示GUI管理器
 * 当玩家使用消耗物品功能时，展示消耗物品的界面
 */
public class CostDisplayGUI {
    
    private static final String GUI_TITLE = ChatColor.GOLD + "消耗物品确认";
    
    /**
     * 打开消耗物品确认界面
     */
    public static void openCostConfirmationGUI(Player player, String actionName, List<CostEntry> costs, Runnable onConfirm, Runnable onCancel) {
        Inventory inventory = Bukkit.createInventory(null, 27, GUI_TITLE);
        
        // 动作名称标题
        ItemStack titleItem = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName(ChatColor.GOLD + "操作确认");
        
        java.util.List<String> titleLore = new java.util.ArrayList<>();
        titleLore.add(ChatColor.GRAY + "即将执行: " + actionName);
        titleLore.add("");
        titleLore.add(ChatColor.YELLOW + "将消耗以下物品:");
        
        titleMeta.setLore(titleLore);
        titleItem.setItemMeta(titleMeta);
        inventory.setItem(4, titleItem);
        
        // 显示消耗物品（最多6个）
        int slot = 9; // 第10格开始
        for (CostEntry cost : costs) {
            if (slot > 14) break; // 最多显示6个物品
            
            ItemStack costItem = createCostItem(cost);
            inventory.setItem(slot, costItem);
            slot++;
        }
        
        // 确认按钮
        ItemStack confirmButton = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "§l确认执行");
        
        java.util.List<String> confirmLore = new java.util.ArrayList<>();
        confirmLore.add(ChatColor.GRAY + "点击确认执行此操作");
        confirmLore.add(ChatColor.GRAY + "物品将被永久消耗");
        
        confirmMeta.setLore(confirmLore);
        confirmButton.setItemMeta(confirmMeta);
        inventory.setItem(18, confirmButton);
        
        // 取消按钮
        ItemStack cancelButton = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "§l取消操作");
        
        java.util.List<String> cancelLore = new java.util.ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "点击取消此操作");
        cancelLore.add(ChatColor.GRAY + "不会消耗任何物品");
        
        cancelMeta.setLore(cancelLore);
        cancelButton.setItemMeta(cancelMeta);
        inventory.setItem(26, cancelButton);
        
        // 存储操作回调（使用玩家UUID作为key）
        player.openInventory(inventory);
        
        // 存储回调信息到player的metadata
        player.setMetadata("cost_action_confirm", new org.bukkit.metadata.MetadataValue() {
            @Override
            public Object value() { return onConfirm; }
            @Override
            public int asInt() { return 0; }
            @Override
            public float asFloat() { return 0; }
            @Override
            public double asDouble() { return 0; }
            @Override
            public long asLong() { return 0; }
            @Override
            public boolean asBoolean() { return true; }
            @Override
            public String asString() { return "confirm"; }
            @Override
            public byte asByte() { return 0; }
            @Override
            public short asShort() { return 0; }
            @Override
            public void invalidate() {}
            @Override
            public org.bukkit.plugin.Plugin getOwningPlugin() { return VillagerPro.getInstance(); }
        });
        
        player.setMetadata("cost_action_cancel", new org.bukkit.metadata.MetadataValue() {
            @Override
            public Object value() { return onCancel; }
            @Override
            public int asInt() { return 0; }
            @Override
            public float asFloat() { return 0; }
            @Override
            public double asDouble() { return 0; }
            @Override
            public long asLong() { return 0; }
            @Override
            public boolean asBoolean() { return true; }
            @Override
            public String asString() { return "cancel"; }
            @Override
            public byte asByte() { return 0; }
            @Override
            public short asShort() { return 0; }
            @Override
            public void invalidate() {}
            @Override
            public org.bukkit.plugin.Plugin getOwningPlugin() { return VillagerPro.getInstance(); }
        });
    }
    
    /**
     * 创建消耗物品的显示物品
     */
    private static ItemStack createCostItem(CostEntry cost) {
        ItemStack item;
        
        switch (cost.getType().toLowerCase()) {
            case "vault":
                // 金币：金锭
                item = new ItemStack(Material.GOLD_INGOT);
                ItemMeta vaultMeta = item.getItemMeta();
                vaultMeta.setDisplayName(ChatColor.GOLD + "§l金币");
                java.util.List<String> vaultLore = new java.util.ArrayList<>();
                vaultLore.add(ChatColor.GRAY + "消耗数量: " + ChatColor.YELLOW + (int)cost.getAmount());
                vaultLore.add(ChatColor.GRAY + "将实际扣除虚拟金币");
                vaultMeta.setLore(vaultLore);
                item.setItemMeta(vaultMeta);
                break;
                
            case "playerpoints":
                // 点券：钻石
                item = new ItemStack(Material.DIAMOND);
                ItemMeta pointsMeta = item.getItemMeta();
                pointsMeta.setDisplayName(ChatColor.BLUE + "§l点券");
                java.util.List<String> pointsLore = new java.util.ArrayList<>();
                pointsLore.add(ChatColor.GRAY + "消耗数量: " + ChatColor.BLUE + (int)cost.getAmount());
                pointsLore.add(ChatColor.GRAY + "将实际扣除虚拟点券");
                pointsMeta.setLore(pointsLore);
                item.setItemMeta(pointsMeta);
                break;
                
            case "itemsadder":
                // ItemsAdder物品（如果存在则显示实际物品，否则显示羊毛）
                item = getItemsAdderItem(cost.getItem(), (int)cost.getAmount());
                if (item == null) {
                    // 如果ItemsAdder物品不存在，显示羊毛并标注
                    item = new ItemStack(Material.WHITE_WOOL);
                    ItemMeta woolMeta = item.getItemMeta();
                    woolMeta.setDisplayName(ChatColor.RED + "§l" + cost.getItem());
                    java.util.List<String> woolLore = new java.util.ArrayList<>();
                    woolLore.add(ChatColor.GRAY + "物品ID: " + cost.getItem());
                    woolLore.add(ChatColor.GRAY + "消耗数量: " + ChatColor.WHITE + (int)cost.getAmount());
                    woolLore.add(ChatColor.RED + "⚠️ ItemsAdder未安装或物品不存在");
                    woolLore.add(ChatColor.GRAY + "将在此服务器中显示为羊毛");
                    woolMeta.setLore(woolLore);
                    item.setItemMeta(woolMeta);
                }
                break;
                
            default:
                // 其他类型，显示石头
                item = new ItemStack(Material.STONE);
                ItemMeta defaultMeta = item.getItemMeta();
                defaultMeta.setDisplayName(ChatColor.GRAY + "§l" + cost.getType());
                java.util.List<String> defaultLore = new java.util.ArrayList<>();
                defaultLore.add(ChatColor.GRAY + "消耗数量: " + (int)cost.getAmount());
                defaultMeta.setLore(defaultLore);
                item.setItemMeta(defaultMeta);
                break;
        }
        
        return item;
    }
    
    /**
     * 获取ItemsAdder物品（如果存在）
     */
    private static ItemStack getItemsAdderItem(String itemNamespace, int amount) {
        try {
            // 检查ItemsAdder是否可用
            org.bukkit.plugin.Plugin itemsAdderPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("ItemsAdder");
            if (itemsAdderPlugin == null) {
                return null; // ItemsAdder未安装
            }
            
            // 尝试获取ItemsAdder API
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdderAPI");
            java.lang.reflect.Method getInstanceMethod = itemsAdderAPI.getMethod("getInstance");
            Object apiInstance = getInstanceMethod.invoke(null);
            
            java.lang.reflect.Method getItemStackMethod = apiInstance.getClass().getMethod("getItemStack", String.class);
            Object itemStackObj = getItemStackMethod.invoke(apiInstance, itemNamespace);
            
            if (itemStackObj instanceof ItemStack) {
                ItemStack itemStack = (ItemStack) itemStackObj;
                itemStack.setAmount(amount);
                return itemStack;
            }
            
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().warning("获取ItemsAdder物品失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 处理确认按钮点击
     */
    public static void handleConfirmClick(Player player) {
        // 获取并移除确认回调
        List<org.bukkit.metadata.MetadataValue> confirmCallbacks = player.getMetadata("cost_action_confirm");
        if (!confirmCallbacks.isEmpty()) {
            org.bukkit.metadata.MetadataValue callback = confirmCallbacks.get(0);
            if (callback.value() instanceof Runnable) {
                ((Runnable) callback.value()).run();
            }
        }
        
        // 清理metadata
        player.removeMetadata("cost_action_confirm", VillagerPro.getInstance());
        player.removeMetadata("cost_action_cancel", VillagerPro.getInstance());
        
        player.closeInventory();
    }
    
    /**
     * 处理取消按钮点击
     */
    public static void handleCancelClick(Player player) {
        // 获取并移除取消回调
        List<org.bukkit.metadata.MetadataValue> cancelCallbacks = player.getMetadata("cost_action_cancel");
        if (!cancelCallbacks.isEmpty()) {
            org.bukkit.metadata.MetadataValue callback = cancelCallbacks.get(0);
            if (callback.value() instanceof Runnable) {
                ((Runnable) callback.value()).run();
            }
        }
        
        // 清理metadata
        player.removeMetadata("cost_action_confirm", VillagerPro.getInstance());
        player.removeMetadata("cost_action_cancel", VillagerPro.getInstance());
        
        player.closeInventory();
    }
}