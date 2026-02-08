package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.DecorationManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * 装饰系统GUI管理器
 * 负责处理装饰购买、放置、管理的所有界面
 */
public class DecorationGUIManager {
    
    private static final String GUI_PREFIX = "§f[VP] ";
    
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
        
        // 填充背景
        fillBackground(gui);
        
        // 村庄信息
        ItemStack villageInfo = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = villageInfo.getItemMeta();
        infoMeta.setDisplayName("§6" + village.getName() + " - 装饰商店");
        infoMeta.setLore(java.util.Arrays.asList(
            "§7等级: §e" + village.getLevel(),
            "§7繁荣度: §e" + village.getProsperity(),
            "§7村民: §e" + DecorationManager.getInstance().getVillageDecorations(village.getId()).size() + " 个装饰"
        ));
        villageInfo.setItemMeta(infoMeta);
        gui.setItem(4, villageInfo);
        
        // 装饰分类按钮
        addCategoryButtons(gui);
        
        // 装饰商品
        addDecorationItems(gui, player, village);
        
        // 关闭按钮
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
        
        // 填充背景
        fillBackground(gui);
        
        // 村庄信息
        ItemStack villageInfo = new ItemStack(Material.MAP);
        ItemMeta infoMeta = villageInfo.getItemMeta();
        infoMeta.setDisplayName("§6" + village.getName() + " - 装饰管理");
        infoMeta.setLore(java.util.Arrays.asList(
            "§7总装饰数: §e" + DecorationManager.getInstance().getVillageDecorations(village.getId()).size(),
            "§7平均美观度: §e" + calculateAverageBeauty(village.getId())
        ));
        villageInfo.setItemMeta(infoMeta);
        gui.setItem(4, villageInfo);
        
        // 装饰列表
        addDecorationList(gui, village, player);
        
        // 功能按钮
        addManagementButtons(gui, village);
        
        // 关闭按钮
        addCloseButton(gui, 53);
        
