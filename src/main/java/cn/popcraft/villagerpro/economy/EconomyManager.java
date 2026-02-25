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
    
    public static void initialize() {
        setupEconomy();
        setupPlayerPoints();
    }
    
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
    
    private static void setupPlayerPoints() {
        if (VillagerPro.getInstance().getConfig().getBoolean("economy.use_playerpoints", true)) {
            if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
                VillagerPro.getInstance().setPlayerPointsAPI(
                    (org.black_ixx.playerpoints.PlayerPoints) Bukkit.getPluginManager().getPlugin("PlayerPoints")
                );
            }
        }
    }
    
    public static boolean isVaultAvailable() {
        return VillagerPro.getInstance().getEconomy() != null;
    }
    
    public static boolean isPlayerPointsAvailable() {
        return VillagerPro.getInstance().getPlayerPointsAPI() != null;
    }
    
    public static boolean isItemsAdderAvailable() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null 
            && VillagerPro.getInstance().getConfig().getBoolean("economy.use_itemsadder", true);
    }
    
    public static double getVaultBalance(Player player) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.getBalance(player);
        }
        return 0.0;
    }
    
    public static int getPlayerPointsBalance(Player player) {
        PlayerPoints playerPoints = VillagerPro.getInstance().getPlayerPointsAPI();
        if (playerPoints != null) {
            return playerPoints.getAPI().look(player.getUniqueId());
        }
        return 0;
    }
    
    public static boolean addVaultBalance(Player player, double amount) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.depositPlayer(player, amount).transactionSuccess();
        }
        return false;
    }
    
    public static boolean addPlayerPoints(Player player, int amount) {
        PlayerPoints playerPoints = VillagerPro.getInstance().getPlayerPointsAPI();
        if (playerPoints != null) {
            return playerPoints.getAPI().give(player.getUniqueId(), amount);
        }
        return false;
    }
    
    public static boolean removeVaultBalance(Player player, double amount) {
        Economy economy = VillagerPro.getInstance().getEconomy();
        if (economy != null) {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }
        return false;
    }
    
    public static boolean removePlayerPoints(Player player, int amount) {
        PlayerPoints playerPoints = VillagerPro.getInstance().getPlayerPointsAPI();
        if (playerPoints != null) {
            return playerPoints.getAPI().take(player.getUniqueId(), amount);
        }
        return false;
    }
    
    public static boolean hasVaultBalance(Player player, double amount) {
        return getVaultBalance(player) >= amount;
    }
    
    public static boolean hasPlayerPoints(Player player, int amount) {
        return getPlayerPointsBalance(player) >= amount;
    }
    
    public static boolean hasItemsAdderItem(Player player, String itemNamespace, int amount) {
        if (!isItemsAdderAvailable()) return false;
        
        try {
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdderAPI");
            Method method = itemsAdderAPI.getMethod("getItemAmount", Player.class, String.class);
            int playerAmount = (int) method.invoke(null, player, itemNamespace);
            return playerAmount >= amount;
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().warning("检查ItemsAdder物品失败：" + e.getMessage());
            return false;
        }
    }
    
    public static boolean withdrawItemsAdderItem(Player player, String itemNamespace, int amount) {
        if (!isItemsAdderAvailable()) return false;
        
        try {
            Class<?> itemsAdderAPI = Class.forName("dev.lone.itemsadder.api.ItemsAdderAPI");
            Method method = itemsAdderAPI.getMethod("takeItem", Player.class, String.class, int.class);
            return (boolean) method.invoke(null, player, itemNamespace, amount);
        } catch (Exception e) {
            VillagerPro.getInstance().getLogger().warning("扣除ItemsAdder物品失败：" + e.getMessage());
            return false;
        }
    }
}
