package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 简化版联盟GUI管理器
 * 专注于GUI展示和基本交互，并接入数据库
 */
public class SimpleAllianceGUIManager {

    private static final NamespacedKey ALLIANCE_ID_KEY = new NamespacedKey(VillagerPro.getInstance(), "alliance_id");
    private static final NamespacedKey PLAYER_NAME_KEY = new NamespacedKey(VillagerPro.getInstance(), "player_name");

    private static SimpleAllianceGUIManager instance;
    private final VillagerPro plugin;
    private final SimpleAllianceManager allianceManager;
    private final ConversationFactory createAllianceFactory;
    private final ConversationFactory inviteFactory;

    private SimpleAllianceGUIManager(VillagerPro plugin, SimpleAllianceManager allianceManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.allianceManager = allianceManager;
        this.createAllianceFactory = new ConversationFactory(plugin)
                .withModality(false)
                .withPrefix(context -> ChatColor.GOLD + "[村庄联盟] ")
                .withFirstPrompt(new CreateAlliancePrompt())
                .withEscapeSequence("取消")
                .withTimeout(60)
                .thatExcludesNonPlayersWithMessage("只有玩家可以创建联盟");
        this.inviteFactory = new ConversationFactory(plugin)
                .withModality(false)
                .withPrefix(context -> ChatColor.GOLD + "[村庄联盟] ")
                .withFirstPrompt(new InvitePlayerPrompt())
                .withEscapeSequence("取消")
                .withTimeout(60)
                .thatExcludesNonPlayersWithMessage("只有玩家可以邀请成员");
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

        // 填充背景
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        boolean hasAlliance = allianceManager.hasAlliance(player.getUniqueId());
        String allianceName = allianceManager.getPlayerAllianceName(player.getUniqueId());

        if (!hasAlliance) {
            inventory.setItem(13, createInfoItem("没有加入联盟", "你还没有加入任何村庄联盟"));
            inventory.setItem(11, createAllianceButton("创建新联盟", Material.GOLD_INGOT,
                    "创建你自己的联盟", "输入联盟名称"));
            inventory.setItem(15, createAllianceButton("浏览联盟", Material.BOOK,
                    "查看所有可用联盟", "选择一个加入"));
        } else {
            inventory.setItem(13, createAllianceInfoItem(allianceName));
            inventory.setItem(11, createAllianceButton("联盟成员", Material.PLAYER_HEAD,
                    "查看联盟成员列表", "包含盟主与所有成员"));
            inventory.setItem(13, createAllianceInfoItem(allianceName)); // 保持信息
            inventory.setItem(15, createAllianceButton("邀请村庄", Material.GOLD_INGOT,
                    "邀请其他玩家村庄加入", "输入玩家名称"));
            inventory.setItem(16, createDangerButton("离开联盟", Material.BARRIER,
                    "离开当前联盟", allianceName));
        }

        inventory.setItem(22, createNavigationButton("返回主菜单", Material.ARROW, "返回村庄管理界面"));
        player.openInventory(inventory);
    }

