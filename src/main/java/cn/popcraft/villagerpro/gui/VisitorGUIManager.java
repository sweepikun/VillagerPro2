package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VisitorManager;
import cn.popcraft.villagerpro.models.VisitorData;
import cn.popcraft.villagerpro.models.VisitorDeal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * 访客GUI管理器
 * 负责处理访客系统的所有GUI界面
 */
public class VisitorGUIManager {
    
    private static final String GUI_PREFIX = "§f[VP] ";
    
    /**
     * 显示商人GUI
     */
    public static void showMerchantGUI(Player player, VisitorData visitor) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "§6[商人] " + visitor.getName());
        
        // 填充背景
        fillBackground(gui);
        
        // 访客信息
        ItemStack visitorInfo = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta infoMeta = visitorInfo.getItemMeta();
        infoMeta.setDisplayName("§e" + visitor.getName());
        infoMeta.setLore(java.util.Arrays.asList(
            "§7类型: §f商人",
            "§7停留时间: §f" + getRemainingTime(visitor) + " 分钟",
            "",
            "§7欢迎来到我的商店！",
            "§7这里有村庄升级的珍贵物品"
        ));
        visitorInfo.setItemMeta(infoMeta);
        gui.setItem(13, visitorInfo);
        
        // 商人商品（从配置文件加载）
        addMerchantItems(gui, visitor);
        
        // 关闭按钮
        addCloseButton(gui, 26);
        
        player.openInventory(gui);
    }
    
    /**
     * 显示旅行者GUI
     */
    public static void showTravelerGUI(Player player, VisitorData visitor) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "§b[旅行者] " + visitor.getName());
        
        // 填充背景
        fillBackground(gui);
        
        // 访客信息
        ItemStack visitorInfo = new ItemStack(Material.COMPASS);
        ItemMeta infoMeta = visitorInfo.getItemMeta();
        infoMeta.setDisplayName("§e" + visitor.getName());
        infoMeta.setLore(java.util.Arrays.asList(
            "§7类型: §f旅行者",
            "§7停留时间: §f" + getRemainingTime(visitor) + " 分钟",
            "",
            "§7村长！你好！",
            "§7我需要一些帮助收集物品",
            "§7完成任务可获得独特奖励！"
        ));
        visitorInfo.setItemMeta(infoMeta);
        gui.setItem(13, visitorInfo);
        
        // 旅行者委托
        addTravelerDeals(gui, visitor, player);
        
        // 关闭按钮
        addCloseButton(gui, 26);
        
        player.openInventory(gui);
    }
    
    /**
     * 显示节日使者GUI
     */
    public static void showFestivalGUI(Player player, VisitorData visitor) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "§d[节日使者] " + visitor.getName());
        
        // 填充背景
        fillBackground(gui);
        
        // 访客信息
        ItemStack visitorInfo = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta infoMeta = visitorInfo.getItemMeta();
        infoMeta.setDisplayName("§e" + visitor.getName());
        infoMeta.setLore(java.util.Arrays.asList(
            "§7类型: §f节日使者",
            "§7停留时间: §f" + getRemainingTime(visitor) + " 分钟",
            "",
            "§7节日快乐！",
            "§7节日期间村庄产出+50%！",
            "§7快来领取节日奖励吧！"
        ));
        visitorInfo.setItemMeta(infoMeta);
        gui.setItem(13, visitorInfo);
        
        // 节日奖励
        addFestivalRewards(gui, visitor, player);
        
        // 关闭按钮
        addCloseButton(gui, 26);
        
        player.openInventory(gui);
    }
    
    /**
     * 添加商人商品到GUI
     */
    private static void addMerchantItems(Inventory gui, VisitorData visitor) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object itemsObj = plugin.getConfig().get("visitors.merchant.items");
        if (!(itemsObj instanceof java.util.List)) return;
        
        @SuppressWarnings("unchecked")
        java.util.List<Object> itemsList = (java.util.List<Object>) itemsObj;
        
        int slot = 10;
        for (Object itemObj : itemsList) {
            if (slot >= 17) break; // 最多显示8个商品
            
            if (!(itemObj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> itemData = (Map<String, Object>) itemObj;
            
            try {
                String itemType = (String) itemData.get("item");
                String name = (String) itemData.getOrDefault("name", itemType);
                String description = (String) itemData.getOrDefault("description", "");
                String rarity = (String) itemData.getOrDefault("rarity", "common");
                
                // 根据稀有度设置颜色
                String color = getRarityColor(rarity);
                
                Material material;
                if (itemType.startsWith("villagerpro:")) {
                    // 自定义物品，需要特殊处理
                    material = Material.PLAYER_HEAD; // 临时使用头颅作为占位符
                } else {
                    material = Material.valueOf(itemType);
                }
                
                ItemStack item = new ItemStack(material);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName(color + name);
                
                // 设置价格信息
                List<Map<String, Object>> prices = (List<Map<String, Object>>) itemData.get("price");
                String priceText = "§7价格: ";
                for (int i = 0; i < prices.size(); i++) {
                    Map<String, Object> priceData = prices.get(i);
                    String priceType = (String) priceData.get("type");
                    double amount = (double) priceData.get("amount");
                    
                    switch (priceType.toLowerCase()) {
                        case "vault":
                            priceText += "§6" + amount + "金币";
                            break;
                        case "playerpoints":
                            priceText += "§b" + (int)amount + "点券";
                            break;
                        case "itemsadder":
                            priceText += "§5" + amount + "自定义物品";
                            break;
                    }
                    if (i < prices.size() - 1) priceText += " §7+ ";
                }
                
                itemMeta.setLore(java.util.Arrays.asList(
                    priceText,
                    "",
                    "§7" + description,
                    "",
                    "§a左键购买"
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于购买处理
                setItemTag(item, "visitor_purchase", itemData);
                
                gui.setItem(slot, item);
                slot++;
                
            } catch (Exception e) {
                VillagerPro.getInstance().getLogger().warning("处理商人商品时出错: " + e.getMessage());
            }
        }
    }
    
    /**
     * 添加旅行者委托到GUI
     */
    private static void addTravelerDeals(Inventory gui, VisitorData visitor, Player player) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object dealsObj = plugin.getConfig().get("visitors.traveler.deals");
        if (!(dealsObj instanceof java.util.List)) return;
        
        @SuppressWarnings("unchecked")
        java.util.List<Object> dealsList = (java.util.List<Object>) dealsObj;
        
        int slot = 10;
        for (Object dealObj : dealsList) {
            if (slot >= 17) break; // 最多显示7个委托
            
            if (!(dealObj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> dealData = (Map<String, Object>) dealObj;
            
            try {
                String name = (String) dealData.get("name");
                String description = (String) dealData.get("description");
                String itemType = (String) dealData.get("item");
                int amount = (Integer) dealData.get("amount");
                
                Map<String, Object> rewardData = (Map<String, Object>) dealData.get("reward");
                String rewardType = (String) rewardData.get("item");
                int rewardAmount = (Integer) rewardData.get("amount");
                String rewardName = (String) rewardData.getOrDefault("name", rewardType);
                
                Material material = Material.valueOf(itemType);
                ItemStack item = new ItemStack(material);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName("§e" + name);
                
                // 检查玩家是否已完成此委托
                boolean completed = isDealCompleted(player, visitor.getId(), name);
                String statusPrefix = completed ? "§a✅ " : "§7";
                
                itemMeta.setLore(java.util.Arrays.asList(
                    statusPrefix + "§7描述: " + description,
                    "§7需要: §f" + amount + " x " + itemType,
                    "§7奖励: §a" + rewardAmount + " x " + rewardName,
                    "",
                    completed ? "§a委托已完成！" : "§a左键接受委托"
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于委托处理
                setItemTag(item, "visitor_deal", dealData);
                
                gui.setItem(slot, item);
                slot++;
                
            } catch (Exception e) {
                VillagerPro.getInstance().getLogger().warning("处理旅行者委托时出错: " + e.getMessage());
            }
        }
    }
    
    /**
     * 添加节日奖励到GUI
     */
    private static void addFestivalRewards(Inventory gui, VisitorData visitor, Player player) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object festivalsObj = plugin.getConfig().get("visitors.festival.festivals");
        if (!(festivalsObj instanceof java.util.List)) return;
        
        @SuppressWarnings("unchecked")
        java.util.List<Object> festivalsList = (java.util.List<Object>) festivalsObj;
        
        int slot = 10;
        for (Object festivalObj : festivalsList) {
            if (slot >= 17) break; // 最多显示7个节日奖励
            
            if (!(festivalObj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> festivalData = (Map<String, Object>) festivalObj;
            
            try {
                String name = (String) festivalData.get("name");
                String description = (String) festivalData.get("description");
                String effect = (String) ((Map<String, Object>) festivalData.get("effect")).getOrDefault("production_boost", "1.0");
                
                // 检查玩家是否已领取
                boolean claimed = isFestivalClaimed(player, visitor.getId(), name);
                String statusPrefix = claimed ? "§a✅ " : "§7";
                
                Material material = Material.FIREWORK_STAR;
                ItemStack item = new ItemStack(material);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName("§d" + name);
                
                itemMeta.setLore(java.util.Arrays.asList(
                    statusPrefix + "§7" + description,
                    "§7效果: §e" + effect + "x 产出",
                    "",
                    claimed ? "§a已领取！" : "§a左键领取"
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于奖励领取
                setItemTag(item, "festival_reward", festivalData);
                
                gui.setItem(slot, item);
                slot++;
                
            } catch (Exception e) {
                VillagerPro.getInstance().getLogger().warning("处理节日奖励时出错: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理访客GUI的点击事件
     */
    public static boolean handleGUIClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null) return false;
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return false;
        
        // 关闭按钮
        if (slot == 26 && "§c关闭".equals(meta.getDisplayName())) {
            player.closeInventory();
            return true;
        }
        
        // 商人购买处理
        if (hasItemTag(clickedItem, "visitor_purchase")) {
            handleMerchantPurchase(player, clickedItem);
            return true;
        }
        
        // 旅行者委托处理
        if (hasItemTag(clickedItem, "visitor_deal")) {
            handleTravelerDeal(player, clickedItem);
            return true;
        }
        
        // 节日奖励处理
        if (hasItemTag(clickedItem, "festival_reward")) {
            handleFestivalClaim(player, clickedItem);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理商人购买
     */
    private static void handleMerchantPurchase(Player player, ItemStack item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> itemData = (Map<String, Object>) getItemTag(item, "visitor_purchase");
        
        try {
            // 获取价格
            List<Map<String, Object>> prices = (List<Map<String, Object>>) itemData.get("price");
            String itemType = (String) itemData.get("item");
            String name = (String) itemData.getOrDefault("name", itemType);
            
            // 检查玩家资源
            if (!canAffordPrice(player, prices)) {
                player.sendMessage("§c资源不足，无法购买 " + name);
                return;
            }
            
            // 扣除资源
            if (deductPrice(player, prices)) {
                // 给予物品
                giveItemToPlayer(player, itemType, 1);
                player.sendMessage("§a成功购买 " + name + "！");
                
                // 记录交易
                VisitorManager.getInstance().createDeal(
                    null, "purchase", itemType, 1, 0, player.getName()
                );
            }
            
        } catch (Exception e) {
            player.sendMessage("§c购买失败，请重试");
            VillagerPro.getInstance().getLogger().warning("购买处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理旅行者委托
     */
    private static void handleTravelerDeal(Player player, ItemStack item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> dealData = (Map<String, Object>) getItemTag(item, "visitor_deal");
        
        try {
            String name = (String) dealData.get("name");
            String itemType = (String) dealData.get("item");
            int amount = (Integer) dealData.get("amount");
            String displayName = (String) dealData.get("name");
            
            // 检查玩家背包
            if (!hasItemInInventory(player, itemType, amount)) {
                player.sendMessage("§c背包中没有足够的 " + itemType + " (需要 " + amount + " 个)");
                return;
            }
            
            // 检查是否已接受委托
            if (isDealAccepted(player, name)) {
                player.sendMessage("§c你已经开始这个委托了！");
                return;
            }
            
            // 接受委托
            acceptDeal(player, name);
            player.sendMessage("§a成功接受委托: " + displayName);
            player.sendMessage("§7收集 " + amount + " 个 " + itemType + " 后右键访客交付");
            
        } catch (Exception e) {
            player.sendMessage("§c委托处理失败，请重试");
            VillagerPro.getInstance().getLogger().warning("委托处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理节日奖励领取
     */
    private static void handleFestivalClaim(Player player, ItemStack item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> festivalData = (Map<String, Object>) getItemTag(item, "festival_reward");
        
        try {
            String name = (String) festivalData.get("name");
            
            // 检查是否已领取
            if (isFestivalClaimed(player, 0, name)) {
                player.sendMessage("§c这个奖励已经领取过了！");
                return;
            }
            
            // 给予奖励
            Map<String, Object> effect = (Map<String, Object>) festivalData.get("effect");
            if (effect.containsKey("item")) {
                String rewardItem = (String) effect.get("item");
                int amount = (Integer) effect.getOrDefault("amount", 1);
                giveItemToPlayer(player, rewardItem, amount);
            }
            
            // 记录领取
            claimFestivalReward(player, name);
            player.sendMessage("§a成功领取节日奖励: " + name + "！");
            
        } catch (Exception e) {
            player.sendMessage("§c领取失败，请重试");
            VillagerPro.getInstance().getLogger().warning("节日奖励领取失败: " + e.getMessage());
        }
    }
    
    // ============== 工具方法 ==============
    
    private static void fillBackground(Inventory gui) {
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        
        for (int i = 0; i < 27; i++) {
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
    
    private static String getRarityColor(String rarity) {
        switch (rarity.toLowerCase()) {
            case "epic": return "§5";
            case "rare": return "§b";
            case "uncommon": return "§a";
            default: return "§7";
        }
    }
    
    private static String getRemainingTime(VisitorData visitor) {
        long remaining = (visitor.getExpiresAt().getTime() - System.currentTimeMillis()) / 60000;
        return Math.max(0, (int) remaining) + "";
    }
    
    // 简化的工具方法（实际实现中需要更复杂的逻辑）
    private static void setItemTag(ItemStack item, String key, Object value) {
        // 这里需要使用NBT标签或ItemMeta来存储数据
        // 简化实现，实际使用中需要更完整的实现
    }
    
    private static Object getItemTag(ItemStack item, String key) {
        // 从ItemMeta获取存储的数据
        return null;
    }
    
    private static boolean hasItemTag(ItemStack item, String key) {
        return getItemTag(item, key) != null;
    }
    
    private static boolean canAffordPrice(Player player, List<Map<String, Object>> prices) {
        // 检查玩家资源
        return true; // 简化实现
    }
    
    private static boolean deductPrice(Player player, List<Map<String, Object>> prices) {
        // 扣除玩家资源
        return true; // 简化实现
    }
    
    private static void giveItemToPlayer(Player player, String itemType, int amount) {
        // 给玩家物品
        // 简化实现
    }
    
    private static boolean hasItemInInventory(Player player, String itemType, int amount) {
        // 检查玩家背包
        return true; // 简化实现
    }
    
    private static boolean isDealCompleted(Player player, int visitorId, String dealName) {
        // 检查委托是否完成
        return false; // 简化实现
    }
    
    private static boolean isDealAccepted(Player player, String dealName) {
        // 检查是否已接受委托
        return false; // 简化实现
    }
    
    private static void acceptDeal(Player player, String dealName) {
        // 记录委托接受
    }
    
    private static boolean isFestivalClaimed(Player player, int visitorId, String festivalName) {
        // 检查节日奖励是否已领取
        return false; // 简化实现
    }
    
    private static void claimFestivalReward(Player player, String festivalName) {
        // 记录奖励领取
    }
}