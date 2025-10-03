package cn.popcraft.villagerpro.economy;

import cn.popcraft.villagerpro.VillagerPro;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public class EconomyManager {
    
    /**
     * 初始化经济系统
     */
    public static void initialize() {
        setupEconomy();
        setupPlayerPoints();
    }
    
    /**
     * 设置Vault经济系统
     */
    private static void setupEconomy() {
        if (VillagerPro.getInstance().getConfig().getBoolean("economy.use_vault", true)) {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    VillagerPro.getInstance().setEconomy(rsp.getProvider());
                }
            }
        }
    }
    
    /**
     * 设置PlayerPoints系统
     */
    private static void setupPlayerPoints() {
        if (VillagerPro.getInstance().getConfig().getBoolean("economy.use_playerpoints", true)) {
            if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
                VillagerPro.getInstance().setPlayerPointsAPI(
                    (org.black_ixx.playerpoints.PlayerPoints) Bukkit.getPluginManager().getPlugin("PlayerPoints")
                );
            }
        }
    }
    
    /**
     * 检查Vault经济系统是否可用
     * @return 是否可用
     */
    public static boolean isVaultAvailable() {
        return VillagerPro.getInstance().getEconomy() != null;
    }
    
    /**
     * 检查PlayerPoints系统是否可用
     * @return 是否可用
     */
    public static boolean isPlayerPointsAvailable() {
        return VillagerPro.getInstance().getPlayerPointsAPI() != null;
    }
    
    /**
     * 检查ItemsAdder系统是否可用
     * @return 是否可用
     */
    public static boolean isItemsAdderAvailable() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null 
            && VillagerPro.getInstance().getConfig().getBoolean("economy.use_itemsadder", true);
    }
    
    /**
     * 获取玩家的Vault余额
     * @param player 玩家
     * @return 余额
     */
    public static double getVaultBalance(Player player) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.getBalance(player);
        }
        return 0.0;
    }
    
    /**
     * 获取玩家的PlayerPoints余额
     * @param player 玩家
     * @return 余额
     */
    public static int getPlayerPointsBalance(Player player) {
        PlayerPoints playerPoints = VillagerPro.getInstance().getPlayerPointsAPI();
        if (playerPoints != null) {
            return playerPoints.getAPI().look(player.getUniqueId());
        }
        return 0;
    }
    
    /**
     * 给玩家添加Vault货币
     * @param player 玩家
     * @param amount 数量
     * @return 是否成功
     */
    public static boolean addVaultBalance(Player player, double amount) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.depositPlayer(player, amount).transactionSuccess();
        }
        return false;
    }
    
    /**
     * 给玩家添加PlayerPoints点券
     * @param player 玩家
     * @param amount 数量
     * @return 是否成功
     */
    public static boolean addPlayerPoints(Player player, int amount) {
        PlayerPoints playerPoints = VillagerPro.getInstance().getPlayerPointsAPI();
        if (playerPoints != null) {
            return playerPoints.getAPI().give(player.getUniqueId(), amount);
        }
        return false;
    }
    
    /**
     * 从玩家扣除Vault货币
     * @param player 玩家
     * @param amount 数量
     * @return 是否成功
     */
    public static boolean removeVaultBalance(Player player, double amount) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }
        return false;
    }
    
    /**
     * 从玩家扣除PlayerPoints点券
     * @param player 玩家
     * @param amount 数量
     * @return 是否成功
     */
    public static boolean removePlayerPoints(Player player, int amount) {
        PlayerPoints playerPoints = VillagerPro.getInstance().getPlayerPointsAPI();
        if (playerPoints != null) {
            return playerPoints.getAPI().take(player.getUniqueId(), amount);
        }
        return false;
    }
    
    /**
     * 检查玩家是否有足够的Vault货币
     * @param player 玩家
     * @param amount 金额
     * @return 是否有足够的货币
     */
    public static boolean hasVaultBalance(Player player, double amount) {
        return getVaultBalance(player) >= amount;
    }
    
    /**
     * 检查玩家是否有足够的PlayerPoints点券
     * @param player 玩家
     * @param amount 点券数量
     * @return 是否有足够的点券
     */
    public static boolean hasPlayerPoints(Player player, int amount) {
        return getPlayerPointsBalance(player) >= amount;
    }
    
    /**
     * 检查玩家是否有足够的ItemsAdder物品
     * @param player 玩家
     * @param itemNamespace 物品命名空间
     * @param amount 数量
     * @return 是否有足够的物品
     */
    public static boolean hasItemsAdderItem(Player player, String itemNamespace, int amount) {
        if (!isItemsAdderAvailable()) return false;
        
        try {
            Class<?> itemsAdderAPI = Class.forName("io.th0rgal.itemsadder.api.ItemsAdderAPI");
            Method method = itemsAdderAPI.getMethod("getItemAmount", Player.class, String.class);
            int playerAmount = (int) method.invoke(null, player, itemNamespace);
            return playerAmount >= amount;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 从玩家扣除ItemsAdder物品
     * @param player 玩家
     * @param itemNamespace 物品命名空间
     * @param amount 数量
     * @return 是否扣除成功
     */
    public static boolean withdrawItemsAdderItem(Player player, String itemNamespace, int amount) {
        if (!isItemsAdderAvailable()) return false;
        
        try {
            Class<?> itemsAdderAPI = Class.forName("io.th0rgal.itemsadder.api.ItemsAdderAPI");
            Method method = itemsAdderAPI.getMethod("takeItem", Player.class, String.class, int.class);
            return (boolean) method.invoke(null, player, itemNamespace, amount);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}