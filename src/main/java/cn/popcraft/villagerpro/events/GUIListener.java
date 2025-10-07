package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.gui.GUIManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.VillagerUpgradeManager;
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

public class GUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Inventory inventory = event.getInventory();
            
            // 检查是否是我们的GUI
            String inventoryTitle = event.getView().getTitle();
            if (!inventoryTitle.startsWith("§f[VP] ")) {
                return;
            }
            
            event.setCancelled(true);
            
            // 获取点击的物品
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            
            // 根据GUI类型处理点击事件
            if (inventoryTitle.equals("§f[VP] 村庄信息")) {
                handleVillageGUI(player, clickedItem);
            } else if (inventoryTitle.equals("§f[VP] 村民列表")) {
                handleVillagerListGUI(player, clickedItem, inventory);
            } else if (inventoryTitle.equals("§f[VP] 村民详情")) {
                handleVillagerInfoGUI(player, clickedItem);
            } else if (inventoryTitle.equals("§f[VP] 村民升级")) {
                handleVillagerUpgradeGUI(player, clickedItem);
            } else if (inventoryTitle.equals("§f[VP] 村庄仓库")) {
                handleWarehouseGUI(player, clickedItem);
            } else if (inventoryTitle.equals("§f[VP] 招募村民")) {
                handleRecruitGUI(player, clickedItem);
            } else if (inventoryTitle.equals("§f[VP] 村庄升级")) {
                handleVillageUpgradeGUI(player, clickedItem);
            }
        }
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
            case BARRIER:
                player.closeInventory();
                break;
        }
    }
    
    private void handleVillagerListGUI(Player player, ItemStack clickedItem, Inventory inventory) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是村民物品
            if (displayName.contains("§7(ID: ")) {
                // 提取村民ID
                String idStr = displayName.substring(displayName.indexOf("(ID: ") + 5, displayName.lastIndexOf(")"));
                try {
                    int villagerId = Integer.parseInt(idStr);
                    GUIManager.openVillagerInfoGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID！");
                }
            } else if (displayName.equals("§c返回")) {
                GUIManager.openVillageGUI(player);
            } else if (displayName.equals("§c关闭")) {
                player.closeInventory();
            }
        }
    }
    
    private void handleVillagerInfoGUI(Player player, ItemStack clickedItem) {
        switch (clickedItem.getType()) {
            case EXPERIENCE_BOTTLE:
                // 获取当前打开的GUI中的村民ID
                // 这里简化处理，实际应该通过其他方式传递村民ID
                // 注意：这里我们假设在调用此方法前已经知道要升级的村民ID
                GUIManager.openVillagerUpgradeGUI(player, 1); // 示例ID
                break;
            case LEAD:
                // 切换跟随模式
                // 这里简化处理，实际应该通过其他方式传递村民ID
                VillagerData villager = VillagerManager.getVillagerById(1); // 示例ID
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
                    VillagerManager.updateVillager(villager);
                    player.sendMessage("§a跟随模式已切换为: " + newMode);
                }
                break;
            case ARROW:
                GUIManager.openVillagerListGUI(player);
                break;
            case BARRIER:
                player.closeInventory();
                break;
        }
    }
    
    private void handleVillagerUpgradeGUI(Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是技能物品
            if (displayName.startsWith("§e")) {
                // 获取技能ID和升级ID
                String skillDisplayName = displayName.substring(2); // 移除颜色代码
                
                // 从配置中查找对应的技能ID
                // 这里简化处理，实际应该通过更好的方式映射显示名称到技能ID
                String profession = "farmer"; // 示例职业
                String skillId = "efficient_harvest"; // 示例技能ID
                
                // 获取村民ID
                // 这里简化处理，实际应该通过其他方式传递村民ID
                int villagerId = 1; // 示例ID
                VillagerData villager = VillagerManager.getVillagerById(villagerId);
                
                if (villager != null) {
                    // 检查是否能支付升级费用
                    if (VillagerUpgradeManager.canAffordUpgrade(player, profession, skillId)) {
                        // 支付费用
                        if (VillagerUpgradeManager.payUpgradeCost(player, profession, skillId)) {
                            // 应用升级
                            if (VillagerUpgradeManager.applyVillagerUpgrade(villager, skillId)) {
                                player.sendMessage("§a技能升级成功！");
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
                    player.sendMessage("§c找不到该村民！");
                }
            } else if (displayName.equals("§c返回")) {
                GUIManager.openVillagerInfoGUI(player, 1); // 示例ID
            } else if (displayName.equals("§c关闭")) {
                player.closeInventory();
            }
        }
    }
    
    private void handleWarehouseGUI(Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            
            // 检查是否是仓库物品
            if (displayName.startsWith("§e")) {
                // 提取物品类型
                String itemType = displayName.substring(2); // 移除颜色代码
                
                Village village = VillageManager.getVillage(player.getUniqueId());
                if (village != null) {
                    // 检查点击的是左键还是右键
                    // 这里简化处理，实际应该根据点击类型执行不同操作
                    player.sendMessage("§a你点击了物品: " + itemType);
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
                // 获取升级ID
                // 这里简化处理，实际应该通过更好的方式映射显示名称到升级ID
                String upgradeId = "villager_capacity"; // 示例升级ID
                
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
        
        // 扣除招募费用
        if (!CostHandler.deduct(player, recruitCosts)) {
            player.sendMessage("§c招募村民时发生错误！");
            player.closeInventory();
            return;
        }
        
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
            
            // 延迟打开村民列表GUI，确保数据库操作完成
            Bukkit.getScheduler().runTaskLater(VillagerPro.getInstance(), () -> {
                GUIManager.openVillageGUI(player);
            }, 1L);
        } else {
            player.sendMessage("§c招募村民失败！");
        }
        
        player.closeInventory();
    }
}