        player.openInventory(gui);
    }
    
    /**
     * 添加分类按钮
     */
    private static void addCategoryButtons(Inventory gui) {
        // 照明装饰
        ItemStack lightCategory = new ItemStack(Material.TORCH);
        ItemMeta lightMeta = lightCategory.getItemMeta();
        lightMeta.setDisplayName("§e照明装饰");
        lightMeta.setLore(java.util.Arrays.asList("§7路灯、火把等", "§a左键浏览"));
        lightCategory.setItemMeta(lightMeta);
        setItemTag(lightCategory, "category", "lighting");
        gui.setItem(19, lightCategory);
        
        // 美化装饰
        ItemStack beautyCategory = new ItemStack(Material.POPPY);
        ItemMeta beautyMeta = beautyCategory.getItemMeta();
        beautyMeta.setDisplayName("§d美化装饰");
        beautyMeta.setLore(java.util.Arrays.asList("§7花坛、雕塑等", "§a左键浏览"));
        beautyCategory.setItemMeta(beautyMeta);
        setItemTag(beautyCategory, "category", "beauty");
        gui.setItem(28, beautyCategory);
        
        // 功能装饰
        ItemStack functionCategory = new ItemStack(Material.CHEST);
        ItemMeta functionMeta = functionCategory.getItemMeta();
        functionMeta.setDisplayName("§b功能装饰");
        functionMeta.setLore(java.util.Arrays.asList("§7长椅、喷泉等", "§a左键浏览"));
        functionCategory.setItemMeta(functionMeta);
        setItemTag(functionCategory, "category", "functional");
        gui.setItem(37, functionCategory);
    }
    
    /**
     * 添加装饰商品
     */
    private static void addDecorationItems(Inventory gui, Player player, Village village) {
        VillagerPro plugin = VillagerPro.getInstance();
        Map<String, Object> decorationItems = plugin.getConfig().getConfigurationSection("decorations.items").getValues(false);
        
        int slot = 9;
        for (Map.Entry<String, Object> entry : decorationItems.entrySet()) {
            if (slot >= 18) break; // 最多显示9个装饰
            
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> decorationData = (Map<String, Object>) entry.getValue();
                String name = (String) decorationData.get("name");
                String description = (String) decorationData.getOrDefault("description", "");
                String material = (String) decorationData.get("material");
                int prosperityBoost = (int) decorationData.getOrDefault("prosperity_boost", 0);
                boolean worldInteraction = (boolean) decorationData.getOrDefault("world_interaction", false);
                boolean villagerInteraction = (boolean) decorationData.getOrDefault("villager_interaction", false);
                boolean villagerSit = (boolean) decorationData.getOrDefault("villager_sit", false);
                
                // 检查是否在合适的位置
                if (!isDecorationSuitable(name)) {
                    continue;
                }
                
                Material decorationMaterial = Material.valueOf(material);
                ItemStack item = new ItemStack(decorationMaterial);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName("§6" + name);
                
                // 设置价格信息
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> costList = (List<Map<String, Object>>) decorationData.get("cost");
                String costText = "§7价格: ";
                for (int i = 0; i < costList.size(); i++) {
                    Map<String, Object> costData = costList.get(i);
                    String costType = (String) costData.get("type");
                    double amount = (double) costData.get("amount");
                    
                    switch (costType.toLowerCase()) {
                        case "vault":
                            costText += "§6" + (int)amount + "金币";
                            break;
                        case "playerpoints":
                            costText += "§b" + (int)amount + "点券";
                            break;
                        case "itemsadder":
                            costText += "§5" + amount + "自定义物品";
                            if (costData.containsKey("item")) {
                                costText += "(" + costData.get("item") + ")";
                            }
                            break;
                    }
                    if (i < costList.size() - 1) costText += " §7+ ";
                }
                
                itemMeta.setLore(java.util.Arrays.asList(
                    costText,
                    "",
                    "§7" + description,
                    "",
                    "§7效果:",
                    prosperityBoost > 0 ? "§7  繁荣度 +" + prosperityBoost : "",
                    worldInteraction ? "§7  村民互动加成" : "",
                    villagerInteraction ? "§7  村民情绪提升" : "",
                    villagerSit ? "§7  村民可坐下休息" : "",
                    "",
                    "§a左键购买 §7| §c右键预览"
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于购买处理
                setItemTag(item, "decoration_item", decorationData);
                setItemTag(item, "decoration_name", name);
                
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
            if (slot >= 45) break; // 最多显示36个装饰
            
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
                "§7位置: §fX:" + (int)loc.getX() + " Y:" + (int)loc.getY() + " Z:" + (int)loc.getZ(),
                "§7放置时间: §f" + decoration.getPlacedAt(),
                "",
                "§a左键查看 §7| §c右键移除"
            ));
            item.setItemMeta(itemMeta);
            
            // 设置额外数据
            setItemTag(item, "village_decoration", decoration);
            
            gui.setItem(slot, item);
            slot++;
        }
    }
    
    /**
     * 添加管理按钮
     */
    private static void addManagementButtons(Inventory gui, Village village) {
        // 清理所有装饰
        ItemStack clearButton = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta clearMeta = clearButton.getItemMeta();
        clearMeta.setDisplayName("§c清理所有装饰");
        clearMeta.setLore(java.util.Arrays.asList("§7移除所有装饰", "§7(不可恢复)", "§c危险操作"));
        clearButton.setItemMeta(clearMeta);
        gui.setItem(46, clearButton);
        
        // 重新排列
        ItemStack arrangeButton = new ItemStack(Material.COMPASS);
        ItemMeta arrangeMeta = arrangeButton.getItemMeta();
        arrangeMeta.setDisplayName("§b重新排列");
        arrangeMeta.setLore(java.util.Arrays.asList("§7智能排列装饰", "§7优化美观度"));
        arrangeButton.setItemMeta(arrangeMeta);
        gui.setItem(47, arrangeButton);
        
        // 导出配置
        ItemStack exportButton = new ItemStack(Material.PAPER);
        ItemMeta exportMeta = exportButton.getItemMeta();
        exportMeta.setDisplayName("§a导出配置");
        exportMeta.setLore(java.util.Arrays.asList("§7导出装饰配置", "§7用于备份或分享"));
        exportButton.setItemMeta(exportMeta);
        gui.setItem(48, exportButton);
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
        
        // 购买装饰
        if (hasItemTag(clickedItem, "decoration_item")) {
            handleDecorationPurchase(player, clickedItem);
            return true;
        }
        
        // 预览装饰
        if (hasItemTag(clickedItem, "decoration_name")) {
            handleDecorationPreview(player, clickedItem);
            return true;
        }
        
        // 管理装饰
        if (hasItemTag(clickedItem, "village_decoration")) {
            handleDecorationManagement(player, clickedItem);
            return true;
        }
        
        // 管理功能
        if ("§c清理所有装饰".equals(meta.getDisplayName())) {
            handleClearAllDecorations(player);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理装饰购买
     */
    private static void handleDecorationPurchase(Player player, ItemStack item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> decorationData = (Map<String, Object>) getItemTag(item, "decoration_item");
        String decorationName = (String) decorationData.get("name");
        
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有村庄！");
            return;
        }
        
        // 执行购买
        boolean success = DecorationManager.getInstance().purchaseDecoration(player, village, decorationName);
        if (success) {
            player.sendMessage("§a装饰购买成功！");
            player.sendMessage("§7右键放置装饰物品");
        }
    }
    
    /**
     * 处理装饰预览
     */
    private static void handleDecorationPreview(Player player, ItemStack item) {
        String decorationName = (String) getItemTag(item, "decoration_name");
        
        // 这里可以实现装饰预览功能
        // 比如显示一个3D预览或信息面板
        player.sendMessage("§e正在预览装饰: " + decorationName);
        player.sendMessage("§7(预览功能开发中)");
    }
    
    /**
     * 处理装饰管理
     */
    private static void handleDecorationManagement(Player player, ItemStack item) {
        @SuppressWarnings("unchecked")
        DecorationManager.VillageDecoration decoration = 
            (DecorationManager.VillageDecoration) getItemTag(item, "village_decoration");
        
        // 显示装饰详情
        player.sendMessage("§6装饰详情:");
        player.sendMessage("§7类型: " + decoration.getDecorationType());
        player.sendMessage("§7数量: " + decoration.getAmount());
        
        org.bukkit.Location loc = decoration.getLocation();
        player.sendMessage("§7位置: X:" + (int)loc.getX() + " Y:" + (int)loc.getY() + " Z:" + (int)loc.getZ());
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
        
        // 确认对话框
        player.sendMessage("§c确定要清理所有装饰吗？");
        player.sendMessage("§7此操作不可恢复！");
        player.sendMessage("§7输入 /vp confirm 确认清理");
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
    
    private static boolean isDecorationSuitable(String decorationName) {
        // 根据装饰类型判断是否适合当前显示
        return true; // 目前都显示，实际可以根据分类筛选
    }
    
    private static int calculateAverageBeauty(int villageId) {
        // 计算平均美观度
        return 75; // 简化实现
    }
    
    // 简化的工具方法
    private static void setItemTag(ItemStack item, String key, Object value) {
        // 使用NBT标签或ItemMeta存储数据
        // 简化实现
    }
    
    private static Object getItemTag(ItemStack item, String key) {
        // 从ItemMeta获取存储的数据
        return null;
    }
    
    private static boolean hasItemTag(ItemStack item, String key) {
        return getItemTag(item, key) != null;
    }
}