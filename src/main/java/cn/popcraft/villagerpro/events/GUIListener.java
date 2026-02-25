package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.gui.GUIManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.VillagerUpgradeManager;
import cn.popcraft.villagerpro.managers.SimpleAllianceManager;
import cn.popcraft.villagerpro.managers.SimpleAllianceGUIManager;
import cn.popcraft.villagerpro.gui.CostDisplayGUI;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import org.bukkit.entity.Ageable;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.player.PlayerQuitEvent;

public class GUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Inventory inventory = event.getInventory();
            
            // 检查是否是我们的GUI
            String inventoryTitle = event.getView().getTitle();
            String guiPrefix = GUIManager.getGUIPrefix();
            if (!inventoryTitle.startsWith(guiPrefix)) {
                return;
            }
            
            event.setCancelled(true);
            
            // 获取点击的物品和槽位
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            
            // 根据GUI类型处理点击事件
            if (inventoryTitle.equals(guiPrefix + "村庄信息")) {
                handleVillageGUI(player, clickedItem);
            } else if (inventoryTitle.startsWith(guiPrefix + "村民列表")) {
                handleVillagerListGUI(player, clickedItem, inventory, event);
            } else if (inventoryTitle.equals(guiPrefix + "村民详情")) {
                handleVillagerInfoGUI(player, clickedItem);
            } else if (inventoryTitle.equals(guiPrefix + "村民升级")) {
                handleVillagerUpgradeGUI(player, clickedItem);
            } else if (inventoryTitle.equals(guiPrefix + "村庄仓库")) {
                handleWarehouseGUI(player, clickedItem, event.getClick());
            } else if (inventoryTitle.equals(guiPrefix + "招募村民")) {
                handleRecruitGUI(player, clickedItem);
            } else if (inventoryTitle.equals(guiPrefix + "村庄升级")) {
                handleVillageUpgradeGUI(player, clickedItem);
            } else if (inventoryTitle.contains("村庄装饰商店") || inventoryTitle.contains("装饰管理")) {
                handleDecorationGUI(player, clickedItem, slot);
            } else if (inventoryTitle.contains("[商人]") || inventoryTitle.contains("[旅行者]") || inventoryTitle.contains("[节日使者]")) {
                handleVisitorGUI(player, clickedItem, slot);
            } else if (inventoryTitle.contains("村庄联盟") || inventoryTitle.contains("创建联盟") || inventoryTitle.contains("联盟列表") || inventoryTitle.contains("联盟信息")) {
                SimpleAllianceGUIManager.handleAllianceGUIClick(player, clickedItem);
            } else if (inventoryTitle.equals(guiPrefix + "消耗物品确认")) {
                handleCostConfirmationGUI(player, clickedItem);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理玩家数据
        GUIManager.removeCurrentVillagerId(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        // 检查是否是村民
        if (entity instanceof Villager) {
            Villager villager = (Villager) entity;
            
            // 检查村民是否已被招募
            if (villager.getCustomName() != null && villager.getCustomName().contains("§")) {
                // 已被招募的村民，显示信息GUI
                VillagerData villagerData = VillagerManager.getVillager(villager.getUniqueId());
                if (villagerData != null) {
                    GUIManager.openVillagerInfoGUI(player, villagerData.getId());
                } else {
                    player.sendMessage("§c该村民数据异常！");
                }
            } else {
                // 未被招募的村民，显示招募GUI
                GUIManager.openRecruitGUI(player);
            }
        }
    }
    
    private void handleVillageGUI(Player player, ItemStack clickedItem) {
        switch (clickedItem.getType()) {
            case ANVIL:
                GUIManager.openVillageUpgradeGUI(player);
                break;
            case PLAYER_HEAD:
                GUIManager.openVillagerListGUI(player);
                break;
            case CHEST:
                GUIManager.openWarehouseGUI(player);
                break;
            case VILLAGER_SPAWN_EGG:
                GUIManager.openRecruitGUI(player);
                break;
            case GOLD_BLOCK:
                // 联盟按钮
                if (cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getBoolean("features.alliance", false)) {
                    SimpleAllianceGUIManager.handleAllianceGUIClick(player, clickedItem);
                }
                break;
            case BARRIER:
                player.closeInventory();
                break;
        }
    }
    
    private void handleVillagerListGUI(Player player, ItemStack clickedItem, Inventory inventory, InventoryClickEvent event) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是村民物品
            if (displayName.contains("§7(ID: ")) {
                // 提取村民ID
                String idStr = displayName.substring(displayName.indexOf("(ID: ") + 5, displayName.lastIndexOf(")"));
                try {
                    int villagerId = Integer.parseInt(idStr);
                    // GUIManager.openVillagerInfoGUI 内部会自动存储 villagerId
                    GUIManager.openVillagerInfoGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID！");
                }
            } else if (displayName.equals("§a上一页")) {
                // 上一页按钮
                int currentPage = getCurrentPageFromTitle(event.getView().getTitle());
                if (currentPage > 1) {
                    GUIManager.openVillagerListGUI(player, currentPage - 1);
                }
            } else if (displayName.equals("§a下一页")) {
                // 下一页按钮
                int currentPage = getCurrentPageFromTitle(event.getView().getTitle());
                GUIManager.openVillagerListGUI(player, currentPage + 1);
            } else if (displayName.equals("§c返回村庄")) {
                GUIManager.openVillageGUI(player);
            } else if (displayName.equals("§c关闭")) {
                player.closeInventory();
            }
        }
    }
    
    /**
     * 从GUI标题中提取当前页码
     * @param title GUI标题
     * @return 当前页码，如果解析失败返回1
     */
    private int getCurrentPageFromTitle(String title) {
        try {
            // 格式：村民列表 §7(第X页/共Y页)
            String pageInfo = title.substring(title.indexOf("第") + 1);
            String currentPageStr = pageInfo.substring(0, pageInfo.indexOf("页"));
            return Integer.parseInt(currentPageStr);
        } catch (Exception e) {
            return 1; // 默认返回第1页
        }
    }
    
    private void handleVillagerInfoGUI(Player player, ItemStack clickedItem) {
        // 获取当前GUI中的村民ID
        Integer villagerId = GUIManager.getCurrentVillagerId(player);
        if (villagerId == null) {
            player.sendMessage("§c村民信息异常！");
            return;
        }
        
        switch (clickedItem.getType()) {
            case EXPERIENCE_BOTTLE:
                GUIManager.openVillagerUpgradeGUI(player, villagerId);
                break;
            case LEAD:
                // 切换跟随模式
                VillagerData villager = VillagerManager.getVillagerById(villagerId);
                if (villager != null) {
                    String currentMode = villager.getFollowMode();
                    String newMode;
                    switch (currentMode) {
                        case "FREE":
                            newMode = "FOLLOW";
                            break;
                        case "FOLLOW":
                            newMode = "STAY";
                            break;
                        case "STAY":
                            newMode = "FREE";
                            break;
                        default:
                            newMode = "FREE";
                    }
                    villager.setFollowMode(newMode);
                    if (VillagerManager.updateVillager(villager)) {
                        player.sendMessage("§a跟随模式已切换为: " + newMode);
                        // 刷新GUI
                        GUIManager.openVillagerInfoGUI(player, villagerId);
                    } else {
                        player.sendMessage("§c更新跟随模式失败！");
                    }
                } else {
                    player.sendMessage("§c找不到该村民！");
                }
                break;
            case ARROW:
                GUIManager.openVillagerListGUI(player, 1); // 返回村民列表第1页
                break;
            case BARRIER:
                player.closeInventory();
                break;
        }
    }
    
    private void handleVillagerUpgradeGUI(Player player, ItemStack clickedItem) {
        // 获取当前GUI中的村民ID
        Integer villagerId = GUIManager.getCurrentVillagerId(player);
        if (villagerId == null) {
            player.sendMessage("§c村民信息异常！");
            player.closeInventory();
            return;
        }
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是技能物品
            if (displayName.startsWith("§e")) {
                // 获取村民信息
                VillagerData villager = VillagerManager.getVillagerById(villagerId);
                
                if (villager != null) {
                    String profession = villager.getProfession();
                    
                    // 从lore中获取技能ID
                    String skillId = null;
                    if (meta.hasLore() && meta.getLore() != null) {
                        for (String loreLine : meta.getLore()) {
                            if (loreLine.startsWith("§7技能ID: §e")) {
                                skillId = loreLine.substring(10); // 移除颜色代码
                                break;
                            }
                        }
                    }
                    
                    // 如果lore中没有技能ID，通过显示名称查找
                    if (skillId == null) {
                        skillId = findSkillIdByDisplayName(profession, displayName);
                    }
                    
                    if (skillId != null) {
                        // 检查是否能支付升级费用
                        if (VillagerUpgradeManager.canAffordUpgrade(player, profession, skillId)) {
                            // 支付费用
                            if (VillagerUpgradeManager.payUpgradeCost(player, profession, skillId)) {
                                // 应用升级
                                if (VillagerUpgradeManager.applyVillagerUpgrade(villager, skillId)) {
                                    player.sendMessage("§a技能升级成功！");
                                    // 刷新升级GUI
                                    GUIManager.openVillagerUpgradeGUI(player, villagerId);
                                } else {
                                    player.sendMessage("§c技能升级失败！");
                                }
                            } else {
                                player.sendMessage("§c支付费用失败！");
                            }
                        } else {
                            player.sendMessage("§c你没有足够的资源来升级这个技能！");
                        }
                    } else {
                        player.sendMessage("§c无法识别技能！");
                    }
                } else {
                    player.sendMessage("§c找不到该村民！");
                }
            } else if (displayName.equals("§c返回")) {
                // 获取当前GUI中的村民ID
                if (villagerId != null) {
                    GUIManager.openVillagerInfoGUI(player, villagerId);
                } else {
                    player.sendMessage("§c村民信息异常！");
                    player.closeInventory();
                }
            } else if (displayName.equals("§c关闭")) {
                player.closeInventory();
                // 清理存储的村民ID
                GUIManager.removeCurrentVillagerId(player);
            }
        }
    }
    
    private void handleWarehouseGUI(Player player, ItemStack clickedItem, org.bukkit.event.inventory.ClickType clickType) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是仓库物品
            if (displayName.startsWith("§e")) {
                // 提取物品类型（移除颜色代码 §e）
                String itemType = displayName.substring(2).trim();
                
                Village village = VillageManager.getVillage(player.getUniqueId());
                if (village != null) {
                    // 左键提取全部，右键提取一组(64)
                    if (clickType == org.bukkit.event.inventory.ClickType.LEFT) {
                        // 提取全部
                        cn.popcraft.villagerpro.managers.WarehouseManager.extractAllItem(player, village.getId(), itemType);
                        // 刷新GUI
                        GUIManager.openWarehouseGUI(player);
                    } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                        // 提取一组（64个）
                        cn.popcraft.villagerpro.managers.WarehouseManager.extractItem(player, village.getId(), itemType, 64);
                        // 刷新GUI
                        GUIManager.openWarehouseGUI(player);
                    }
                }
            } else if (displayName.equals("§c返回")) {
                GUIManager.openVillageGUI(player);
            } else if (displayName.equals("§c关闭")) {
                player.closeInventory();
            }
        }
    }
    
    private void handleRecruitGUI(Player player, ItemStack clickedItem) {
        switch (clickedItem.getType()) {
            case WHEAT:
                recruitVillager(player, "farmer");
                break;
            case COD:
                recruitVillager(player, "fisherman");
                break;
            case WHITE_WOOL:
                recruitVillager(player, "shepherd");
                break;
            case ENCHANTED_BOOK:
                recruitVillager(player, "librarian");
                break;
            case POTION:
                recruitVillager(player, "priest");
                break;
            case MAP:
                recruitVillager(player, "cartographer");
                break;
            case ARROW:
                GUIManager.openVillageGUI(player);
                break;
            case BARRIER:
                player.closeInventory();
                break;
        }
    }
    
    private void handleVillageUpgradeGUI(Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是升级选项
            if (displayName.startsWith("§e")) {
                // 从lore中获取升级ID
                String upgradeId = null;
                if (meta.hasLore() && meta.getLore() != null) {
                    for (String loreLine : meta.getLore()) {
                        if (loreLine.startsWith("§8ID: ")) {
                            upgradeId = loreLine.substring(6);
                            break;
                        }
                    }
                }
                
                // 如果lore中没有升级ID，通过显示名称查找
                if (upgradeId == null) {
                    upgradeId = findUpgradeIdByDisplayName(displayName);
                }
                
                if (upgradeId == null) {
                    player.sendMessage("§c无法识别升级项目！");
                    return;
                }
                
                Village village = VillageManager.getVillage(player.getUniqueId());
                if (village != null) {
                    // 检查是否能支付升级费用
                    List<CostEntry> costs = VillageUpgradeManager.getUpgradeCosts(upgradeId);
                    if (CostHandler.canAfford(player, costs)) {
                        // 支付费用
                        if (CostHandler.deduct(player, costs)) {
                            // 应用升级
                            if (VillageUpgradeManager.applyVillageUpgrade(village, upgradeId)) {
                                player.sendMessage("§a村庄升级成功！");
                            } else {
                                player.sendMessage("§c村庄升级失败！");
                            }
                        } else {
                            player.sendMessage("§c支付费用失败！");
                        }
                    } else {
                        player.sendMessage("§c你没有足够的资源来升级这个项目！");
                    }
                } else {
                    player.sendMessage("§c找不到你的村庄！");
                }
            } else if (displayName.equals("§c返回")) {
                GUIManager.openVillageGUI(player);
            } else if (displayName.equals("§c关闭")) {
                player.closeInventory();
            }
        }
    }
    
    private void recruitVillager(Player player, String profession) {
        // 查找附近未被招募的村民
        Villager targetVillager = null;
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                // 检查村民是否已被招募（通过自定义名称判断）
                if (villager.getCustomName() == null || !villager.getCustomName().contains("§")) {
                    targetVillager = villager;
                    break;
                }
            }
        }

        if (targetVillager == null) {
            player.sendMessage("§c附近没有可招募的村民！");
            player.closeInventory();
            return;
        }

        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c你还没有创建村庄！");
            player.closeInventory();
            return;
        }

        // 检查是否能支付招募费用
        List<CostEntry> recruitCosts = VillagerManager.getRecruitCosts();
        if (!CostHandler.canAfford(player, recruitCosts)) {
            player.sendMessage("§c你没有足够的资源来招募村民！");
            player.closeInventory();
            return;
        }

        // 创建final副本以在lambda中使用
        final Village finalVillage = village;
        final Villager finalTargetVillager = targetVillager;
        final List<CostEntry> finalRecruitCosts = recruitCosts;
        final String finalProfession = profession;

        // 使用消耗物品确认界面
        CostDisplayGUI.openCostConfirmationGUI(player, "招募村民", finalRecruitCosts,
                () -> {
                    // 确认回调：执行招募
                    if (!CostHandler.deduct(player, finalRecruitCosts)) {
                        player.sendMessage("§c招募村民时发生错误！");
                        return;
                    }

                    // 执行招募逻辑
                    executeVillagerRecruitment(player, finalVillage, finalTargetVillager, finalProfession);
                },
                () -> {
                    // 取消回调
                    player.sendMessage("§7ℹ️ 招募村民已取消");
                });
        // 招募逻辑现在在executeVillagerRecruitment方法中处理
    }
    
    /**
     * 根据职业和显示名称查找技能ID
     * @param profession 职业
     * @param displayName 显示名称
     * @return 技能ID，如果没有找到则返回null
     */
    private String findSkillIdByDisplayName(String profession, String displayName) {
        String path = "villager_upgrades." + profession;
        if (!cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path)) {
            return null;
        }
        
        // 移除颜色代码
        String cleanDisplayName = displayName.replace("§e", "").replace("§7", "").trim();
        
        org.bukkit.configuration.ConfigurationSection section = 
            cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        
        for (String skillId : section.getKeys(false)) {
            String skillName = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                .getString(path + "." + skillId + ".name", "");
            if (cleanDisplayName.equals(skillName)) {
                return skillId;
            }
        }
        
        return null;
    }
    
    /**
     * 根据显示名称查找升级ID
     * @param displayName 显示名称
     * @return 升级ID，如果没有找到则返回null
     */
    private String findUpgradeIdByDisplayName(String displayName) {
        String path = "village_upgrades.available_upgrades";
        if (!cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().contains(path)) {
            return null;
        }
        
        // 移除颜色代码
        String cleanDisplayName = displayName.replace("§e", "").trim();
        
        org.bukkit.configuration.ConfigurationSection section = 
            cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig().getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        
        for (String upgradeId : section.getKeys(false)) {
            String upgradeName = cn.popcraft.villagerpro.VillagerPro.getInstance().getConfig()
                .getString(path + "." + upgradeId + ".name", "");
            if (cleanDisplayName.equals(upgradeName)) {
                return upgradeId;
            }
        }
        
        return null;
    }
    
    /**
     * 处理装饰GUI点击
     */
    private void handleDecorationGUI(Player player, ItemStack clickedItem, int slot) {
        // 委托给装饰GUI管理器处理
        try {
            cn.popcraft.villagerpro.gui.DecorationGUIManager.handleGUIClick(player, slot, clickedItem);
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().warning("装饰GUI处理失败: " + e.getMessage());
            player.sendMessage("§c装饰功能暂时不可用，请重试");
        }
    }
    
    /**
     * 处理访客GUI点击
     */
    private void handleVisitorGUI(Player player, ItemStack clickedItem, int slot) {
        // 委托给访客GUI管理器处理
        try {
            cn.popcraft.villagerpro.gui.VisitorGUIManager.handleGUIClick(player, slot, clickedItem);
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().warning("访客GUI处理失败: " + e.getMessage());
            player.sendMessage("§c访客功能暂时不可用，请重试");
        }
    }
    
    /**
     * 处理消耗物品确认GUI点击
     */
    private void handleCostConfirmationGUI(Player player, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // 处理确认按钮
        if (displayName.contains("确认执行")) {
            CostDisplayGUI.handleConfirmClick(player);
            player.sendMessage("§a✅ 操作执行成功！");
        }
        // 处理取消按钮
        else if (displayName.contains("取消操作")) {
            CostDisplayGUI.handleCancelClick(player);
            player.sendMessage("§7ℹ️ 操作已取消");
        }
    }
    
    /**
     * 执行村民招募逻辑（从确认界面调用）
     */
    private void executeVillagerRecruitment(Player player, cn.popcraft.villagerpro.models.Village village, Villager targetVillager, String profession) {
        // 设置村民职业
        Villager.Profession villagerProfession;
        switch (profession) {
            case "farmer":
                villagerProfession = Villager.Profession.FARMER;
                break;
            case "fisherman":
                villagerProfession = Villager.Profession.FISHERMAN;
                break;
            case "shepherd":
                villagerProfession = Villager.Profession.SHEPHERD;
                break;
            case "librarian":
                villagerProfession = Villager.Profession.LIBRARIAN;
                break;
            case "priest":
                villagerProfession = Villager.Profession.CLERIC;
                break;
            case "cartographer":
                villagerProfession = Villager.Profession.CARTOGRAPHER;
                break;
            default:
                villagerProfession = Villager.Profession.NONE;
        }
        
        targetVillager.setProfession(villagerProfession);
        
        // 确保村民是成年人
        if (targetVillager instanceof Ageable) {
            Ageable ageable = (Ageable) targetVillager;
            if (!ageable.isAdult()) {
                ageable.setAge(0); // 设置为成年人
            }
        }
        
        // 设置村民自定义名称以标识已被招募
        String professionName = VillagerManager.getProfessionDisplayName(profession);
        targetVillager.setCustomName("§a" + professionName + " §7(ID: " + targetVillager.getEntityId() + ")");
        targetVillager.setCustomNameVisible(true);
        
        // 保存村民数据到数据库
        VillagerData villagerData = VillagerManager.recruitVillager(player, village, targetVillager.getUniqueId(), profession);
        if (villagerData != null) {
            player.sendMessage("§a成功招募了一名" + professionName + "！");
            
            // 延迟打开村庄主界面
            Bukkit.getScheduler().runTaskLater(VillagerPro.getInstance(), () -> {
                GUIManager.openVillageGUI(player);
            }, 1L);
        } else {
            player.sendMessage("§c招募村民失败！");
        }
    }

}