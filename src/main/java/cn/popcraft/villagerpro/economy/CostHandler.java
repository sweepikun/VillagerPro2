package cn.popcraft.villagerpro.economy;

import cn.popcraft.villagerpro.VillagerPro;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CostHandler {
    
    /**
     * 检查玩家是否能支付所有成本
     * @param player 玩家
     * @param costs 成本列表
     * @return 是否能支付
     */
    public static boolean canAfford(Player player, List<CostEntry> costs) {
        List<CostEntry> normalizedCosts = CostEntry.normalize(costs);
        if (normalizedCosts == null) {
            return false;
        }
        for (CostEntry cost : normalizedCosts) {
            if (!canAfford(player, cost)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查玩家是否能支付单个成本
     * @param player 玩家
     * @param cost 成本
     * @return 是否能支付
     */
    public static boolean canAfford(Player player, CostEntry cost) {
        if (cost == null || !cost.isValid()) {
            return false;
        }
        switch (cost.getType().toLowerCase()) {
            case "vault":
                return hasVaultBalance(player, cost.getAmount());
            case "playerpoints":
                if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
                    return false; // 如果 PlayerPoints 不可用，视为无法支付此成本
                }
                return hasPlayerPoints(player, (int) cost.getAmount());
            case "itemsadder":
                // 如果 ItemsAdder 不可用，视为无法支付此成本
                if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
                    return false;
                }
                return hasItemsAdderItem(player, cost.getItem(), (int) cost.getAmount());
            case "item":
                return hasVanillaItem(player, cost.getItem(), (int) cost.getAmount());
            default:
                return false;
        }
    }
    
    /**
     * 扣除玩家的所有成本（原子操作）
     * @param player 玩家
     * @param costs 成本列表
     * @return 是否扣除成功
     */
    public static boolean deduct(Player player, List<CostEntry> costs) {
        return deductDetailed(player, costs).success();
    }

    public static DeductionResult deductDetailed(Player player, List<CostEntry> costs) {
        DeductionResult result = executeCosts(costs,
                cost -> canAfford(player, cost),
                cost -> deduct(player, cost),
                cost -> refund(player, cost));
        if (result.status() == DeductionStatus.DEDUCTION_FAILED) {
            player.sendMessage("§c扣除资源时发生错误，已扣资源已退还");
        } else if (result.status() == DeductionStatus.COMPENSATION_FAILED) {
            player.sendMessage("§c资源扣除失败且未能完整退款，请联系管理员核对流水");
            VillagerPro.getInstance().getLogger().severe(
                    "无法完整补偿玩家 " + player.getName() + " 的失败扣费，已扣="
                            + result.deductedCount() + "，已退=" + result.refundedCount());
        }
        return result;
    }

    static DeductionResult executeCosts(List<CostEntry> costs,
                                        Predicate<CostEntry> affordability,
                                        Predicate<CostEntry> deduction,
                                        Predicate<CostEntry> compensation) {
        List<CostEntry> normalizedCosts = CostEntry.normalize(costs);
        if (normalizedCosts == null) {
            return new DeductionResult(DeductionStatus.INVALID, 0, 0);
        }
        try {
            for (CostEntry cost : normalizedCosts) {
                if (!affordability.test(cost)) {
                    return new DeductionResult(DeductionStatus.UNAFFORDABLE, 0, 0);
                }
            }
        } catch (RuntimeException exception) {
            return new DeductionResult(DeductionStatus.UNAFFORDABLE, 0, 0);
        }

        List<CostEntry> deductedCosts = new ArrayList<>();
        for (CostEntry cost : normalizedCosts) {
            boolean deducted;
            try {
                deducted = deduction.test(cost);
            } catch (RuntimeException exception) {
                deducted = false;
            }
            if (!deducted) {
                int refunded = compensate(deductedCosts, compensation);
                DeductionStatus status = refunded == deductedCosts.size()
                        ? DeductionStatus.DEDUCTION_FAILED : DeductionStatus.COMPENSATION_FAILED;
                return new DeductionResult(status, deductedCosts.size(), refunded);
            }
            deductedCosts.add(cost);
        }

        return new DeductionResult(DeductionStatus.SUCCESS, deductedCosts.size(), 0);
    }

    public static boolean refund(Player player, List<CostEntry> costs) {
        List<CostEntry> normalizedCosts = CostEntry.normalize(costs);
        if (normalizedCosts == null) {
            return false;
        }

        boolean success = compensate(normalizedCosts, cost -> refund(player, cost))
                == normalizedCosts.size();
        if (!success) {
            VillagerPro.getInstance().getLogger().severe("无法完整退还玩家 " + player.getName() + " 的操作成本");
        }
        return success;
    }

    private static int compensate(List<CostEntry> costs, Predicate<CostEntry> compensation) {
        int refunded = 0;
        for (int i = costs.size() - 1; i >= 0; i--) {
            try {
                if (compensation.test(costs.get(i))) refunded++;
            } catch (RuntimeException ignored) {
                // Continue refunding the remaining currencies even if one provider is broken.
            }
        }
        return refunded;
    }

    public enum DeductionStatus {
        SUCCESS,
        INVALID,
        UNAFFORDABLE,
        DEDUCTION_FAILED,
        COMPENSATION_FAILED
    }

    public record DeductionResult(DeductionStatus status, int deductedCount, int refundedCount) {
        public boolean success() {
            return status == DeductionStatus.SUCCESS;
        }
    }

    /**
     * 扣除玩家的单个成本
     * @param player 玩家
     * @param cost 成本
     * @return 是否扣除成功
     */
    private static boolean deduct(Player player, CostEntry cost) {
        switch (cost.getType().toLowerCase()) {
            case "vault":
                return withdrawVault(player, cost.getAmount());
            case "playerpoints":
                return withdrawPlayerPoints(player, (int) cost.getAmount());
            case "itemsadder":
                // 如果 ItemsAdder 不可用，视为无法支付此成本
                if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
                    return false;
                }
                return removeItemsAdderItem(player, cost.getItem(), (int) cost.getAmount());
            case "item":
                return removeVanillaItem(player, cost.getItem(), (int) cost.getAmount());
            default:
                return false;
        }
    }

    private static boolean refund(Player player, CostEntry cost) {
        switch (cost.getType().toLowerCase()) {
            case "vault":
                Economy economy = VillagerPro.getInstance().getEconomy();
                return economy != null && economy.depositPlayer(player, cost.getAmount()).transactionSuccess();
            case "playerpoints":
                return VillagerPro.getInstance().getPlayerPointsAPI() != null
                        && VillagerPro.getInstance().getPlayerPointsAPI().getAPI()
                                .give(player.getUniqueId(), (int) cost.getAmount());
            case "itemsadder":
                return giveItemsAdderItem(player, cost.getItem(), (int) cost.getAmount());
            case "item":
                return giveVanillaItem(player, cost.getItem(), (int) cost.getAmount());
            default:
                return false;
        }
    }
    
    /**
     * 获取成本显示Lore
     * @param costs 成本列表
     * @return Lore列表
     */
    public static List<String> getDisplayLore(List<CostEntry> costs) {
        List<String> lore = new ArrayList<>();
        for (CostEntry cost : costs) {
            switch (cost.getType().toLowerCase()) {
                case "vault":
                    lore.add("§e" + (int) cost.getAmount() + "金币");
                    break;
                case "playerpoints":
                    lore.add("§e" + (int) cost.getAmount() + "点券");
                    break;
                case "itemsadder":
                    lore.add("§e" + (int) cost.getAmount() + "个" + cost.getItem());
                    break;
                case "item":
                    lore.add("§e" + (int) cost.getAmount() + "个" + cost.getItem());
                    break;
            }
        }
        return lore;
    }
    
    // 添加Vault经济支持
    private static boolean hasVaultBalance(Player player, double amount) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        return economy != null && economy.has(player, amount);
    }
    
    private static boolean withdrawVault(Player player, double amount) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }
        return false;
    }
    
    // 添加PlayerPoints点券支持
    private static boolean hasPlayerPoints(Player player, int amount) {
        return VillagerPro.getInstance().getPlayerPointsAPI() != null 
            && VillagerPro.getInstance().getPlayerPointsAPI().getAPI().look(player.getUniqueId()) >= amount;
    }
    
    private static boolean withdrawPlayerPoints(Player player, int amount) {
        return VillagerPro.getInstance().getPlayerPointsAPI() != null 
            && VillagerPro.getInstance().getPlayerPointsAPI().getAPI().take(player.getUniqueId(), amount);
    }
    
    // 添加ItemsAdder物品支持
    private static boolean hasItemsAdderItem(Player player, String itemNamespace, int amount) {
        // 检查ItemsAdder是否可用
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return false; // 如果ItemsAdder不可用，视为无法支付此成本
        }

        // 使用ItemsAdder API检查物品数量
        try {
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdderAPI");
            Method method = itemsAdderAPI.getMethod("getItemAmount", Player.class, String.class);
            int playerAmount = (int) method.invoke(null, player, itemNamespace);
            return playerAmount >= amount;
        } catch (ClassNotFoundException e) {
            VillagerPro.getInstance().getLogger().severe("未找到 ItemsAdder API 类: " + e.getMessage());
            return false;
        } catch (NoSuchMethodException e) {
            VillagerPro.getInstance().getLogger().severe("ItemsAdder API 中未找到 getItemAmount 方法: " + e.getMessage());
            return false;
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().severe("调用 ItemsAdder API 时发生错误: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean removeItemsAdderItem(Player player, String itemNamespace, int amount) {
        // 检查ItemsAdder是否可用
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return false; // 如果ItemsAdder不可用，视为扣除失败
        }
        
        // 使用ItemsAdder API移除物品
        try {
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdderAPI");
            Method method = itemsAdderAPI.getMethod("takeItem", Player.class, String.class, int.class);
            return (boolean) method.invoke(null, player, itemNamespace, amount);
        } catch (ClassNotFoundException e) {
            VillagerPro.getInstance().getLogger().severe("未找到 ItemsAdder API 类: " + e.getMessage());
            return false;
        } catch (NoSuchMethodException e) {
            VillagerPro.getInstance().getLogger().severe("ItemsAdder API 中未找到 takeItem 方法: " + e.getMessage());
            return false;
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().severe("调用 ItemsAdder API 时发生错误: " + e.getMessage());
            return false;
        }
    }

    private static boolean giveItemsAdderItem(Player player, String itemNamespace, int amount) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return false;
        }

        try {
            Class<?> itemsAdder = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Object customItem = itemsAdder.getMethod("getCustomItem", String.class).invoke(null, itemNamespace);
            if (customItem == null) {
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
            VillagerPro.getInstance().getLogger().severe("退还ItemsAdder物品失败: " + e.getMessage());
            return false;
        }
    }

    private static boolean hasVanillaItem(Player player, String itemType, int amount) {
        Material material = Material.getMaterial(itemType.toUpperCase());
        return material != null && player.getInventory().contains(material, amount);
    }

    private static boolean removeVanillaItem(Player player, String itemType, int amount) {
        Material material = Material.getMaterial(itemType.toUpperCase());
        if (material == null || !player.getInventory().contains(material, amount)) {
            return false;
        }
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material) continue;
            int removed = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - removed);
            remaining -= removed;
            if (remaining == 0) return true;
        }
        return false;
    }

    private static boolean giveVanillaItem(Player player, String itemType, int amount) {
        Material material = Material.getMaterial(itemType.toUpperCase());
        if (material == null) return false;
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(material.getMaxStackSize(), remaining);
            Map<Integer, ItemStack> leftovers = player.getInventory()
                    .addItem(new ItemStack(material, stackAmount));
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stackAmount;
        }
        return true;
    }
}
