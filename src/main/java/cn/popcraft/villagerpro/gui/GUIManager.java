package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.VillagerUpgradeManager;
import cn.popcraft.villagerpro.managers.WarehouseManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

public class GUIManager {
    private static final String GUI_PREFIX = VillagerPro.getInstance().getConfig().getString("gui.title_prefix", "§f[VP] ");
    
    /**
     * 打开村庄主界面
     * @param player 玩家
     */
    public static void openVillageGUI(Player player) {
        // 获取玩家的村庄
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            return;
        }
        
        // 创建GUI
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "村庄信息");
        
        // 填充背景玻璃板
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, background);
            }
        }
        
        // 村庄信息展示
        ItemStack villageInfo = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = villageInfo.getItemMeta();
        infoMeta.setDisplayName("§6" + village.getName());
        List<String> infoLore = new java.util.ArrayList<>();
        infoLore.add("§7等级: §e" + village.getLevel());
        infoLore.add("§7繁荣度: §e" + village.getProsperity());
        
        // 获取村民数量
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        infoLore.add("§7村民: §e" + villagers.size() + "/" + village.getVillagerLimit());
        
        // 获取仓库信息
        int warehouseCapacity = village.getWarehouseCapacity();
        int currentStorage = WarehouseManager.getCurrentStorage(village.getId());
        infoLore.add("§7仓库: §e" + currentStorage + "/" + warehouseCapacity);
        
        infoMeta.setLore(infoLore);
        villageInfo.setItemMeta(infoMeta);
        gui.setItem(13, villageInfo);
        
        // 升级村庄按钮
        ItemStack upgradeButton = new ItemStack(Material.ANVIL);
        ItemMeta upgradeMeta = upgradeButton.getItemMeta();
        upgradeMeta.setDisplayName("§a升级村庄");
        upgradeButton.setItemMeta(upgradeMeta);
        gui.setItem(10, upgradeButton);
        
        // 村民列表按钮
        ItemStack villagerListButton = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta villagerListMeta = villagerListButton.getItemMeta();
        villagerListMeta.setDisplayName("§b村民列表");
        villagerListButton.setItemMeta(villagerListMeta);
        gui.setItem(11, villagerListButton);
        
        // 仓库按钮
        ItemStack warehouseButton = new ItemStack(Material.CHEST);
        ItemMeta warehouseMeta = warehouseButton.getItemMeta();
        warehouseMeta.setDisplayName("§6村庄仓库");
        warehouseButton.setItemMeta(warehouseMeta);
        gui.setItem(15, warehouseButton);
        
        // 招募村民按钮
        ItemStack recruitButton = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta recruitMeta = recruitButton.getItemMeta();
        recruitMeta.setDisplayName("§d招募村民");
        recruitButton.setItemMeta(recruitMeta);
        gui.setItem(16, recruitButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(26, closeButton);
        
        // 打开GUI
        player.openInventory(gui);
    }
    
    /**
     * 打开村民列表界面
     * @param player 玩家
     */
    public static void openVillagerListGUI(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            return;
        }
        
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        
        // 创建GUI (根据村民数量调整大小，最大54个格子)
        int size = Math.min(((villagers.size() / 9) + 1) * 9, 54);
        size = Math.max(size, 9); // 至少1行
        
        Inventory gui = Bukkit.createInventory(null, size, GUI_PREFIX + "村民列表");
        
        // 显示村民
        for (int i = 0; i < villagers.size() && i < size - 9; i++) {
            VillagerData villager = villagers.get(i);
            
            Material iconMaterial = getProfessionIcon(villager.getProfession());
            ItemStack villagerItem = new ItemStack(iconMaterial);
            ItemMeta meta = villagerItem.getItemMeta();
            meta.setDisplayName("§e" + getProfessionDisplayName(villager.getProfession()) + " §7(ID: " + villager.getId() + ")");
            
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7等级: §e" + villager.getLevel());
            lore.add("§7经验: §e" + villager.getExperience());
            lore.add("§7跟随模式: §e" + villager.getFollowMode());
            lore.add("");
            lore.add("§e左键§7查看详情");
            lore.add("§e右键§7升级");
            meta.setLore(lore);
            
            villagerItem.setItemMeta(meta);
            gui.setItem(i, villagerItem);
        }
        
        // 填充背景玻璃板
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        
        for (int i = Math.max(0, villagers.size()); i < size - 9; i++) {
            gui.setItem(i, background);
        }
        
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(size - 9, backButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(size - 1, closeButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 打开村民详情界面
     * @param player 玩家
     * @param villagerId 村民ID
     */
    public static void openVillagerInfoGUI(Player player, int villagerId) {
        VillagerData villager = null;
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village != null) {
            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            for (VillagerData v : villagers) {
                if (v.getId() == villagerId) {
                    villager = v;
                    break;
                }
            }
        }
        
        if (villager == null) {
            player.sendMessage("§c找不到该村民！");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "村民详情");
        
        // 村民信息
        Material iconMaterial = getProfessionIcon(villager.getProfession());
        ItemStack villagerItem = new ItemStack(iconMaterial);
        ItemMeta meta = villagerItem.getItemMeta();
        meta.setDisplayName("§e" + getProfessionDisplayName(villager.getProfession()));
        
        List<String> lore = new java.util.ArrayList<>();
        lore.add("§7ID: §e" + villager.getId());
        lore.add("§7等级: §e" + villager.getLevel());
        lore.add("§7经验: §e" + villager.getExperience());
        lore.add("§7跟随模式: §e" + villager.getFollowMode());
        lore.add("§7工作范围: §e" + villager.getWorkRange() + "格");
        lore.add("§7基础产出: §e" + villager.getBaseProductionAmount() + "个");
        meta.setLore(lore);
        
        villagerItem.setItemMeta(meta);
        gui.setItem(13, villagerItem);
        
        // 升级按钮
        ItemStack upgradeButton = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta upgradeMeta = upgradeButton.getItemMeta();
        upgradeMeta.setDisplayName("§a升级村民");
        upgradeButton.setItemMeta(upgradeMeta);
        gui.setItem(11, upgradeButton);
        
        // 跟随模式按钮
        ItemStack followButton = new ItemStack(Material.LEAD);
        ItemMeta followMeta = followButton.getItemMeta();
        followMeta.setDisplayName("§b切换跟随模式");
        List<String> followLore = new java.util.ArrayList<>();
        followLore.add("§7当前模式: §e" + villager.getFollowMode());
        followMeta.setLore(followLore);
        followButton.setItemMeta(followMeta);
        gui.setItem(15, followButton);
        
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(18, backButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(26, closeButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 打开村民升级界面
     * @param player 玩家
     * @param villagerId 村民ID
     */
    public static void openVillagerUpgradeGUI(Player player, int villagerId) {
        VillagerData villager = null;
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village != null) {
            List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
            for (VillagerData v : villagers) {
                if (v.getId() == villagerId) {
                    villager = v;
                    break;
                }
            }
        }
        
        if (villager == null) {
            player.sendMessage("§c找不到该村民！");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "村民升级");
        
        // 获取该职业可用的技能
        String profession = villager.getProfession();
        if (VillagerPro.getInstance().getConfig().contains("villager_upgrades." + profession)) {
            Set<String> skills = VillagerPro.getInstance().getConfig()
                    .getConfigurationSection("villager_upgrades." + profession).getKeys(false);
            
            int slot = 10; // 起始槽位
            for (String skillId : skills) {
                if (slot > 16) break; // 最多显示7个技能
                
                Material iconMaterial = Material.getMaterial(
                    VillagerUpgradeManager.getSkillIcon(profession, skillId));
                if (iconMaterial == null) {
                    iconMaterial = Material.STONE;
                }
                
                ItemStack skillItem = new ItemStack(iconMaterial);
                ItemMeta meta = skillItem.getItemMeta();
                meta.setDisplayName("§e" + VillagerUpgradeManager.getSkillDisplayName(profession, skillId));
                
                List<String> lore = new java.util.ArrayList<>();
                lore.add("§7" + VillagerUpgradeManager.getSkillDescription(profession, skillId));
                
                int currentLevel = VillagerUpgradeManager.getVillagerSkillLevel(villagerId, skillId);
                int maxLevel = VillagerUpgradeManager.getSkillMaxLevel(profession, skillId);
                lore.add("§7等级: §e" + currentLevel + "/" + maxLevel);
                
                // 显示成本
                lore.add("");
                lore.add("§6升级成本:");
                List<cn.popcraft.villagerpro.economy.CostEntry> costs = 
                    VillagerUpgradeManager.getUpgradeCosts(profession, skillId);
                lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(costs));
                
                meta.setLore(lore);
                skillItem.setItemMeta(meta);
                gui.setItem(slot, skillItem);
                
                slot++;
                if (slot % 9 == 8) slot += 2; // 跳过下一行的边缘
            }
        }
        
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(18, backButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(26, closeButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 打开仓库界面
     * @param player 玩家
     */
    public static void openWarehouseGUI(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            return;
        }
        
        List<cn.popcraft.villagerpro.models.WarehouseItem> warehouseItems = 
            cn.popcraft.villagerpro.managers.WarehouseManager.getWarehouseItems(village.getId());
        
        // 创建GUI (根据物品数量调整大小，最大54个格子)
        int size = Math.min(((warehouseItems.size() / 9) + 1) * 9, 54);
        size = Math.max(size, 9); // 至少1行
        
        Inventory gui = Bukkit.createInventory(null, size, GUI_PREFIX + "村庄仓库");
        
        // 显示仓库物品
        for (int i = 0; i < warehouseItems.size() && i < size - 9; i++) {
            cn.popcraft.villagerpro.models.WarehouseItem item = warehouseItems.get(i);
            
            Material material = Material.getMaterial(item.getItemType());
            if (material == null) {
                material = Material.STONE;
            }
            
            ItemStack warehouseItem = new ItemStack(material);
            ItemMeta meta = warehouseItem.getItemMeta();
            meta.setDisplayName("§e" + item.getItemType());
            
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7数量: §e" + item.getAmount());
            lore.add("");
            lore.add("§e左键§7提取全部");
            lore.add("§e右键§7提取一组");
            meta.setLore(lore);
            
            warehouseItem.setItemMeta(meta);
            gui.setItem(i, warehouseItem);
        }
        
        // 填充背景玻璃板
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        
        for (int i = Math.max(0, warehouseItems.size()); i < size - 9; i++) {
            gui.setItem(i, background);
        }
        
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(size - 9, backButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(size - 1, closeButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 打开招募界面
     * @param player 玩家
     */
    public static void openRecruitGUI(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            return;
        }
        
        // 检查村民数量是否已达上限
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        if (villagers.size() >= village.getVillagerLimit()) {
            player.sendMessage("§c村民数量已达上限！");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "招募村民");
        
        // 农民
        ItemStack farmer = new ItemStack(Material.WHEAT);
        ItemMeta farmerMeta = farmer.getItemMeta();
        farmerMeta.setDisplayName("§e农民");
        List<String> farmerLore = new java.util.ArrayList<>();
        farmerLore.add("§7种植并收获农作物");
        farmerLore.addAll(getRecruitCostLore());
        farmerMeta.setLore(farmerLore);
        farmer.setItemMeta(farmerMeta);
        gui.setItem(10, farmer);
        
        // 渔夫
        ItemStack fisherman = new ItemStack(Material.COD);
        ItemMeta fishermanMeta = fisherman.getItemMeta();
        fishermanMeta.setDisplayName("§e渔夫");
        List<String> fishermanLore = new java.util.ArrayList<>();
        fishermanLore.add("§7钓鱼获取各种鱼类");
        fishermanLore.addAll(getRecruitCostLore());
        fishermanMeta.setLore(fishermanLore);
        fisherman.setItemMeta(fishermanMeta);
        gui.setItem(11, fisherman);
        
        // 牧羊人
        ItemStack shepherd = new ItemStack(Material.WHITE_WOOL);
        ItemMeta shepherdMeta = shepherd.getItemMeta();
        shepherdMeta.setDisplayName("§e牧羊人");
        List<String> shepherdLore = new java.util.ArrayList<>();
        shepherdLore.add("§7剪羊毛并饲养绵羊");
        shepherdLore.addAll(getRecruitCostLore());
        shepherdMeta.setLore(shepherdLore);
        shepherd.setItemMeta(shepherdMeta);
        gui.setItem(12, shepherd);
        
        // 图书管理员
        ItemStack librarian = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta librarianMeta = librarian.getItemMeta();
        librarianMeta.setDisplayName("§e图书管理员");
        List<String> librarianLore = new java.util.ArrayList<>();
        librarianLore.add("§7提供附魔服务");
        librarianLore.addAll(getRecruitCostLore());
        librarianMeta.setLore(librarianLore);
        librarian.setItemMeta(librarianMeta);
        gui.setItem(14, librarian);
        
        // 祭司
        ItemStack priest = new ItemStack(Material.POTION);
        ItemMeta priestMeta = priest.getItemMeta();
        priestMeta.setDisplayName("§e祭司");
        List<String> priestLore = new java.util.ArrayList<>();
        priestLore.add("§7酿造各种药水");
        priestLore.addAll(getRecruitCostLore());
        priestMeta.setLore(priestLore);
        priest.setItemMeta(priestMeta);
        gui.setItem(15, priest);
        
        // 制图师
        ItemStack cartographer = new ItemStack(Material.MAP);
        ItemMeta cartographerMeta = cartographer.getItemMeta();
        cartographerMeta.setDisplayName("§e制图师");
        List<String> cartographerLore = new java.util.ArrayList<>();
        cartographerLore.add("§7制作各种地图");
        cartographerLore.addAll(getRecruitCostLore());
        cartographerMeta.setLore(cartographerLore);
        cartographer.setItemMeta(cartographerMeta);
        gui.setItem(16, cartographer);
        
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(18, backButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(26, closeButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 打开村庄升级界面
     * @param player 玩家
     */
    public static void openVillageUpgradeGUI(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            return;
        }
        
        // 检查村庄是否已达到最高等级
        int maxLevel = VillagerPro.getInstance().getConfig().getInt("village.max_level", 5);
        if (village.getLevel() >= maxLevel) {
            player.sendMessage("§c村庄已达到最高等级！");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "村庄升级");
        
        // 获取所有升级选项（不仅仅是未达最高等级的）
        List<String> allUpgradeOptions = new java.util.ArrayList<>();
        if (VillagerPro.getInstance().getConfig().contains("village_upgrades.available_upgrades")) {
            org.bukkit.configuration.ConfigurationSection section = VillagerPro.getInstance().getConfig()
                    .getConfigurationSection("village_upgrades.available_upgrades");
            if (section != null) {
                allUpgradeOptions.addAll(section.getKeys(false));
            }
        }
        
        // 显示升级选项
        int slot = 10; // 起始槽位（第11格）
        for (String upgradeId : allUpgradeOptions) {
            if (slot > 16) break; // 最多显示7个选项（第11-17格）
            
            Material iconMaterial = Material.getMaterial(VillageUpgradeManager.getUpgradeIcon(upgradeId));
            if (iconMaterial == null) {
                iconMaterial = Material.STONE;
            }
            
            ItemStack upgradeItem = new ItemStack(iconMaterial);
            ItemMeta meta = upgradeItem.getItemMeta();
            meta.setDisplayName("§e" + VillageUpgradeManager.getUpgradeDisplayName(upgradeId));
            
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7" + VillageUpgradeManager.getUpgradeDescription(upgradeId));
            
            int currentLevel = VillageUpgradeManager.getVillageUpgradeLevel(village.getId(), upgradeId);
            int maxLevelUpgrade = VillageUpgradeManager.getUpgradeMaxLevel(upgradeId);
            lore.add("§7等级: §e" + currentLevel + "/" + maxLevelUpgrade);
            
            // 如果已达最高等级，添加特殊标识
            if (currentLevel >= maxLevelUpgrade) {
                lore.add("§c§l[已满级]");
            }
            
            // 显示成本
            lore.add("");
            lore.add("§6升级成本:");
            List<cn.popcraft.villagerpro.economy.CostEntry> costs = 
                VillageUpgradeManager.getUpgradeCosts(upgradeId);
            lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(costs));
            
            meta.setLore(lore);
            upgradeItem.setItemMeta(meta);
            gui.setItem(slot, upgradeItem);
            
            slot++;
        }
        
        // 返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(18, backButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(26, closeButton);
        
        player.openInventory(gui);
    }
    
    /**
     * 获取职业图标
     * @param profession 职业
     * @return 对应的Material
     */
    private static Material getProfessionIcon(String profession) {
        switch (profession.toLowerCase()) {
            case "farmer":
                return Material.WHEAT;
            case "fisherman":
                return Material.COD;
            case "shepherd":
                return Material.WHITE_WOOL;
            case "librarian":
                return Material.ENCHANTED_BOOK;
            case "priest":
                return Material.POTION;
            case "cartographer":
                return Material.MAP;
            default:
                return Material.VILLAGER_SPAWN_EGG;
        }
    }
    
    /**
     * 获取职业显示名称
     * @param profession 职业
     * @return 显示名称
     */
    private static String getProfessionDisplayName(String profession) {
        return VillagerPro.getInstance().getConfig()
                .getString("villager.professions." + profession + ".name", profession);
    }
    
    /**
     * 获取招募成本显示Lore
     * @return 成本Lore列表
     */
    private static List<String> getRecruitCostLore() {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("§6招募成本:");
        List<cn.popcraft.villagerpro.economy.CostEntry> costs = 
            cn.popcraft.villagerpro.managers.VillagerManager.getRecruitCosts();
        lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(costs));
        return lore;
    }
    
    /**
     * 获取升级成本显示Lore
     * @param upgradeId 升级ID
     * @return 成本Lore列表
     */
    private static List<String> getUpgradeCostLore(String upgradeId) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("§6升级成本:");
        List<cn.popcraft.villagerpro.economy.CostEntry> costs = 
            cn.popcraft.villagerpro.managers.VillageUpgradeManager.getUpgradeCosts(upgradeId);
        lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(costs));
        return lore;
    }
    
    /**
     * 添加GUI事件处理方法
     */
    // GUI事件处理方法已在GUIListener类中实现
    
    /**
     * 添加GUI工具方法
     */
    // GUI工具方法已在上述方法中实现
}