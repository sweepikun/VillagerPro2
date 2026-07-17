package cn.popcraft.villagerpro.gui;

import cn.popcraft.villagerpro.managers.NeedsManager;
import cn.popcraft.villagerpro.managers.PersonalityManager;
import cn.popcraft.villagerpro.managers.SpecializationManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillagerAbilityManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.WorkstationManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.models.VillagerNeeds;
import cn.popcraft.villagerpro.models.VillagerSpecialization;
import cn.popcraft.villagerpro.models.VillagerWorkstation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VillagerDevelopmentGUI {
    public static final String SPECIALIZATION_TITLE = "职业专精";
    public static final String NEEDS_TITLE = "村民需求";
    public static final String WORKSTATION_TITLE = "村民工作站";

    private VillagerDevelopmentGUI() {
    }

    public static void openSpecialization(Player player, int villagerId) {
        VillagerData villager = ownedVillager(player, villagerId);
        if (villager == null) return;
        List<String> branches = SpecializationManager.getBranchIds(villager.getProfession());
        if (branches.isEmpty()) {
            player.sendMessage("§c该职业暂时没有专精分支");
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 27,
                GUIManager.getGUIPrefix() + SPECIALIZATION_TITLE);
        VillagerSpecialization selected = SpecializationManager.getSpecialization(villagerId);
        int[] slots = {11, 15, 13, 10, 16};
        for (int index = 0; index < branches.size() && index < slots.length; index++) {
            String branchId = branches.get(index);
            ItemStack item = new ItemStack(SpecializationManager.getBranchIcon(
                    villager.getProfession(), branchId));
            ItemMeta meta = item.getItemMeta();
            boolean active = selected != null && selected.getBranchId().equals(branchId);
            meta.setDisplayName((active ? "§a" : "§e")
                    + SpecializationManager.getBranchName(villager.getProfession(), branchId));
            int currentLevel = active ? selected.getLevel() : 0;
            List<String> lore = new ArrayList<>();
            lore.add("§7" + SpecializationManager.getBranchDescription(
                    villager.getProfession(), branchId));
            lore.add("§7等级: §f" + currentLevel + "/" + SpecializationManager.getMaxLevel());
            lore.add("§7解锁要求: §f村民 "
                    + SpecializationManager.getRequiredVillagerLevel() + " 级");
            if (selected != null && !active) {
                lore.add("§c与当前分支互斥，请先洗点");
            } else if (currentLevel >= SpecializationManager.getMaxLevel()) {
                lore.add("§a已经满级");
            } else {
                lore.add("");
                lore.add("§6选择/升级成本:");
                lore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(
                        SpecializationManager.getSelectionCosts(currentLevel + 1)));
                lore.add("§e点击选择或升级");
            }
            lore.add("§8分支ID: " + branchId);
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slots[index], item);
        }
        if (selected != null) {
            ItemStack respec = new ItemStack(Material.REDSTONE);
            ItemMeta meta = respec.getItemMeta();
            meta.setDisplayName("§c重置专精");
            meta.setLore(Arrays.asList("§7清除当前互斥分支与等级",
                    "§7费用: §e" + (int) cn.popcraft.villagerpro.VillagerPro.getInstance()
                            .getConfig().getDouble("specializations.respec_cost", 500) + "金币"));
            respec.setItemMeta(meta);
            gui.setItem(22, respec);
        }
        addNavigation(gui);
        player.openInventory(gui);
    }

    public static void openNeeds(Player player, int villagerId) {
        if (!NeedsManager.isEnabled()) {
            player.sendMessage("§c村民需求功能当前未启用");
            return;
        }
        VillagerData villager = ownedVillager(player, villagerId);
        if (villager == null) return;
        VillagerNeeds needs = NeedsManager.getNeeds(villagerId);
        Inventory gui = Bukkit.createInventory(null, 27,
                GUIManager.getGUIPrefix() + NEEDS_TITLE);
        gui.setItem(10, needItem(Material.BREAD, "温饱", needs.getHunger(),
                "自动消耗面包、胡萝卜或马铃薯"));
        gui.setItem(13, needItem(Material.WHITE_WOOL, "舒适", needs.getComfort(),
                "自动消耗羊毛或地毯，工作站也会恢复舒适"));
        gui.setItem(16, needItem(Material.POTION, "健康", needs.getHealth(),
                "自动消耗药水"));
        ItemStack efficiency = new ItemStack(Material.BEACON);
        ItemMeta efficiencyMeta = efficiency.getItemMeta();
        efficiencyMeta.setDisplayName("§b当前生产效率");
        List<String> lore = new ArrayList<>();
        lore.add("§7需求倍率: §f" + String.format("%.0f%%",
                NeedsManager.getProductionMultiplier(villager) * 100));
        lore.add("§7个性效率: §f" + String.format("%.0f%%",
                PersonalityManager.getInstance().getProductionMultiplier(villager) * 100));
        lore.add("§7按三项需求中的最低值判定");
        lore.add("§7最近消耗: §f" + (needs.getLastConsumed().isBlank()
                ? "暂无" : needs.getLastConsumed()));
        lore.add("§8保留库存不会被自动消耗");
        efficiencyMeta.setLore(lore);
        efficiency.setItemMeta(efficiencyMeta);
        gui.setItem(22, efficiency);
        addNavigation(gui);
        player.openInventory(gui);
    }

    public static void openWorkstation(Player player, int villagerId) {
        if (!WorkstationManager.isEnabled()) {
            player.sendMessage("§c实体工作站功能当前未启用");
            return;
        }
        VillagerData villager = ownedVillager(player, villagerId);
        if (villager == null) return;
        Inventory gui = Bukkit.createInventory(null, 27,
                GUIManager.getGUIPrefix() + WORKSTATION_TITLE);
        VillagerWorkstation workstation = WorkstationManager.getWorkstation(villagerId);
        Material expected = WorkstationManager.getExpectedMaterial(villager.getProfession());
        ItemStack info = new ItemStack(expected);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e" + expected.name());
        List<String> lore = new ArrayList<>();
        if (workstation == null) {
            lore.add("§c尚未绑定");
            lore.add("§7该村民在绑定前不会生产");
        } else {
            lore.add("§7等级: §f" + workstation.getLevel());
            lore.add("§7位置: §f" + workstation.getWorld() + " "
                    + workstation.getBlockX() + ", " + workstation.getBlockY()
                    + ", " + workstation.getBlockZ());
            String issue = WorkstationManager.getOperationalIssue(villager);
            lore.add("§7状态: " + (issue == null ? "§a正常" : "§c" + issue));
            lore.add("§7生产倍率: §f" + String.format("%.0f%%",
                    WorkstationManager.getProductionMultiplier(villager) * 100));
        }
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        gui.setItem(13, info);

        ItemStack bind = new ItemStack(Material.COMPASS);
        ItemMeta bindMeta = bind.getItemMeta();
        bindMeta.setDisplayName(workstation == null ? "§a绑定工作站" : "§e重新绑定工作站");
        bindMeta.setLore(Arrays.asList("§7点击后右键村庄范围内的 " + expected.name(),
                "§7重新绑定会保留工作站等级"));
        bind.setItemMeta(bindMeta);
        gui.setItem(11, bind);

        ItemStack upgrade = new ItemStack(Material.ANVIL);
        ItemMeta upgradeMeta = upgrade.getItemMeta();
        upgradeMeta.setDisplayName("§a升级工作站");
        int nextLevel = workstation == null ? 2 : workstation.getLevel() + 1;
        List<String> upgradeLore = new ArrayList<>();
        if (workstation == null) {
            upgradeLore.add("§c请先绑定工作站");
        } else if (nextLevel > WorkstationManager.getMaxLevel()) {
            upgradeLore.add("§a已经满级");
        } else {
            upgradeLore.add("§7每级实际提高生产和加工效率");
            upgradeLore.addAll(cn.popcraft.villagerpro.economy.CostHandler.getDisplayLore(
                    WorkstationManager.getUpgradeCosts(nextLevel)));
        }
        upgradeMeta.setLore(upgradeLore);
        upgrade.setItemMeta(upgradeMeta);
        gui.setItem(15, upgrade);
        addNavigation(gui);
        player.openInventory(gui);
    }

    public static void handleSpecializationClick(Player player, ItemStack item) {
        Integer villagerId = GUIManager.getCurrentVillagerId(player);
        if (villagerId == null || handleNavigation(player, item, villagerId)) return;
        VillagerData villager = ownedVillager(player, villagerId);
        if (villager == null || !item.hasItemMeta()) return;
        if (item.getType() == Material.REDSTONE) {
            SpecializationManager.respec(player, villager);
        } else {
            String branchId = readHiddenId(item.getItemMeta(), "§8分支ID: ");
            if (branchId != null) SpecializationManager.selectOrUpgrade(player, villager, branchId);
        }
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village != null) VillagerAbilityManager.applyVillageHealthBoost(village);
        openSpecialization(player, villagerId);
    }

    public static void handleNeedsClick(Player player, ItemStack item) {
        Integer villagerId = GUIManager.getCurrentVillagerId(player);
        if (villagerId != null) handleNavigation(player, item, villagerId);
    }

    public static void handleWorkstationClick(Player player, ItemStack item) {
        Integer villagerId = GUIManager.getCurrentVillagerId(player);
        if (villagerId == null || handleNavigation(player, item, villagerId)) return;
        VillagerData villager = ownedVillager(player, villagerId);
        if (villager == null) return;
        if (item.getType() == Material.COMPASS) {
            WorkstationManager.beginBinding(player, villager);
            return;
        }
        if (item.getType() == Material.ANVIL) {
            WorkstationManager.upgrade(player, villager);
            openWorkstation(player, villagerId);
        }
    }

    private static ItemStack needItem(Material material, String name, double value, String detail) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(colorFor(value) + name + " " + String.format("%.0f/100", value));
        meta.setLore(Arrays.asList("§7" + detail, bar(value)));
        item.setItemMeta(meta);
        return item;
    }

    private static String colorFor(double value) {
        return value >= 75 ? "§a" : value >= 40 ? "§e" : "§c";
    }

    private static String bar(double value) {
        int filled = Math.max(0, Math.min(10, (int) Math.round(value / 10)));
        return "§a" + "|".repeat(filled) + "§8" + "|".repeat(10 - filled);
    }

    private static void addNavigation(Inventory gui) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c返回村民");
        back.setItemMeta(backMeta);
        gui.setItem(gui.getSize() - 9, back);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        close.setItemMeta(closeMeta);
        gui.setItem(gui.getSize() - 1, close);
    }

    private static boolean handleNavigation(Player player, ItemStack item, int villagerId) {
        if (item.getType() == Material.ARROW) {
            GUIManager.openVillagerInfoGUI(player, villagerId);
            return true;
        }
        if (item.getType() == Material.BARRIER) {
            player.closeInventory();
            return true;
        }
        return false;
    }

    private static String readHiddenId(ItemMeta meta, String prefix) {
        if (meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            if (line.startsWith(prefix)) return line.substring(prefix.length());
        }
        return null;
    }

    private static VillagerData ownedVillager(Player player, int villagerId) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        VillagerData villager = VillagerManager.getVillagerById(villagerId);
        if (village == null || villager == null || villager.getVillageId() != village.getId()) {
            player.sendMessage("§c该村民不属于你的村庄");
            return null;
        }
        GUIManager.setCurrentVillagerId(player, villagerId);
        return villager;
    }
}
