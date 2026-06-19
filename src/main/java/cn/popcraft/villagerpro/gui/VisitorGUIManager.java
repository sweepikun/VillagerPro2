package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.managers.VisitorManager;
import cn.popcraft.villagerpro.models.VisitorData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访客GUI管理器
 * 负责处理访客系统的所有GUI界面
 */
public class VisitorGUIManager {
    
    private static final String GUI_PREFIX = "§f[VP] ";
    private static final NamespacedKey TAG_KEY = new NamespacedKey(VillagerPro.getInstance(), "visitor_tag");
    
    // 节日增益：festivalName -> 到期时间戳
    private static final Map<String, Long> activeFestivalBoosts = new ConcurrentHashMap<>();
    
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
                setItemTag(item, "merchant:" + name);
                
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
                
                // 检查玩家是否已接受或已完成此委托
                boolean accepted = isDealAccepted(player, name);
                boolean completed = accepted && hasItemInInventory(player, itemType, amount);
                String statusPrefix = completed ? "§a✅ " : (accepted ? "§e⏳ " : "§7");
                
                itemMeta.setLore(java.util.Arrays.asList(
                    statusPrefix + "§7描述: " + description,
                    "§7需要: §f" + amount + " x " + itemType,
                    "§7奖励: §a" + rewardAmount + " x " + rewardName,
                    "",
                    completed ? "§a左键交付委托" : (accepted ? "§7背包中物品不足" : "§a左键接受委托")
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于委托处理
                setItemTag(item, "traveler:" + name);
                
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
                Map<String, Object> effect = (Map<String, Object>) festivalData.get("effect");
                String effectStr = String.valueOf(effect.getOrDefault("production_boost", "1.0"));
                
                // 检查玩家是否已领取
                boolean claimed = isFestivalClaimed(player, name);
                String statusPrefix = claimed ? "§a✅ " : "§7";
                
                Material material = Material.FIREWORK_STAR;
                ItemStack item = new ItemStack(material);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName("§d" + name);
                
                itemMeta.setLore(java.util.Arrays.asList(
                    statusPrefix + "§7" + description,
                    "§7效果: §e" + effectStr + "x 产出",
                    "",
                    claimed ? "§a已领取！" : "§a左键领取"
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于奖励领取
                setItemTag(item, "festival:" + name);
                
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
        if (meta == null || !meta.hasDisplayName()) return false;
        
        // 关闭按钮
        if (slot == 26 && "§c关闭".equals(meta.getDisplayName())) {
            player.closeInventory();
            return true;
        }
        
        String tag = getItemTag(clickedItem);
        if (tag == null || tag.isEmpty()) return false;
        
        String title = player.getOpenInventory().getTitle();
        
        if (tag.startsWith("merchant:")) {
            String name = tag.substring("merchant:".length());
            handleMerchantPurchase(player, name);
            return true;
        }
        
        if (tag.startsWith("traveler:")) {
            String name = tag.substring("traveler:".length());
            handleTravelerDeal(player, name);
            return true;
        }
        
        if (tag.startsWith("festival:")) {
            String name = tag.substring("festival:".length());
            handleFestivalClaim(player, name);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理商人购买
     */
    private static void handleMerchantPurchase(Player player, String name) {
        Map<String, Object> itemData = findMerchantItemByName(name);
        if (itemData == null) {
            player.sendMessage("§c找不到该商品");
            return;
        }
        
        try {
            List<Map<String, Object>> prices = (List<Map<String, Object>>) itemData.get("price");
            String itemType = (String) itemData.get("item");
            
            List<CostEntry> costs = parsePrices(prices);
            if (!CostHandler.canAfford(player, costs)) {
                player.sendMessage("§c资源不足，无法购买 " + name);
                return;
            }
            
            if (!CostHandler.deduct(player, costs)) {
                player.sendMessage("§c扣除资源失败");
                return;
            }
            
            giveItemToPlayer(player, itemType, 1);
            player.sendMessage("§a成功购买 " + name + "！");
            
            // 记录交易
            VisitorManager.getInstance().createDeal(
                null, "purchase", itemType, 1, 0, player.getName()
            );
            
        } catch (Exception e) {
            player.sendMessage("§c购买失败，请重试");
            VillagerPro.getInstance().getLogger().warning("购买处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理旅行者委托
     */
    private static void handleTravelerDeal(Player player, String name) {
        Map<String, Object> dealData = findTravelerDealByName(name);
        if (dealData == null) {
            player.sendMessage("§c找不到该委托");
            return;
        }
        
        try {
            String itemType = (String) dealData.get("item");
            int amount = (Integer) dealData.get("amount");
            String displayName = (String) dealData.get("name");
            
            if (isDealAccepted(player, name)) {
                // 已接受，尝试交付
                if (hasItemInInventory(player, itemType, amount)) {
                    removeItemFromInventory(player, itemType, amount);
                    
                    Map<String, Object> rewardData = (Map<String, Object>) dealData.get("reward");
                    String rewardType = (String) rewardData.get("item");
                    int rewardAmount = (Integer) rewardData.get("amount");
                    giveItemToPlayer(player, rewardType, rewardAmount);
                    
                    clearDealAccepted(player, name);
                    player.sendMessage("§a完成委托: " + displayName + "！");
                    player.sendMessage("§a获得奖励: " + rewardAmount + " x " + rewardType);
                } else {
                    player.sendMessage("§c背包中没有足够的 " + itemType + " (需要 " + amount + " 个)");
                }
            } else {
                // 接受委托
                acceptDeal(player, name);
                player.sendMessage("§a成功接受委托: " + displayName);
                player.sendMessage("§7收集 " + amount + " 个 " + itemType + " 后再次点击交付");
            }
            
        } catch (Exception e) {
            player.sendMessage("§c委托处理失败，请重试");
            VillagerPro.getInstance().getLogger().warning("委托处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理节日奖励领取
     */
    private static void handleFestivalClaim(Player player, String name) {
        Map<String, Object> festivalData = findFestivalByName(name);
        if (festivalData == null) {
            player.sendMessage("§c找不到该节日奖励");
            return;
        }
        
        try {
            if (isFestivalClaimed(player, name)) {
                player.sendMessage("§c这个奖励已经领取过了！");
                return;
            }
            
            Map<String, Object> effect = (Map<String, Object>) festivalData.get("effect");
            if (effect.containsKey("item")) {
                String rewardItem = (String) effect.get("item");
                int amount = (Integer) effect.getOrDefault("amount", 1);
                giveItemToPlayer(player, rewardItem, amount);
            }
            
            // 激活产出加成
            double boost = 1.0;
            if (effect.containsKey("production_boost")) {
                Object boostObj = effect.get("production_boost");
                if (boostObj instanceof Number) {
                    boost = ((Number) boostObj).doubleValue();
                } else {
                    try {
                        boost = Double.parseDouble(String.valueOf(boostObj));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            long durationHours = ((Number) festivalData.getOrDefault("duration_hours", 48)).longValue();
            activateFestivalBoost(name, boost, durationHours);
            
            claimFestivalReward(player, name);
            player.sendMessage("§a成功领取节日奖励: " + name + "！");
            player.sendMessage("§7节日产出加成 " + boost + "x 将持续 " + durationHours + " 小时");
            
        } catch (Exception e) {
            player.sendMessage("§c领取失败，请重试");
            VillagerPro.getInstance().getLogger().warning("节日奖励领取失败: " + e.getMessage());
        }
    }
    
    // ============== 配置查找 ==============
    
    private static Map<String, Object> findMerchantItemByName(String name) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object itemsObj = plugin.getConfig().get("visitors.merchant.items");
        if (!(itemsObj instanceof List)) return null;
        
        for (Object itemObj : (List<?>) itemsObj) {
            if (itemObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemData = (Map<String, Object>) itemObj;
                String itemName = (String) itemData.getOrDefault("name", "");
                if (itemName.equals(name)) {
                    return itemData;
                }
            }
        }
        return null;
    }
    
    private static Map<String, Object> findTravelerDealByName(String name) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object dealsObj = plugin.getConfig().get("visitors.traveler.deals");
        if (!(dealsObj instanceof List)) return null;
        
        for (Object dealObj : (List<?>) dealsObj) {
            if (dealObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dealData = (Map<String, Object>) dealObj;
                String dealName = (String) dealData.get("name");
                if (dealName != null && dealName.equals(name)) {
                    return dealData;
                }
            }
        }
        return null;
    }
    
    private static Map<String, Object> findFestivalByName(String name) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object festivalsObj = plugin.getConfig().get("visitors.festival.festivals");
        if (!(festivalsObj instanceof List)) return null;
        
        for (Object festivalObj : (List<?>) festivalsObj) {
            if (festivalObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> festivalData = (Map<String, Object>) festivalObj;
                String festivalName = (String) festivalData.get("name");
                if (festivalName != null && festivalName.equals(name)) {
                    return festivalData;
                }
            }
        }
        return null;
    }
    
    // ============== 节日增益 ==============
    
    private static void activateFestivalBoost(String festivalName, double boost, long durationHours) {
        long expiresAt = System.currentTimeMillis() + durationHours * 60 * 60 * 1000;
        activeFestivalBoosts.put(festivalName, expiresAt);
    }
    
    /**
     * 获取当前生效的节日产出倍率
     * @return 所有激活倍率的乘积，无加成返回 1.0
     */
    public static double getActiveProductionBoost() {
        double totalBoost = 1.0;
        long now = System.currentTimeMillis();
        activeFestivalBoosts.entrySet().removeIf(entry -> entry.getValue() < now);
        
        for (Map.Entry<String, Long> entry : activeFestivalBoosts.entrySet()) {
            Map<String, Object> festivalData = findFestivalByName(entry.getKey());
            if (festivalData == null) continue;
            
            Map<String, Object> effect = (Map<String, Object>) festivalData.get("effect");
            if (effect == null) continue;
            
            Object boostObj = effect.get("production_boost");
            double boost = 1.0;
            if (boostObj instanceof Number) {
                boost = ((Number) boostObj).doubleValue();
            } else if (boostObj != null) {
                try {
                    boost = Double.parseDouble(String.valueOf(boostObj));
                } catch (NumberFormatException ignored) {
                }
            }
            totalBoost *= boost;
        }
        
        return totalBoost;
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
    
    // 使用 PersistentDataContainer 存储标签
    private static void setItemTag(ItemStack item, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(TAG_KEY, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }
    
    private static String getItemTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(TAG_KEY, PersistentDataType.STRING) 
            ? container.get(TAG_KEY, PersistentDataType.STRING) 
            : null;
    }
    
    private static List<CostEntry> parsePrices(List<Map<String, Object>> prices) {
        List<CostEntry> costs = new ArrayList<>();
        if (prices == null) return costs;
        
        for (Map<String, Object> priceData : prices) {
            String type = (String) priceData.get("type");
            Object amountObj = priceData.get("amount");
            double amount = 0;
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).doubleValue();
            } else if (amountObj != null) {
                try {
                    amount = Double.parseDouble(String.valueOf(amountObj));
                } catch (NumberFormatException ignored) {
                }
            }
            
            if ("itemsadder".equalsIgnoreCase(type)) {
                String item = priceData.get("item") != null ? String.valueOf(priceData.get("item")) : "";
                costs.add(new CostEntry(type, amount, item));
            } else {
                costs.add(new CostEntry(type, amount));
            }
        }
        
        return costs;
    }
    
    private static void giveItemToPlayer(Player player, String itemType, int amount) {
        if (itemType.startsWith("villagerpro:")) {
            // 自定义物品，通过 ItemsAdder 发放（如果可用）
            giveCustomItem(player, itemType, amount);
            return;
        }
        
        Material material = Material.getMaterial(itemType);
        if (material == null) {
            player.sendMessage("§c无法给予未知物品: " + itemType);
            return;
        }
        
        ItemStack itemStack = new ItemStack(material, amount);
        player.getInventory().addItem(itemStack);
    }
    
    private static void giveCustomItem(Player player, String itemNamespace, int amount) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            player.sendMessage("§c自定义物品插件未加载，无法获得 " + itemNamespace);
            return;
        }
        try {
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Object customItem = itemsAdderAPI.getMethod("getCustomItem", String.class).invoke(null, itemNamespace);
            if (customItem == null) {
                player.sendMessage("§c找不到自定义物品: " + itemNamespace);
                return;
            }
            ItemStack itemStack = (ItemStack) customItem.getClass().getMethod("getItemStack").invoke(customItem);
            itemStack.setAmount(amount);
            player.getInventory().addItem(itemStack);
        } catch (Exception e) {
            player.sendMessage("§c发放自定义物品失败: " + itemNamespace);
            VillagerPro.getInstance().getLogger().warning("发放自定义物品失败: " + e.getMessage());
        }
    }
    
    private static boolean hasItemInInventory(Player player, String itemType, int amount) {
        Material material = Material.getMaterial(itemType);
        if (material == null) return false;
        
        int found = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                found += item.getAmount();
                if (found >= amount) return true;
            }
        }
        return false;
    }
    
    private static void removeItemFromInventory(Player player, String itemType, int amount) {
        Material material = Material.getMaterial(itemType);
        if (material == null) return;
        
        int toRemove = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();
                if (itemAmount <= toRemove) {
                    player.getInventory().remove(item);
                    toRemove -= itemAmount;
                } else {
                    item.setAmount(itemAmount - toRemove);
                    toRemove = 0;
                }
                if (toRemove == 0) break;
            }
        }
    }
    
    // 旅行者委托状态：playerUUID -> dealName -> 是否已接受
    private static final Map<String, Map<String, Boolean>> acceptedDeals = new HashMap<>();
    
    private static boolean isDealAccepted(Player player, String dealName) {
        return acceptedDeals.getOrDefault(player.getUniqueId().toString(), new HashMap<>()).getOrDefault(dealName, false);
    }
    
    private static void acceptDeal(Player player, String dealName) {
        acceptedDeals.computeIfAbsent(player.getUniqueId().toString(), k -> new HashMap<>()).put(dealName, true);
    }
    
    private static void clearDealAccepted(Player player, String dealName) {
        acceptedDeals.computeIfAbsent(player.getUniqueId().toString(), k -> new HashMap<>()).put(dealName, false);
    }
    
    // 节日奖励领取：playerUUID -> festivalName -> 是否已领取
    private static final Map<String, Map<String, Boolean>> claimedFestivals = new HashMap<>();
    
    private static boolean isFestivalClaimed(Player player, String festivalName) {
        return claimedFestivals.getOrDefault(player.getUniqueId().toString(), new HashMap<>()).getOrDefault(festivalName, false);
    }
    
    private static void claimFestivalReward(Player player, String festivalName) {
        claimedFestivals.computeIfAbsent(player.getUniqueId().toString(), k -> new HashMap<>()).put(festivalName, true);
    }
    
    /**
     * 移除颜色代码
     */
    @SuppressWarnings("unused")
    private static String stripColor(String text) {
        return text == null ? "" : ChatColor.stripColor(text);
    }
}