    /**
     * 打开联盟列表界面
     */
    public void openAllianceListGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.GOLD + "联盟列表");

        List<SimpleAllianceManager.SimpleAlliance> alliances = allianceManager.getAllAlliances();
        int slot = 0;
        for (SimpleAllianceManager.SimpleAlliance alliance : alliances) {
            if (slot >= 45) break;
            inventory.setItem(slot, createAllianceListItem(alliance));
            slot++;
        }

        inventory.setItem(49, createNavigationButton("返回", Material.ARROW, "返回联盟主界面"));
        player.openInventory(inventory);
    }

    /**
     * 打开联盟成员列表
     */
    public void openAllianceMembersGUI(Player player) {
        int allianceId = getPlayerAllianceId(player);
        if (allianceId <= 0) {
            player.sendMessage(ChatColor.RED + "你没有加入任何联盟");
            return;
        }

        SimpleAllianceManager.SimpleAlliance alliance = allianceManager.getAllianceById(allianceId);
        if (alliance == null) {
            player.sendMessage(ChatColor.RED + "联盟数据异常");
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.GOLD + "联盟成员 - " + alliance.getName());
        List<cn.popcraft.villagerpro.models.Village> members = allianceManager.getAllianceMembers(allianceId);

        int slot = 0;
        for (cn.popcraft.villagerpro.models.Village village : members) {
            if (slot >= 45) break;
            inventory.setItem(slot, createMemberItem(village, allianceManager.getAllianceOwnerVillageId(allianceId) == village.getId()));
            slot++;
        }

        inventory.setItem(49, createNavigationButton("返回", Material.ARROW, "返回联盟主界面"));
        player.openInventory(inventory);
    }

    /**
     * 打开联盟详情界面
     */
    public void openAllianceDetailGUI(Player player, int allianceId) {
        SimpleAllianceManager.SimpleAlliance alliance = allianceManager.getAllianceById(allianceId);
        if (alliance == null) {
            player.sendMessage(ChatColor.RED + "联盟不存在");
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.GOLD + "联盟信息 - " + alliance.getName());

        // 联盟信息
        ItemStack info = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + alliance.getName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "成员: " + alliance.getMemberCount() + "/" + plugin.getConfig().getInt("alliance.config.max_members", 5));
        lore.add(ChatColor.GRAY + "创建时间: " + alliance.getCreatedDate());
        lore.add("");
        lore.add(ChatColor.YELLOW + "点击加入该联盟");
        meta.setLore(lore);
        info.setItemMeta(meta);
        setIntTag(info, ALLIANCE_ID_KEY, allianceId);
        inventory.setItem(13, info);

        inventory.setItem(18, createNavigationButton("返回", Material.ARROW, "返回联盟列表"));
        inventory.setItem(26, createNavigationButton("返回主菜单", Material.ARROW, "返回村庄联盟管理"));
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

        if (displayName.contains("创建新联盟")) {
            player.closeInventory();
            Conversation conversation = instance.createAllianceFactory.buildConversation(player);
            conversation.begin();
        } else if (displayName.contains("浏览联盟")) {
            instance.openAllianceListGUI(player);
        } else if (displayName.contains("联盟成员")) {
            instance.openAllianceMembersGUI(player);
        } else if (displayName.contains("邀请村庄")) {
            player.closeInventory();
            Conversation conversation = instance.inviteFactory.buildConversation(player);
            conversation.begin();
        } else if (displayName.contains("离开联盟")) {
            instance.allianceManager.leaveAlliance(player);
            player.closeInventory();
        } else if (displayName.contains("返回主菜单")) {
            GUIManager.openVillageGUI(player);
        } else if (displayName.contains("返回")) {
            String title = player.getOpenInventory().getTitle();
            if (title.contains("联盟信息")) {
                instance.openAllianceListGUI(player);
            } else {
                instance.openAllianceMainGUI(player);
            }
        } else if (displayName.contains("联盟信息") || getIntTag(clickedItem, ALLIANCE_ID_KEY) > 0) {
            int allianceId = getIntTag(clickedItem, ALLIANCE_ID_KEY);
            if (allianceId > 0) {
                instance.openAllianceDetailGUI(player, allianceId);
            }
        } else if (displayName.contains("加入")) {
            int allianceId = getIntTag(clickedItem, ALLIANCE_ID_KEY);
            if (allianceId > 0) {
                instance.allianceManager.joinAlliance(player, allianceId);
                player.closeInventory();
            }
        }
    }

    // ============== Prompts ==============

    private class CreateAlliancePrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.YELLOW + "请输入联盟名称（输入 '取消' 退出）：";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input == null || input.trim().isEmpty()) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "联盟名称不能为空");
                return this;
            }
            Player player = (Player) context.getForWhom();
            Bukkit.getScheduler().runTask(plugin, () -> allianceManager.createAlliance(player, input.trim()));
            return Prompt.END_OF_CONVERSATION;
        }
    }

    private class InvitePlayerPrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.YELLOW + "请输入要邀请的玩家名称（输入 '取消' 退出）：";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input == null || input.trim().isEmpty()) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "玩家名称不能为空");
                return this;
            }

            Player inviter = (Player) context.getForWhom();
            Player target = Bukkit.getPlayer(input.trim());

            if (target == null || !target.isOnline()) {
                inviter.sendRawMessage(ChatColor.RED + "玩家不在线");
                return Prompt.END_OF_CONVERSATION;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                cn.popcraft.villagerpro.models.Village targetVillage = cn.popcraft.villagerpro.managers.VillageManager.getVillage(target.getUniqueId());
                if (targetVillage == null) {
                    inviter.sendMessage(ChatColor.RED + "该玩家没有村庄");
                    return;
                }

                cn.popcraft.villagerpro.models.Village inviterVillage = cn.popcraft.villagerpro.managers.VillageManager.getVillage(inviter.getUniqueId());
                if (inviterVillage == null) {
                    inviter.sendMessage(ChatColor.RED + "你没有村庄");
                    return;
                }

                int allianceId = getAllianceIdByVillageId(inviterVillage.getId());
                if (allianceId <= 0) {
                    inviter.sendMessage(ChatColor.RED + "你没有加入任何联盟");
                    return;
                }

                if (getAllianceIdByVillageId(targetVillage.getId()) > 0) {
                    inviter.sendMessage(ChatColor.RED + "该玩家已经加入了联盟");
                    return;
                }

                if (allianceManager.addMemberToAlliance(allianceId, targetVillage.getId())) {
                    inviter.sendMessage(ChatColor.GREEN + "✅ 成功邀请 " + target.getName() + " 加入联盟！");
                    target.sendMessage(ChatColor.GREEN + "✅ 你已被邀请加入村庄联盟！");
                } else {
                    inviter.sendMessage(ChatColor.RED + "邀请失败");
                }
            });

            return Prompt.END_OF_CONVERSATION;
        }
    }

    // ============== 工具方法 ==============

    private ItemStack createInfoItem(String title, String... lines) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + title);
        List<String> lore = new ArrayList<>();
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
        List<String> lore = new ArrayList<>();
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
        List<String> lore = new ArrayList<>();
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
        List<String> lore = new ArrayList<>();
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
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "你已加入该联盟");
        lore.add(ChatColor.GRAY + "点击下方按钮管理联盟");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAllianceListItem(SimpleAllianceManager.SimpleAlliance alliance) {
        ItemStack item = new ItemStack(Material.BOOKSHELF);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + alliance.getName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "成员: " + alliance.getMemberCount() + "/" + plugin.getConfig().getInt("alliance.config.max_members", 5));
        lore.add(ChatColor.GRAY + "创建时间: " + alliance.getCreatedDate());
        lore.add("");
        lore.add(ChatColor.YELLOW + "点击查看详情/加入");
        meta.setLore(lore);
        item.setItemMeta(meta);
        setIntTag(item, ALLIANCE_ID_KEY, alliance.getId());
        return item;
    }

    private ItemStack createMemberItem(cn.popcraft.villagerpro.models.Village village, boolean isOwner) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        String ownerName = Bukkit.getOfflinePlayer(village.getOwnerUUID()).getName();
        meta.setDisplayName((isOwner ? ChatColor.GOLD + "★ " : ChatColor.GREEN + "") + village.getName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "村长: " + (ownerName != null ? ownerName : "未知"));
        lore.add(ChatColor.GRAY + "村庄等级: " + village.getLevel());
        if (isOwner) {
            lore.add(ChatColor.YELLOW + "盟主");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int getPlayerAllianceId(Player player) {
        cn.popcraft.villagerpro.models.Village village = cn.popcraft.villagerpro.managers.VillageManager.getVillage(player.getUniqueId());
        if (village == null) return -1;
        return getAllianceIdByVillageId(village.getId());
    }

    private int getAllianceIdByVillageId(int villageId) {
        return allianceManager.getAllianceIdByVillageId(villageId);
    }

    private static void setIntTag(ItemStack item, NamespacedKey key, int value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    private static int getIntTag(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.INTEGER) ? container.get(key, PersistentDataType.INTEGER) : -1;
    }
}
