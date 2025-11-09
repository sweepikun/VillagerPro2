package cn.popcraft.villagerpro.economy;

import cn.popcraft.villagerpro.VillagerPro;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CostHandler {
    
    /**
     * 检查玩家是否能支付所有成本
     * @param player 玩家
     * @param costs 成本列表
     * @return 是否能支付
     */
    public static boolean canAfford(Player player, List<CostEntry> costs) {
        for (CostEntry cost : costs) {
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
        switch (cost.getType().toLowerCase()) {
            case "vault":
                return hasVaultBalance(player, cost.getAmount());
            case "playerpoints":
                 if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
                     return true; // 如果PlayerPoints不可用，则忽略此成本
                 }
                return hasPlayerPoints(player, (int) cost.getAmount());
            case "itemsadder":
                // 如果ItemsAdder不可用，则忽略此成本
                if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
                    return true;
                }
                return hasItemsAdderItem(player, cost.getItem(), (int) cost.getAmount());
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
        // 先检查是否能支付所有成本
        if (!canAfford(player, costs)) {
            return false;
        }
        
        // 执行扣除操作
        for (CostEntry cost : costs) {
            if (!deduct(player, cost)) {
                // 如果扣除失败，理论上不应该发生，因为我们已经检查过了
                player.sendMessage("§c扣除资源时发生错误！");
                return false;
            }
        }
        
        return true;
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
                // 如果ItemsAdder不可用，则忽略此成本
                if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
                    return true;
                }
                return removeItemsAdderItem(player, cost.getItem(), (int) cost.getAmount());
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
            return true; // 如果ItemsAdder不可用，则忽略此成本
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
            return true; // 如果ItemsAdder不可用，则忽略此成本
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
}