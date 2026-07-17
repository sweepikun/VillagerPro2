package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.managers.PolicyManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VisitorManager;
import cn.popcraft.villagerpro.managers.WarehouseManager;
import cn.popcraft.villagerpro.managers.WarehouseRuleManager;
import cn.popcraft.villagerpro.models.Village;
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
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访客GUI管理器
 * 负责处理访客系统的所有GUI界面
 */
public class VisitorGUIManager {
    
    private static final String GUI_PREFIX = "§f[VP] ";
    private static final NamespacedKey TAG_KEY = new NamespacedKey(VillagerPro.getInstance(), "visitor_tag");
    private static final int[] CONTENT_SLOTS = {9, 10, 11, 12, 14, 15, 16, 17};
    
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
        addMerchantItems(gui, visitor, player);
        
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
            "§7每种节日都有独立奖励与效果",
            "§7本周期内每项奖励只能领取一次"
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
    private static void addMerchantItems(Inventory gui, VisitorData visitor, Player player) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object itemsObj = plugin.getConfig().get("visitors.merchant.items");
        if (!(itemsObj instanceof java.util.List)) return;
        
        @SuppressWarnings("unchecked")
        java.util.List<Object> itemsList = (java.util.List<Object>) itemsObj;
        
        int slotIndex = 0;
        for (Object itemObj : itemsList) {
            if (slotIndex >= CONTENT_SLOTS.length) break;
            
            if (!(itemObj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> itemData = (Map<String, Object>) itemObj;
            
            try {
                String itemType = (String) itemData.get("item");
                String name = (String) itemData.getOrDefault("name", itemType);
                String description = (String) itemData.getOrDefault("description", "");
                String rarity = (String) itemData.getOrDefault("rarity", "common");
                int productAmount = Math.max(1, readInt(itemData.get("amount"), 1));
                String productId = merchantProductId(itemData);
                int stockLimit = Math.max(1, readInt(itemData.get("stock"), 1));
                int perPlayerLimit = Math.max(1, readInt(itemData.get("per_player_limit"), 1));
                MerchantStock stock = loadMerchantStock(visitor.getId(), productId,
                        player.getUniqueId().toString(), stockLimit);
                int remainingStock = Math.max(0, stockLimit - stock.totalPurchased());
                int remainingPersonal = Math.max(0, perPlayerLimit - stock.playerPurchased());
                
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
                    double amount = readDouble(priceData.get("amount"), 0);
                    
                    switch (priceType.toLowerCase()) {
                        case "vault":
                            priceText += "§6" + amount + "金币";
                            break;
                        case "playerpoints":
                            priceText += "§b" + (int)amount + "点券";
                            break;
                        case "itemsadder":
                            priceText += "§5" + (int) amount + " x "
                                    + priceData.getOrDefault("item", "自定义物品");
                            break;
                        case "item":
                            priceText += "§f" + (int) amount + " x "
                                    + priceData.getOrDefault("item", "原版物品");
                            break;
                    }
                    if (i < prices.size() - 1) priceText += " §7+ ";
                }
                
                itemMeta.setLore(java.util.Arrays.asList(
                    priceText,
                    "§7数量: §f" + productAmount,
                    "§7本批库存: §f" + remainingStock + "/" + stockLimit,
                    "§7个人限购: §f" + stock.playerPurchased() + "/" + perPlayerLimit,
                    "",
                    "§7" + description,
                    "",
                    remainingStock <= 0 ? "§c本批商品已售罄"
                            : (remainingPersonal <= 0 ? "§c你已达到限购数量" : "§a左键购买")
                ));
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于购买处理
                setItemTag(item, "merchant:" + visitor.getId() + ":" + productId);
                
                gui.setItem(CONTENT_SLOTS[slotIndex++], item);
                
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
        List<?> dealsList = dealsObj instanceof List<?> list ? list : List.of();
        Map<String, TravelerQuest> storedQuests = loadTravelerQuests(player, visitor.getId());
        Set<String> renderedNames = new HashSet<>();
        
        int slotIndex = 0;
        for (Object dealObj : dealsList) {
            if (slotIndex >= CONTENT_SLOTS.length) break;
            
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
                TravelerQuest stored = storedQuests.get(name);
                if (stored != null) {
                    itemType = stored.itemType();
                    amount = stored.requiredAmount();
                    rewardType = stored.rewardItem();
                    rewardAmount = stored.rewardAmount();
                    rewardName = rewardType;
                }
                gui.setItem(CONTENT_SLOTS[slotIndex], createTravelerQuestItem(player, visitor.getId(), name,
                        description, itemType, amount, rewardType, rewardAmount, rewardName,
                        stored == null ? null : stored.status()));
                renderedNames.add(name);
                slotIndex++;
                
            } catch (Exception e) {
                VillagerPro.getInstance().getLogger().warning("处理旅行者委托时出错: " + e.getMessage());
            }
        }

        for (TravelerQuest stored : storedQuests.values()) {
            if (slotIndex >= CONTENT_SLOTS.length) break;
            if (renderedNames.contains(stored.questName()) || !"accepted".equals(stored.status())) continue;
            gui.setItem(CONTENT_SLOTS[slotIndex], createTravelerQuestItem(player, visitor.getId(), stored.questName(),
                    "该委托配置已变更，仍按接取时的合同交付", stored.itemType(),
                    stored.requiredAmount(), stored.rewardItem(), stored.rewardAmount(),
                    stored.rewardItem(), stored.status()));
            slotIndex++;
        }
    }

    private static ItemStack createTravelerQuestItem(Player player, int visitorId, String name,
                                                      String description, String itemType, int amount,
                                                      String rewardType, int rewardAmount,
                                                      String rewardName, String status) {
        Material material = Material.matchMaterial(itemType);
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName("§e" + name);
        boolean accepted = "accepted".equals(status);
        boolean finished = "completed".equals(status);
        Village village = VillageManager.getVillage(player.getUniqueId());
        boolean ready = accepted && village != null
                && WarehouseManager.getExtractableAmount(village.getId(), itemType) >= amount;
        String statusPrefix = finished ? "§a✅ " : (accepted ? "§e⏳ " : "§7");
        itemMeta.setLore(java.util.Arrays.asList(
                statusPrefix + "§7描述: " + description,
                "§7需要: §f" + amount + " x " + itemType,
                "§7奖励: §a" + rewardAmount + " x " + rewardName,
                "",
                finished ? "§7该委托已完成" : (ready ? "§a左键交付委托"
                        : (accepted ? "§7仓库可用库存不足" : "§a左键接受委托"))
        ));
        item.setItemMeta(itemMeta);
        setItemTag(item, "traveler:" + visitorId + ":" + name);
        return item;
    }
    
    /**
     * 添加节日奖励到GUI
     */
    private static void addFestivalRewards(Inventory gui, VisitorData visitor, Player player) {
        VillagerPro plugin = VillagerPro.getInstance();
        List<Map<String, Object>> festivalsList = loadFestivals();
        
        int slotIndex = 0;
        for (Object festivalObj : festivalsList) {
            if (slotIndex >= CONTENT_SLOTS.length) break;
            
            if (!(festivalObj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> festivalData = (Map<String, Object>) festivalObj;
            
            try {
                String name = (String) festivalData.get("name");
                String description = (String) festivalData.get("description");
                Map<String, Object> effect = (Map<String, Object>) festivalData.get("effect");
                double productionBoost = readDouble(effect.get("production_boost"), 1.0);
                
                // 检查玩家是否已领取
                boolean claimed = isFestivalClaimed(player, name);
                String statusPrefix = claimed ? "§a✅ " : "§7";
                
                Material material = Material.FIREWORK_STAR;
                ItemStack item = new ItemStack(material);
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName("§d" + name);
                
                List<String> lore = new ArrayList<>();
                lore.add(statusPrefix + "§7" + description);
                if (productionBoost > 1.0) {
                    lore.add("§7全服产出: §e" + String.format("%.2fx", productionBoost));
                }
                if (effect.containsKey("item")) {
                    lore.add("§7个人奖励: §a" + readInt(effect.get("amount"), 1)
                            + " x " + effect.get("item"));
                }
                lore.add("");
                lore.add(claimed ? "§a已领取！" : "§a左键领取");
                itemMeta.setLore(lore);
                item.setItemMeta(itemMeta);
                
                // 设置额外数据用于奖励领取
                setItemTag(item, "festival:" + name);
                
                gui.setItem(CONTENT_SLOTS[slotIndex++], item);
                
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
            String data = tag.substring("merchant:".length());
            int separator = data.indexOf(':');
            if (separator <= 0) return false;
            int visitorId;
            try {
                visitorId = Integer.parseInt(data.substring(0, separator));
            } catch (NumberFormatException e) {
                return false;
            }
            handleMerchantPurchase(player, visitorId, data.substring(separator + 1));
            return true;
        }
        
        if (tag.startsWith("traveler:")) {
            String data = tag.substring("traveler:".length());
            int separator = data.indexOf(':');
            if (separator <= 0) return false;
            int visitorId;
            try {
                visitorId = Integer.parseInt(data.substring(0, separator));
            } catch (NumberFormatException e) {
                return false;
            }
            handleTravelerDeal(player, visitorId, data.substring(separator + 1));
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
    private static void handleMerchantPurchase(Player player, int visitorId, String productId) {
        VisitorData visitor = VisitorManager.getInstance().getVisitor(visitorId);
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (visitor == null || village == null || visitor.getVillageId() != village.getId()) {
            player.sendMessage("§c这位商人已经离开，或并不属于你的村庄");
            return;
        }
        Map<String, Object> itemData = findMerchantItemById(productId);
        if (itemData == null) {
            player.sendMessage("§c找不到该商品");
            return;
        }
        boolean releaseReservation = false;
        boolean refundCosts = false;
        List<CostEntry> chargedCosts = List.of();
        try {
            List<Map<String, Object>> prices = (List<Map<String, Object>>) itemData.get("price");
            String itemType = (String) itemData.get("item");
            String name = String.valueOf(itemData.getOrDefault("name", itemType));
            int productAmount = Math.max(1, readInt(itemData.get("amount"), 1));
            int stockLimit = Math.max(1, readInt(itemData.get("stock"), 1));
            int perPlayerLimit = Math.max(1, readInt(itemData.get("per_player_limit"), 1));
            
            List<CostEntry> costs = parsePrices(prices);
            if (costs.isEmpty()) {
                player.sendMessage("§c该商品没有有效价格，已阻止免费购买");
                return;
            }
            if (!CostHandler.canAfford(player, costs)) {
                player.sendMessage("§c资源不足，无法购买 " + name);
                return;
            }
            if (!reserveMerchantProduct(visitorId, productId, player.getUniqueId().toString(),
                    stockLimit, perPlayerLimit)) {
                player.sendMessage("§c商品已经售罄，或你已达到本批限购数量");
                showMerchantGUI(player, visitor);
                return;
            }
            releaseReservation = true;
            
            if (!CostHandler.deduct(player, costs)) {
                releaseMerchantProduct(visitorId, productId, player.getUniqueId().toString());
                releaseReservation = false;
                player.sendMessage("§c扣除资源失败");
                return;
            }
            chargedCosts = costs;
            refundCosts = true;
            
            if (!giveItemToPlayer(player, itemType, productAmount)) {
                boolean refunded = CostHandler.refund(player, costs);
                refundCosts = false;
                releaseMerchantProduct(visitorId, productId, player.getUniqueId().toString());
                releaseReservation = false;
                player.sendMessage(refunded ? "§c商品发放失败，费用已退还"
                        : "§c商品发放失败且费用未完整退还，请联系管理员");
                return;
            }
            refundCosts = false;
            releaseReservation = false;
            player.sendMessage("§a成功购买 " + productAmount + " x " + name + "！");
            
            // 记录交易
            double vaultPrice = costs.stream()
                    .filter(cost -> "vault".equalsIgnoreCase(cost.getType()))
                    .mapToDouble(CostEntry::getAmount).sum();
            if (VisitorManager.getInstance().createDeal(visitor, "purchase", itemType,
                    productAmount, vaultPrice, player.getName()) == null) {
                VillagerPro.getInstance().getLogger().warning(
                        "商人购买已完成但成交日志写入失败: visitor=" + visitorId
                                + ", player=" + player.getName() + ", item=" + itemType);
            }
            showMerchantGUI(player, visitor);
            
        } catch (Exception e) {
            if (refundCosts) {
                CostHandler.refund(player, chargedCosts);
            }
            if (releaseReservation) {
                releaseMerchantProduct(visitorId, productId, player.getUniqueId().toString());
            }
            player.sendMessage("§c购买失败，请重试");
            VillagerPro.getInstance().getLogger().warning("购买处理失败: " + e.getMessage());
        }
    }

    private static MerchantStock loadMerchantStock(int visitorId, String productId,
                                                   String playerUuid, int stockLimit) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(purchase_count), 0), "
                             + "COALESCE(MAX(CASE WHEN player_uuid = ? THEN purchase_count ELSE 0 END), 0) "
                             + "FROM visitor_shop_sales WHERE visitor_id = ? AND product_id = ?")) {
            statement.setString(1, playerUuid);
            statement.setInt(2, visitorId);
            statement.setString(3, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new MerchantStock(resultSet.getInt(1), resultSet.getInt(2));
                }
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取访客商品库存失败: " + e.getMessage());
        }
        return new MerchantStock(stockLimit, stockLimit);
    }

    private static boolean reserveMerchantProduct(int visitorId, String productId,
                                                  String playerUuid, int stockLimit,
                                                  int perPlayerLimit) {
        try (Connection connection = DatabaseManager.getConnection()) {
            return OperationTransactions.reserveVisitorProduct(connection,
                    DatabaseManager.getDialect(), visitorId, productId, playerUuid,
                    stockLimit, perPlayerLimit);
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("占用访客商品库存失败: " + e.getMessage());
            return false;
        }
    }

    private static void releaseMerchantProduct(int visitorId, String productId, String playerUuid) {
        try (Connection connection = DatabaseManager.getConnection()) {
            if (!OperationTransactions.releaseVisitorProduct(
                    connection, visitorId, productId, playerUuid)) {
                VillagerPro.getInstance().getLogger().severe(
                        "无法释放失败购买占用的访客商品库存: visitor=" + visitorId
                                + ", product=" + productId + ", player=" + playerUuid);
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().severe(
                    "释放访客商品库存失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理旅行者委托
     */
    private static void handleTravelerDeal(Player player, int visitorId, String name) {
        TravelerQuest storedQuest = loadTravelerQuest(player, visitorId, name);
        Map<String, Object> dealData = findTravelerDealByName(name);
        if (storedQuest == null && dealData == null) {
            player.sendMessage("§c找不到该委托");
            return;
        }
        
        try {
            Village village = VillageManager.getVillage(player.getUniqueId());
            if (village == null) {
                player.sendMessage("§c请先创建村庄");
                return;
            }
            String itemType = storedQuest == null
                    ? (String) dealData.get("item") : storedQuest.itemType();
            int amount = storedQuest == null
                    ? (Integer) dealData.get("amount") : storedQuest.requiredAmount();
            String displayName = dealData == null ? name : (String) dealData.get("name");
            
            if (storedQuest != null && "completed".equals(storedQuest.status())) {
                player.sendMessage("§c这个委托已经完成过了！");
            } else if (storedQuest != null && "accepted".equals(storedQuest.status())) {
                int available = WarehouseManager.getExtractableAmount(village.getId(), itemType);
                if (available < amount) {
                    player.sendMessage("§c仓库可用 " + itemType + " 不足，需要 " + amount
                            + "，当前 " + available + "；保留量不会被交付");
                    return;
                }
                String rewardType = storedQuest.rewardItem();
                int rewardAmount = storedQuest.rewardAmount();
                if (Material.getMaterial(rewardType) == null) {
                    player.sendMessage("§c委托奖励不是有效的原版物品，无法安全入库");
                    return;
                }
                int reserve = WarehouseRuleManager.getItemRule(
                        village.getId(), itemType).getReserveAmount();
                boolean completed;
                try (Connection connection = DatabaseManager.getConnection()) {
                    completed = OperationTransactions.completeVisitorQuest(connection,
                            DatabaseManager.getDialect(), player.getUniqueId().toString(),
                            visitorId, name, village.getId(), itemType, amount,
                            rewardType, rewardAmount, reserve,
                            PolicyManager.getFrozenStockFraction(village.getId()),
                            village.getWarehouseCapacity());
                }
                if (!completed) {
                    player.sendMessage("§c委托状态、可用库存或仓库空间已经变化，本次没有扣料");
                    return;
                }
                player.sendMessage("§a完成委托: " + displayName + "！");
                player.sendMessage("§a奖励已入库: " + rewardAmount + " x " + rewardType);
            } else {
                // 接受委托
                if (dealData == null) {
                    player.sendMessage("§c该委托已从配置移除，无法重新接受");
                    return;
                }
                if (acceptDeal(player, visitorId, name, itemType, amount, dealData)) {
                    player.sendMessage("§a成功接受委托: " + displayName);
                    player.sendMessage("§7将 " + amount + " 个 " + itemType
                            + " 存入村庄仓库后再次点击交付");
                } else {
                    player.sendMessage("§c接受委托失败，请稍后重试");
                }
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
            Village village = VillageManager.getVillage(player.getUniqueId());
            if (village == null) {
                player.sendMessage("§c请先创建村庄");
                return;
            }
            Object effectObject = festivalData.get("effect");
            if (!(effectObject instanceof Map<?, ?>)) {
                player.sendMessage("§c该节日没有有效的奖励配置");
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> effect = (Map<String, Object>) effectObject;
            if (isFestivalClaimed(player, name)) {
                player.sendMessage("§c这个奖励已经领取过了！");
                return;
            }
            
            String rewardItem = null;
            int rewardAmount = 0;
            if (effect.containsKey("item")) {
                rewardItem = String.valueOf(effect.get("item"));
                rewardAmount = Math.max(1, readInt(effect.get("amount"), 1));
                if (Material.getMaterial(rewardItem) == null) {
                    player.sendMessage("§c节日奖励不是有效的原版物品，无法安全入库");
                    return;
                }
            }
            double boost = readDouble(effect.get("production_boost"), 1.0);
            long durationHours = Math.max(1,
                    readInt(festivalData.get("duration_hours"), 48));
            String claimKey = festivalClaimKey(name);
            OperationTransactions.FestivalClaimResult claim = claimFestivalReward(
                    player, claimKey, village, rewardItem, rewardAmount,
                    boost > 1.0 ? durationHours : 0);
            if (!claim.claimed()) {
                player.sendMessage("§c奖励已领取、仓库空间不足或领取状态已经变化");
                return;
            }
            if (claim.boostExpiry() > 0) {
                activeFestivalBoosts.put(claimKey, claim.boostExpiry());
            }
            
            player.sendMessage("§a成功领取节日奖励: " + name + "！");
            if (rewardItem != null) {
                player.sendMessage("§a个人奖励已入库: " + rewardAmount + " x " + rewardItem);
            }
            if (boost > 1.0) {
                long remainingMs = claim.boostExpiry() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    long remainingMinutes = Math.max(1, (remainingMs + 59_999) / 60_000);
                    player.sendMessage("§7节日产出加成 " + boost + "x，全服剩余 "
                            + remainingMinutes + " 分钟；重复领取不会延长时间");
                } else {
                    player.sendMessage("§7本周期的全服产出加成已经结束，不会因后续领取重新启动");
                }
            }
            
        } catch (Exception e) {
            player.sendMessage("§c领取失败，请重试");
            VillagerPro.getInstance().getLogger().warning("节日奖励领取失败: " + e.getMessage());
        }
    }
    
    // ============== 配置查找 ==============
    
    private static Map<String, Object> findMerchantItemById(String productId) {
        VillagerPro plugin = VillagerPro.getInstance();
        Object itemsObj = plugin.getConfig().get("visitors.merchant.items");
        if (!(itemsObj instanceof List)) return null;
        
        for (Object itemObj : (List<?>) itemsObj) {
            if (itemObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemData = (Map<String, Object>) itemObj;
                if (merchantProductId(itemData).equals(productId)) {
                    return itemData;
                }
            }
        }
        return null;
    }

    private static String merchantProductId(Map<String, Object> itemData) {
        Object configured = itemData.get("id");
        if (configured != null && !String.valueOf(configured).isBlank()) {
            return String.valueOf(configured);
        }
        return String.valueOf(itemData.getOrDefault("item", "unknown"))
                .toLowerCase(java.util.Locale.ROOT);
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
        for (Map<String, Object> festivalData : loadFestivals()) {
            String festivalName = (String) festivalData.get("name");
            if (festivalName != null && festivalName.equals(name)) {
                return festivalData;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> loadFestivals() {
        List<Map<String, Object>> festivals = new ArrayList<>();
        ConfigurationSection root = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("visitors.festival.festivals");
        if (root == null) return festivals;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Map<String, Object> data = new LinkedHashMap<>(section.getValues(false));
            ConfigurationSection effect = section.getConfigurationSection("effect");
            if (effect != null) {
                data.put("effect", new LinkedHashMap<>(effect.getValues(false)));
            }
            festivals.add(data);
        }
        return festivals;
    }
    
    // ============== 节日增益 ==============
    
    /**
     * 获取当前生效的节日产出倍率
     * @return 所有激活倍率的乘积，无加成返回 1.0
     */
    public static double getActiveProductionBoost() {
        double totalBoost = 1.0;
        long now = System.currentTimeMillis();
        activeFestivalBoosts.entrySet().removeIf(entry -> entry.getValue() < now);

        try (Connection connection = DatabaseManager.getConnection()) {
            cleanupExpiredFestivalBoosts(connection, now);
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT festival_name, expires_at FROM festival_boosts WHERE expires_at >= ?")) {
                query.setLong(1, now);
                ResultSet resultSet = query.executeQuery();
                while (resultSet.next()) {
                    activeFestivalBoosts.put(resultSet.getString("festival_name"),
                            resultSet.getLong("expires_at"));
                }
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("加载节日增益失败: " + e.getMessage());
        }
        
        Set<String> appliedFestivals = new HashSet<>();
        for (Map.Entry<String, Long> entry : activeFestivalBoosts.entrySet()) {
            String festivalName = festivalNameFromActivationKey(entry.getKey());
            if (!appliedFestivals.add(festivalName)) continue;
            Map<String, Object> festivalData = findFestivalByName(festivalName);
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

    private static void cleanupExpiredFestivalBoosts(Connection connection, long now)
            throws SQLException {
        List<String> obsoleteKeys = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT festival_name FROM festival_boosts WHERE expires_at < ?")) {
            query.setLong(1, now);
            try (ResultSet resultSet = query.executeQuery()) {
                while (resultSet.next()) {
                    String activationKey = resultSet.getString(1);
                    String festivalName = festivalNameFromActivationKey(activationKey);
                    if (!activationKey.equals(festivalClaimKey(festivalName))) {
                        obsoleteKeys.add(activationKey);
                    }
                }
            }
        }
        if (obsoleteKeys.isEmpty()) return;
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM festival_boosts WHERE festival_name = ? AND expires_at < ?")) {
            for (String activationKey : obsoleteKeys) {
                delete.setString(1, activationKey);
                delete.setLong(2, now);
                delete.addBatch();
            }
            delete.executeBatch();
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
            
            if ("itemsadder".equalsIgnoreCase(type) || "item".equalsIgnoreCase(type)) {
                String item = priceData.get("item") != null ? String.valueOf(priceData.get("item")) : "";
                costs.add(new CostEntry(type, amount, item));
            } else {
                costs.add(new CostEntry(type, amount));
            }
        }
        
        return costs;
    }

    private static double readDouble(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int readInt(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
    
    private static boolean giveItemToPlayer(Player player, String itemType, int amount) {
        if (itemType.startsWith("villagerpro:")) {
            // 自定义物品，通过 ItemsAdder 发放（如果可用）
            return giveCustomItem(player, itemType, amount);
        }
        
        Material material = Material.getMaterial(itemType);
        if (material == null) {
            player.sendMessage("§c无法给予未知物品: " + itemType);
            return false;
        }
        
        ItemStack itemStack = new ItemStack(material, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        return true;
    }
    
    private static boolean giveCustomItem(Player player, String itemNamespace, int amount) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            player.sendMessage("§c自定义物品插件未加载，无法获得 " + itemNamespace);
            return false;
        }
        try {
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Object customItem = itemsAdderAPI.getMethod("getCustomItem", String.class).invoke(null, itemNamespace);
            if (customItem == null) {
                player.sendMessage("§c找不到自定义物品: " + itemNamespace);
                return false;
            }
            ItemStack itemStack = (ItemStack) customItem.getClass().getMethod("getItemStack").invoke(customItem);
            itemStack.setAmount(amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            return true;
        } catch (Exception e) {
            player.sendMessage("§c发放自定义物品失败: " + itemNamespace);
            VillagerPro.getInstance().getLogger().warning("发放自定义物品失败: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean isDealAccepted(Player player, int visitorId, String dealName) {
        return hasDealStatus(player, visitorId, dealName, "accepted");
    }

    private static boolean isDealCompleted(Player player, int visitorId, String dealName) {
        return hasDealStatus(player, visitorId, dealName, "completed");
    }

    private static TravelerQuest loadTravelerQuest(Player player, int visitorId, String dealName) {
        return loadTravelerQuests(player, visitorId).get(dealName);
    }

    private static Map<String, TravelerQuest> loadTravelerQuests(Player player, int visitorId) {
        Map<String, TravelerQuest> quests = new LinkedHashMap<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT quest_name, item_type, required_amount, reward_item, reward_amount, status "
                             + "FROM visitor_quests WHERE player_uuid = ? AND visitor_id = ? "
                             + "ORDER BY accepted_at")) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setInt(2, visitorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TravelerQuest quest = new TravelerQuest(resultSet.getString("quest_name"),
                            resultSet.getString("item_type"), resultSet.getInt("required_amount"),
                            resultSet.getString("reward_item"), resultSet.getInt("reward_amount"),
                            resultSet.getString("status"));
                    quests.put(quest.questName(), quest);
                }
            }
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取访客委托合同失败: " + e.getMessage());
        }
        return quests;
    }

    private static boolean hasDealStatus(Player player, int visitorId, String dealName, String status) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM visitor_quests WHERE player_uuid = ? AND visitor_id = ? " +
                             "AND quest_name = ? AND status = ? LIMIT 1")) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setInt(2, visitorId);
            statement.setString(3, dealName);
            statement.setString(4, status);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取访客委托状态失败: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean acceptDeal(Player player, int visitorId, String dealName, String itemType,
                                      int amount, Map<String, Object> dealData) {
        Map<String, Object> reward = (Map<String, Object>) dealData.get("reward");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DatabaseManager.insertIgnore(
                     "INSERT INTO visitor_quests " +
                             "(player_uuid, visitor_id, quest_name, item_type, required_amount, " +
                             "reward_type, reward_item, reward_amount, status) " +
                             "VALUES (?, ?, ?, ?, ?, 'item', ?, ?, 'accepted')"))) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setInt(2, visitorId);
            statement.setString(3, dealName);
            statement.setString(4, itemType);
            statement.setInt(5, amount);
            statement.setString(6, String.valueOf(reward.get("item")));
            statement.setInt(7, ((Number) reward.get("amount")).intValue());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("接受访客委托失败: " + e.getMessage());
            return false;
        }
    }

    private static boolean isFestivalClaimed(Player player, String festivalName) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM festival_claims WHERE player_uuid = ? AND festival_name = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, festivalClaimKey(festivalName));
            return statement.executeQuery().next();
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("读取节日领取状态失败: " + e.getMessage());
            return true;
        }
    }

    private static OperationTransactions.FestivalClaimResult claimFestivalReward(
            Player player, String claimKey, Village village, String rewardItem,
            int rewardAmount, long boostDurationHours) {
        long now = System.currentTimeMillis();
        long durationMillis = boostDurationHours > Long.MAX_VALUE / (60L * 60L * 1000L)
                ? Long.MAX_VALUE : Math.max(0L, boostDurationHours) * 60L * 60L * 1000L;
        long boostExpiry = durationMillis > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + durationMillis;
        try (Connection connection = DatabaseManager.getConnection()) {
            return OperationTransactions.claimFestivalReward(connection,
                    DatabaseManager.getDialect(), player.getUniqueId().toString(),
                    claimKey, village.getId(), rewardItem, rewardAmount,
                    village.getWarehouseCapacity(),
                    boostDurationHours > 0 ? claimKey : null,
                    boostDurationHours > 0 ? boostExpiry : 0);
        } catch (SQLException e) {
            VillagerPro.getInstance().getLogger().warning("保存节日领取状态失败: " + e.getMessage());
            return new OperationTransactions.FestivalClaimResult(false, 0);
        }
    }

    private static String festivalClaimKey(String festivalName) {
        long cycle = Bukkit.getWorlds().isEmpty()
                ? System.currentTimeMillis() / (7L * 24 * 60 * 60 * 1000)
                : Bukkit.getWorlds().get(0).getFullTime() / 168000L;
        return festivalName + "#" + cycle;
    }

    private static String festivalNameFromActivationKey(String activationKey) {
        int separator = activationKey.lastIndexOf('#');
        return separator > 0 ? activationKey.substring(0, separator) : activationKey;
    }

    private record TravelerQuest(String questName, String itemType, int requiredAmount,
                                 String rewardItem, int rewardAmount, String status) {
    }

    private record MerchantStock(int totalPurchased, int playerPurchased) {
    }
    
    /**
     * 移除颜色代码
     */
    @SuppressWarnings("unused")
    private static String stripColor(String text) {
        return text == null ? "" : ChatColor.stripColor(text);
    }
}
