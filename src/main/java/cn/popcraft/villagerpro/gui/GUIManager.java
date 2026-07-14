package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.VillagerUpgradeManager;
import cn.popcraft.villagerpro.managers.WarehouseManager;
import cn.popcraft.villagerpro.managers.PolicyManager;
import cn.popcraft.villagerpro.managers.CrisisManager;
import cn.popcraft.villagerpro.scheduler.WorkScheduler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GUIManager {
    private static final String GUI_PREFIX = VillagerPro.getInstance().getConfig().getString("gui.title_prefix", "§f[VP] ");
    
    private static final Map<Player, Integer> currentViewingVillagerId = new HashMap<>();
    
    public static String getGUIPrefix() {
        return GUI_PREFIX;
    }
    
    public static void setCurrentVillagerId(Player player, int villagerId) {
        currentViewingVillagerId.put(player, villagerId);
    }
    
    public static Integer getCurrentVillagerId(Player player) {
        return currentViewingVillagerId.get(player);
    }
    
    public static void removeCurrentVillagerId(Player player) {
        currentViewingVillagerId.remove(player);
    }
    
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

        if (VillagerPro.getInstance().getConfig().getBoolean("orders.enabled", true)) {
            ItemStack ordersButton = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta ordersMeta = ordersButton.getItemMeta();
            ordersMeta.setDisplayName("§e每日订单");
            ordersMeta.setLore(java.util.Arrays.asList("§7从仓库交付物资，获得金币与繁荣度"));
            ordersButton.setItemMeta(ordersMeta);
            gui.setItem(19, ordersButton);
        }

        ItemStack statsButton = new ItemStack(Material.CLOCK);
        ItemMeta statsMeta = statsButton.getItemMeta();
        statsMeta.setDisplayName("§b生产统计");
        statsMeta.setLore(java.util.Arrays.asList("§7查看最近产量、成功率和停工原因"));
        statsButton.setItemMeta(statsMeta);
        gui.setItem(20, statsButton);

        ItemStack rulesButton = new ItemStack(Material.COMPARATOR);
        ItemMeta rulesMeta = rulesButton.getItemMeta();
        rulesMeta.setDisplayName("§d仓库规则");
        rulesMeta.setLore(java.util.Arrays.asList("§7设置保留量、生产开关和溢出处理"));
        rulesButton.setItemMeta(rulesMeta);
        gui.setItem(21, rulesButton);

        if (VillagerPro.getInstance().getConfig().getBoolean("features.policies", true)) {
            PolicyManager.ActivePolicy policy = PolicyManager.getActivePolicy(village.getId());
            ItemStack policyButton = new ItemStack(Material.LECTERN);
            ItemMeta policyMeta = policyButton.getItemMeta();
            policyMeta.setDisplayName("§6村庄政策");
            policyMeta.setLore(java.util.Arrays.asList(
                    policy == null ? "§7当前未实行政策"
                            : "§a当前: " + policy.type().getDisplayName(),
                    "§e点击查看政策效果与剩余时间"));
            policyButton.setItemMeta(policyMeta);
            gui.setItem(22, policyButton);
        }

        if (VillagerPro.getInstance().getConfig().getBoolean("features.crises", true)) {
            CrisisManager.ActiveCrisis crisis = CrisisManager.getActiveCrisis(village.getId());
            ItemStack crisisButton = new ItemStack(Material.BELL);
            ItemMeta crisisMeta = crisisButton.getItemMeta();
            crisisMeta.setDisplayName(crisis == null ? "§a村庄安定" : "§c村庄危机");
            crisisMeta.setLore(crisis == null
                    ? java.util.Arrays.asList("§7当前没有危机", "§e点击查看状态")
                    : java.util.Arrays.asList(
                    "§c" + crisis.type().getDisplayName(),
                    "§7救援: " + crisis.requiredItem() + " "
                            + crisis.contributedAmount() + "/" + crisis.requiredAmount(),
                    "§e点击查看影响与剩余时间"));
            crisisButton.setItemMeta(crisisMeta);
            gui.setItem(23, crisisButton);
        }

        if (VillagerPro.getInstance().getConfig().getBoolean("features.caravans", true)) {
            ItemStack caravanButton = new ItemStack(Material.MINECART);
            ItemMeta caravanMeta = caravanButton.getItemMeta();
            caravanMeta.setDisplayName("§e商队路线");
            caravanMeta.setLore(java.util.Arrays.asList(
                    "§7查看运输中、待领取和失败记录",
                    "§e点击查看商队状态"));
            caravanButton.setItemMeta(caravanMeta);
            gui.setItem(24, caravanButton);
        }

        if (VillagerPro.getInstance().getConfig().getBoolean("features.defense", true)) {
            ItemStack guardButton = new ItemStack(Material.IRON_BLOCK);
            ItemMeta guardMeta = guardButton.getItemMeta();
            guardMeta.setDisplayName("§b召唤村庄守卫");
            guardMeta.setLore(java.util.Arrays.asList(
                    "§7达到配置等级后，可支付资源召唤限时铁傀儡守卫"));
            guardButton.setItemMeta(guardMeta);
            gui.setItem(12, guardButton);
        }

        if (VillagerPro.getInstance().getConfig().getBoolean("features.decorations", true)) {
            ItemStack decorationButton = new ItemStack(Material.FLOWER_POT);
            ItemMeta decorationMeta = decorationButton.getItemMeta();
            decorationMeta.setDisplayName("§d村庄装饰");
            decorationButton.setItemMeta(decorationMeta);
            gui.setItem(17, decorationButton);
        }
        
        // 联盟按钮（如果联盟功能启用）
        if (VillagerPro.getInstance().getConfig().getBoolean("features.alliance", false)) {
            ItemStack allianceButton = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta allianceMeta = allianceButton.getItemMeta();
            allianceMeta.setDisplayName("§e联盟管理");
            List<String> allianceLore = new java.util.ArrayList<>();
            allianceLore.add("§7管理村庄联盟");
            allianceLore.add("§7与其他村庄合作");
            allianceMeta.setLore(allianceLore);
            allianceButton.setItemMeta(allianceMeta);
            gui.setItem(14, allianceButton);
        }
        
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
        openVillagerListGUI(player, 1); // 默认显示第1页
    }
    
    /**
     * 打开村民列表界面（分页版本）
     * @param player 玩家
     * @param page 页码（从1开始）
     */
    public static void openVillagerListGUI(Player player, int page) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            return;
        }
        
        List<VillagerData> villagers = VillagerManager.getVillagers(village.getId());
        if (villagers.isEmpty()) {
            player.sendMessage("§7你还没有招募任何村民！");
            return;
        }
        
        // 分页设置
        int VILLAGERS_PER_PAGE = 36; // 每页最多显示36个村民（4行×9列）
        int NAVIGATION_ROWS = 2; // 底部保留2行用于导航
        
        // 计算总页数
        int totalPages = (villagers.size() + VILLAGERS_PER_PAGE - 1) / VILLAGERS_PER_PAGE;
        
        // 修正页码
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        // 计算当前页的村民范围
        int startIndex = (page - 1) * VILLAGERS_PER_PAGE;
        int endIndex = Math.min(startIndex + VILLAGERS_PER_PAGE, villagers.size());
        
        // 创建固定大小的GUI（54格，6行）
        int size = 54;
        Inventory gui = Bukkit.createInventory(null, size, GUI_PREFIX + "村民列表 §7(第" + page + "页/共" + totalPages + "页)");
        
        // 显示当前页的村民
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex; // 在当前页中的位置 (0-35)
            VillagerData villager = villagers.get(i);
            
            Material iconMaterial = getProfessionIcon(villager.getProfession());
            ItemStack villagerItem = new ItemStack(iconMaterial);
            ItemMeta meta = villagerItem.getItemMeta();
            meta.setDisplayName("§e" + getProfessionDisplayName(villager.getProfession()) + " §7(ID: " + villager.getId() + ")");
            
            List<String> lore = new java.util.ArrayList<>();
            lore.add("§7等级: §e" + villager.getLevel());
            lore.add("§7经验: §e" + villager.getExperience());
            lore.add("§7跟随模式: §e" + villager.getFollowMode());
            
            // 添加产出倒计时
            long remainingTime = WorkScheduler.getRemainingWorkTime(villager.getId());
            if (remainingTime <= 0) {
                lore.add("§a下次产出: 准备就绪!");
            } else {
                lore.add("§6下次产出: §e" + formatTime(remainingTime));
            }
            lore.add("");
            lore.add("§e左键§7查看详情");
            lore.add("§e右键§7升级");
            meta.setLore(lore);
            
            villagerItem.setItemMeta(meta);
            gui.setItem(slotIndex, villagerItem);
        }
        
        // 填充背景玻璃板
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        
        // 填充空位
        for (int i = 0; i < size - 18; i++) { // 保留最后2行(18格)给导航按钮
            if (gui.getItem(i) == null) {
                gui.setItem(i, background);
            }
        }
        
        // 导航按钮（第5行，第17-20格）
        if (page > 1) {
            // 上一页按钮
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName("§a上一页");
            List<String> prevLore = new java.util.ArrayList<>();
            prevLore.add("§7当前页: §e" + page + "§7/§e" + totalPages);
            prevLore.add("§7点击返回上一页");
            prevMeta.setLore(prevLore);
            prevButton.setItemMeta(prevMeta);
            gui.setItem(36, prevButton); // 第5行第1格
        }
        
        if (page < totalPages) {
            // 下一页按钮
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName("§a下一页");
            List<String> nextLore = new java.util.ArrayList<>();
            nextLore.add("§7当前页: §e" + page + "§7/§e" + totalPages);
            nextLore.add("§7点击查看下一页");
            nextMeta.setLore(nextLore);
            nextButton.setItemMeta(nextMeta);
            gui.setItem(44, nextButton); // 第5行第9格（从0开始计数，所以是第44格）
        }
        
        // 页面信息显示
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName("§6页面信息");
        List<String> pageInfoLore = new java.util.ArrayList<>();
        pageInfoLore.add("§7村民总数: §e" + villagers.size());
        pageInfoLore.add("§7当前页: §e" + page + "§7/§e" + totalPages);
        pageInfoLore.add("§7本页显示: §e" + (endIndex - startIndex) + "§7个村民");
        pageInfoMeta.setLore(pageInfoLore);
        pageInfo.setItemMeta(pageInfoMeta);
        gui.setItem(40, pageInfo); // 第5行中间
        
        // 返回按钮（第6行）
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回村庄");
        backButton.setItemMeta(backMeta);
        gui.setItem(45, backButton); // 第6行第2格
        
        // 关闭按钮（第6行）
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        closeButton.setItemMeta(closeMeta);
        gui.setItem(53, closeButton); // 第6行第9格
        
        player.openInventory(gui);
    }
    
    /**
     * 打开村民详情界面
     * @param player 玩家
     * @param villagerId 村民ID
     */
    public static void openVillagerInfoGUI(Player player, int villagerId) {
        setCurrentVillagerId(player, villagerId);
        
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
            removeCurrentVillagerId(player);
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
        int effectiveWorkRange = villager.getWorkRange();
        if ("farmer".equals(villager.getProfession())) {
            effectiveWorkRange += village.getUpgrades().getOrDefault("farming_boost", 0)
                    * VillageUpgradeManager.getIntEffect(
                    "farming_boost", "range_per_level", 2);
        }
        lore.add("§7有效工作范围: §e" + effectiveWorkRange + "格");
        if (cn.popcraft.villagerpro.managers.EcoChainManager.getInstance()
                .hasProcessingRecipes(villager.getProfession())) {
            double levelMultiplier = cn.popcraft.villagerpro.util.GameplayMath.levelMultiplier(
                    villager.getLevel(), VillagerPro.getInstance().getConfig().getDouble(
                            "eco_chain.processing_output_bonus_per_level", 0.05));
            lore.add("§7等级成品倍率: §e" + String.format("%.0f%%", levelMultiplier * 100));
        } else {
            lore.add("§7等级/技能产出: §e" + villager.getBaseProductionAmount() + "个");
        }
        meta.setLore(lore);
        
        villagerItem.setItemMeta(meta);
        gui.setItem(13, villagerItem);
        
        // 升级按钮
        ItemStack upgradeButton = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta upgradeMeta = upgradeButton.getItemMeta();
        upgradeMeta.setDisplayName("§a升级村民");
        upgradeButton.setItemMeta(upgradeMeta);
        gui.setItem(11, upgradeButton);

        ItemStack specializationButton = new ItemStack(Material.NETHER_STAR);
        ItemMeta specializationMeta = specializationButton.getItemMeta();
        specializationMeta.setDisplayName("§d职业专精");
        cn.popcraft.villagerpro.models.VillagerSpecialization specialization =
                cn.popcraft.villagerpro.managers.SpecializationManager
                        .getSpecialization(villager.getId());
        specializationMeta.setLore(java.util.Arrays.asList(specialization == null
                ? "§7尚未选择互斥专精分支"
                : "§7当前: §f" + cn.popcraft.villagerpro.managers.SpecializationManager
                        .getBranchName(villager.getProfession(), specialization.getBranchId())
                        + " " + specialization.getLevel() + "级"));
        specializationButton.setItemMeta(specializationMeta);
        gui.setItem(10, specializationButton);

        ItemStack needsButton = new ItemStack(Material.APPLE);
        ItemMeta needsMeta = needsButton.getItemMeta();
        needsMeta.setDisplayName("§a生活需求");
        cn.popcraft.villagerpro.models.VillagerNeeds needs =
                cn.popcraft.villagerpro.managers.NeedsManager.getNeeds(villager.getId());
        needsMeta.setLore(java.util.Arrays.asList(
                "§7温饱/舒适/健康最低值: §f" + String.format("%.0f", needs.getLowestValue()),
                "§7需求会实际改变生产效率"));
        needsButton.setItemMeta(needsMeta);
        gui.setItem(12, needsButton);

        ItemStack workstationButton = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta workstationMeta = workstationButton.getItemMeta();
        workstationMeta.setDisplayName("§6实体工作站");
        String workstationIssue = cn.popcraft.villagerpro.managers.WorkstationManager
                .getOperationalIssue(villager);
        workstationMeta.setLore(java.util.Arrays.asList(workstationIssue == null
                ? "§a工作站正常"
                : "§c" + workstationIssue));
        workstationButton.setItemMeta(workstationMeta);
        gui.setItem(14, workstationButton);
        
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
        String upgradePath = "villager_upgrades." + profession;
        org.bukkit.configuration.file.FileConfiguration config = VillagerPro.getInstance().getConfig();
        if (config.contains(upgradePath) &&
            config.getConfigurationSection(upgradePath) != null) {
            Set<String> skills = config
                .getConfigurationSection(upgradePath).getKeys(false);
            
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
                lore.add("§7技能ID: §e" + skillId); // 添加技能ID用于后续处理
                
                // 显示成本
                lore.add("");
                lore.add("§6升级成本:");
                List<cn.popcraft.villagerpro.economy.CostEntry> costs = 
                    VillagerUpgradeManager.getUpgradeCosts(profession, skillId, currentLevel + 1);
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
        
        warehouseItems.removeIf(item -> item.getAmount() <= 0);

        // 至少一行内容和一行控制按钮，最多 5 行内容。
        int contentRows = Math.max(1, (warehouseItems.size() + 8) / 9);
        int size = Math.min((contentRows + 1) * 9, 54);
        
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
            cn.popcraft.villagerpro.managers.WarehouseRuleManager.ItemRule rule =
                    cn.popcraft.villagerpro.managers.WarehouseRuleManager.getItemRule(
                            village.getId(), item.getItemType());
            lore.add("§7保留量: §f" + rule.getReserveAmount());
            lore.add("§7可提取: §f" + cn.popcraft.villagerpro.managers.WarehouseManager
                    .getExtractableAmount(village.getId(), item.getItemType()));
            lore.add("§7生产: " + (rule.isProductionEnabled() ? "§a开启" : "§c停产"));
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

        ItemStack rulesButton = new ItemStack(Material.COMPARATOR);
        ItemMeta rulesMeta = rulesButton.getItemMeta();
        rulesMeta.setDisplayName("§d仓库规则");
        rulesButton.setItemMeta(rulesMeta);
        gui.setItem(size - 5, rulesButton);
        
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

        if (VillagerPro.getInstance().getConfig().getBoolean("features.eco_chain", true)) {
            cn.popcraft.villagerpro.managers.EcoChainManager eco =
                    cn.popcraft.villagerpro.managers.EcoChainManager.getInstance();
            if (eco.hasPrerequisites(village, "baker")) {
                ItemStack baker = new ItemStack(Material.BREAD);
                ItemMeta meta = baker.getItemMeta();
                meta.setDisplayName("§e面包师");
                List<String> lore = new java.util.ArrayList<>();
                lore.add("§7将农民的小麦加工成面包");
                lore.addAll(getRecruitCostLore());
                meta.setLore(lore);
                baker.setItemMeta(meta);
                gui.setItem(13, baker);
            }
            if (eco.hasPrerequisites(village, "weaver")) {
                ItemStack weaver = new ItemStack(Material.LOOM);
                ItemMeta meta = weaver.getItemMeta();
                meta.setDisplayName("§e织布工");
                List<String> lore = new java.util.ArrayList<>();
                lore.add("§7将牧羊人的羊毛加工成地毯");
                lore.addAll(getRecruitCostLore());
                meta.setLore(lore);
                weaver.setItemMeta(meta);
                gui.setItem(22, weaver);
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
     * 打开村庄升级界面
     * @param player 玩家
     */
    public static void openVillageUpgradeGUI(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c 你还没有创建村庄！");
            return;
        }
        
        org.bukkit.configuration.file.FileConfiguration config = VillagerPro.getInstance().getConfig();
        
        if (VillageUpgradeManager.getAvailableUpgradePoints(village) <= 0) {
            player.sendMessage("§c 当前没有可用的村庄技能点，请先提升村庄等级！");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PREFIX + "村庄升级");
        
        int optionCount = Math.max(1, config.getInt("village_upgrades.options_per_level", 3));
        List<String> allUpgradeOptions = VillageUpgradeManager.getRandomUpgradeOptions(village, optionCount);
        
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
                VillageUpgradeManager.getUpgradeCosts(upgradeId, currentLevel + 1);
            lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(costs));
            
            // 添加升级ID用于后续处理
            lore.add("");
            lore.add("§8ID: " + upgradeId);
            
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
     * @param currentLevel 当前等级
     * @return 成本Lore列表
     */
    private static List<String> getUpgradeCostLore(String upgradeId, int currentLevel) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("§6升级成本:");
        List<cn.popcraft.villagerpro.economy.CostEntry> costs = 
            cn.popcraft.villagerpro.managers.VillageUpgradeManager.getUpgradeCosts(upgradeId, currentLevel + 1);
        lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(costs));
        return lore;
    }
    
    /**
     * 格式化时间显示
     * @param millis 毫秒数
     * @return 格式化的时间字符串
     */
    private static String formatTime(long millis) {
        if (millis <= 0) {
            return "准备就绪";
        }
        
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d时%d分%d秒", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
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
