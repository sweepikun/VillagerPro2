package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.database.OperationTransactions;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.models.VillagerSpecialization;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SpecializationManager {
    private static final Map<Integer, Optional<VillagerSpecialization>> SPECIALIZATIONS =
            new ConcurrentHashMap<>();

    private SpecializationManager() {
    }

    public static VillagerSpecialization getSpecialization(int villagerId) {
        Optional<VillagerSpecialization> cached = SPECIALIZATIONS.get(villagerId);
        if (cached != null) return cached.orElse(null);
        boolean loaded = false;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT villager_id, branch_id, level FROM villager_specializations " +
                             "WHERE villager_id = ?")) {
            statement.setInt(1, villagerId);
            ResultSet resultSet = statement.executeQuery();
            loaded = true;
            if (resultSet.next()) {
                VillagerSpecialization specialization = new VillagerSpecialization(resultSet.getInt("villager_id"),
                        resultSet.getString("branch_id"), resultSet.getInt("level"));
                SPECIALIZATIONS.put(villagerId, Optional.of(specialization));
                return specialization;
            }
        } catch (SQLException e) {
            logFailure("读取职业专精", e);
        }
        if (loaded) SPECIALIZATIONS.put(villagerId, Optional.empty());
        return null;
    }

    public static void removeSpecializationCache(int villagerId) {
        SPECIALIZATIONS.remove(villagerId);
    }

    public static void shutdown() {
        SPECIALIZATIONS.clear();
    }

    public static List<String> getBranchIds(String profession) {
        if (!VillagerPro.getInstance().getConfig().getBoolean("specializations.enabled", true)) {
            return Collections.emptyList();
        }
        ConfigurationSection section = VillagerPro.getInstance().getConfig()
                .getConfigurationSection("specializations.professions." + profession);
        return section == null ? Collections.emptyList() : new ArrayList<>(section.getKeys(false));
    }

    public static String getBranchName(String profession, String branchId) {
        return VillagerPro.getInstance().getConfig().getString(
                branchPath(profession, branchId) + ".name", branchId);
    }

    public static String getBranchDescription(String profession, String branchId) {
        return VillagerPro.getInstance().getConfig().getString(
                branchPath(profession, branchId) + ".description", "");
    }

    public static Material getBranchIcon(String profession, String branchId) {
        Material material = Material.getMaterial(VillagerPro.getInstance().getConfig().getString(
                branchPath(profession, branchId) + ".icon", "NETHER_STAR"));
        return material == null ? Material.NETHER_STAR : material;
    }

    public static int getMaxLevel() {
        return Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("specializations.max_level", 3));
    }

    public static int getRequiredVillagerLevel() {
        return Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("specializations.required_villager_level", 3));
    }

    public static double getEffect(VillagerData villager, String effectId) {
        if (!VillagerPro.getInstance().getConfig().getBoolean("specializations.enabled", true)) {
            return 0.0;
        }
        VillagerSpecialization specialization = getSpecialization(villager.getId());
        if (specialization == null) return 0.0;
        double perLevel = VillagerPro.getInstance().getConfig().getDouble(
                branchPath(villager.getProfession(), specialization.getBranchId())
                        + ".effects." + effectId, 0.0);
        return perLevel * specialization.getLevel();
    }

    public static String getRareItem(VillagerData villager) {
        if (!VillagerPro.getInstance().getConfig().getBoolean("specializations.enabled", true)) {
            return null;
        }
        VillagerSpecialization specialization = getSpecialization(villager.getId());
        if (specialization == null) return null;
        return VillagerPro.getInstance().getConfig().getString(
                branchPath(villager.getProfession(), specialization.getBranchId())
                        + ".effects.rare_item");
    }

    public static boolean selectOrUpgrade(Player player, VillagerData villager, String branchId) {
        if (!getBranchIds(villager.getProfession()).contains(branchId)) {
            player.sendMessage("§c这个职业没有该专精分支");
            return false;
        }
        if (villager.getLevel() < getRequiredVillagerLevel()) {
            player.sendMessage("§c村民达到 " + getRequiredVillagerLevel() + " 级后才能选择专精");
            return false;
        }
        VillagerSpecialization current = getSpecialization(villager.getId());
        if (current != null && !current.getBranchId().equals(branchId)) {
            player.sendMessage("§c已选择其他互斥分支，请先洗点");
            return false;
        }
        int nextLevel = current == null ? 1 : current.getLevel() + 1;
        if (nextLevel > getMaxLevel()) {
            player.sendMessage("§c该专精已经满级");
            return false;
        }
        List<CostEntry> costs = getSelectionCosts(nextLevel);
        if (!CostHandler.deduct(player, costs)) {
            player.sendMessage("§c资源不足，无法提升专精");
            return false;
        }
        try (Connection connection = DatabaseManager.getConnection()) {
            if (OperationTransactions.upgradeSpecialization(connection,
                    DatabaseManager.getDialect(), villager.getId(), branchId,
                    nextLevel - 1, getMaxLevel())) {
                SPECIALIZATIONS.put(villager.getId(), Optional.of(new VillagerSpecialization(
                        villager.getId(), branchId, nextLevel)));
                player.sendMessage("§a专精已提升为 " + getBranchName(
                        villager.getProfession(), branchId) + " " + nextLevel + "级");
                return true;
            }
        } catch (SQLException e) {
            logFailure("保存职业专精", e);
        }
        boolean refunded = CostHandler.refund(player, costs);
        player.sendMessage(refunded ? "§c保存专精失败，费用已退回"
                : "§c保存专精失败且费用未完整退回，请联系管理员");
        return false;
    }

    public static boolean respec(Player player, VillagerData villager) {
        VillagerSpecialization current = getSpecialization(villager.getId());
        if (current == null) {
            player.sendMessage("§c该村民尚未选择专精");
            return false;
        }
        List<CostEntry> costs = Collections.singletonList(new CostEntry("vault",
                Math.max(1, VillagerPro.getInstance().getConfig()
                        .getDouble("specializations.respec_cost", 500))));
        if (!CostHandler.deduct(player, costs)) {
            player.sendMessage("§c资源不足，无法洗点");
            return false;
        }
        try (Connection connection = DatabaseManager.getConnection()) {
            if (OperationTransactions.removeSpecialization(connection, villager.getId(),
                    current.getBranchId(), current.getLevel())) {
                SPECIALIZATIONS.put(villager.getId(), Optional.empty());
                player.sendMessage("§a专精已经重置，可以重新选择分支");
                return true;
            }
        } catch (SQLException e) {
            logFailure("重置职业专精", e);
        }
        boolean refunded = CostHandler.refund(player, costs);
        player.sendMessage(refunded ? "§c洗点失败，费用已退回"
                : "§c洗点失败且费用未完整退回，请联系管理员");
        return false;
    }

    public static List<CostEntry> getSelectionCosts(int nextLevel) {
        double base = nextLevel <= 1
                ? VillagerPro.getInstance().getConfig().getDouble(
                        "specializations.selection_cost", 300)
                : VillagerPro.getInstance().getConfig().getDouble(
                        "specializations.upgrade_base_cost", 250) * nextLevel;
        return Collections.singletonList(new CostEntry("vault", Math.max(1, base)));
    }

    private static String branchPath(String profession, String branchId) {
        return "specializations.professions." + profession + "." + branchId;
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
