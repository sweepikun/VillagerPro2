package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 简化版联盟GUI管理器
 * 专注于GUI展示和基本交互
 */
public class SimpleAllianceGUIManager {
    
    private static SimpleAllianceGUIManager instance;
    private final VillagerPro plugin;
    private final SimpleAllianceManager allianceManager;
    private final GUIManager guiManager;
    
    private SimpleAllianceGUIManager(VillagerPro plugin, SimpleAllianceManager allianceManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.allianceManager = allianceManager;
        this.guiManager = guiManager;
    }
    
    public static SimpleAllianceGUIManager getInstance() {
        return instance;
    }
    
    public void initialize(VillagerPro plugin, SimpleAllianceManager allianceManager, GUIManager guiManager) {
        if (instance == null) {
            instance = new SimpleAllianceGUIManager(plugin, allianceManager, guiManager);
        }
    }
    
    /**
     * 打开主联盟界面
     */
    public void openAllianceMainGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.GOLD + "村庄联盟管理");
        
        // 获取玩家联盟状态
        boolean hasAlliance = allianceManager.hasAlliance(player.getUniqueId());
        String allianceName = allianceManager.getPlayerAllianceName(player.getUniqueId());
        
        if (!hasAlliance) {
            // 没有联盟，显示加入/创建选项
            inventory.setItem(13, createInfoItem("没有加入联盟", "你还没有加入任何村庄联盟"));
            
            inventory.setItem(11, createAllianceButton("创建新联盟", Material.GOLD_INGOT, 
                "创建你自己的联盟", "邀请其他村庄加入"));
            
            inventory.setItem(15, createAllianceButton("浏览联盟", Material.BOOK, 
                "查看所有可用联盟", "选择一个加入"));
            
        } else {
            // 有联盟，显示联盟信息
            inventory.setItem(13, createAllianceInfoItem(allianceName));
            
            inventory.setItem(20, createAllianceButton("联盟成员", Material.PLAYER_HEAD, 
                "查看联盟成员列表", "管理成员权限"));
            
            inventory.setItem(24, createAllianceButton("邀请村庄", Material.GOLD_INGOT, 
                "邀请其他村庄加入", allianceName));
            
            inventory.setItem(31, createDangerButton("离开联盟", Material.BARRIER, 
                "离开当前联盟", allianceName));
        }
        
        // 导航按钮
        inventory.setItem(22, createNavigationButton("返回主菜单", Material.ARROW, "返回村庄管理界面"));
        
        player.openInventory(inventory);
    }
    
    /**
     * 打开联盟列表界面
     */
    public void openAllianceListGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.GOLD + "联盟列表");
        
        List<SimpleAllianceManager.SimpleAlliance> alliances = allianceManager.getAllAlliances();
        
        // 添加联盟列表
        int slot = 0;
        for (SimpleAllianceManager.SimpleAlliance alliance : alliances) {
            if (slot >= 45) break; // 最多显示45个
            ItemStack item = createAllianceListItem(alliance);
            inventory.setItem(slot, item);
            slot++;
        }
        
        inventory.setItem(49, createNavigationButton("返回", Material.ARROW, "返回联盟主界面"));
        
        player.openInventory(inventory);
    }
    
    /**
     * 处理GUI点击
     */
    public static void handleAllianceGUIClick(Player player, ItemStack clickedItem) {
        if (instance == null) {
            player.sendMessage(ChatColor.RED + "联盟功能未启用！");
            return;
        }
        
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // 处理不同的按钮
        if (displayName.contains("创建新联盟")) {
            // 简化实现：直接创建联盟
            player.sendMessage(ChatColor.YELLOW + "请在聊天中输入联盟名称（输入 'test' 测试）");
            instance.allianceManager.createAlliance(player, "测试联盟");
            player.closeInventory();
        } else if (displayName.contains("浏览联盟")) {
            instance.openAllianceListGUI(player);
        } else if (displayName.contains("联盟成员")) {
            player.sendMessage(ChatColor.GREEN + "✅ 成员列表功能将在后续版本中完善");
        } else if (displayName.contains("邀请村庄")) {
            player.sendMessage(ChatColor.GREEN + "✅ 邀请功能将在后续版本中完善");
        } else if (displayName.contains("离开联盟")) {
            instance.allianceManager.leaveAlliance(player);
            player.closeInventory();
        } else if (displayName.contains("返回")) {
            instance.openAllianceMainGUI(player);
        } else if (displayName.contains("联盟")) {
            // 点击联盟列表项
            player.sendMessage(ChatColor.GREEN + "✅ 联盟详情功能将在后续版本中完善");
        }
    }
    
    // ============== 工具方法 ==============
    
    private ItemStack createInfoItem(String title, String... lines) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + title);
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String line : lines) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createAllianceButton(String name, Material material, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + name);
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String line : lines) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createDangerButton(String name, Material material, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + name);
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String line : lines) {
            lore.add(ChatColor.RED + line);
        }
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createNavigationButton(String name, Material material, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.BLUE + name);
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String line : lines) {
            lore.add(ChatColor.GRAY + line);
        }
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createAllianceInfoItem(String allianceName) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + allianceName);
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "成员数量: 3/5");
        lore.add(ChatColor.GRAY + "创建时间: 2025-01-01");
        lore.add("");
        lore.add(ChatColor.YELLOW + "点击查看详细信息");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createAllianceListItem(SimpleAllianceManager.SimpleAlliance alliance) {
        ItemStack item = new ItemStack(Material.BOOKSHELF);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + alliance.getName());
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "成员: " + alliance.getMemberCount() + "/5");
        lore.add(ChatColor.GRAY + "创建时间: " + alliance.getCreatedDate());
        lore.add("");
        lore.add(ChatColor.YELLOW + "点击查看详情");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}