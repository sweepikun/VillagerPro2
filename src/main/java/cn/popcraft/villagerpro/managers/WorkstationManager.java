package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.economy.CostEntry;
import cn.popcraft.villagerpro.economy.CostHandler;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.models.VillagerWorkstation;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkstationManager {
    private static final Map<UUID, Integer> pendingBindings = new ConcurrentHashMap<>();
    private static final Map<Integer, Optional<VillagerWorkstation>> WORKSTATIONS =
            new ConcurrentHashMap<>();

    private WorkstationManager() {
    }

    public static VillagerWorkstation getWorkstation(int villagerId) {
        Optional<VillagerWorkstation> cached = WORKSTATIONS.get(villagerId);
        if (cached != null) return cached.orElse(null);
        boolean loaded = false;
        String sql = "SELECT villager_id, world, block_x, block_y, block_z, material, level " +
                "FROM villager_workstations WHERE villager_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villagerId);
            ResultSet resultSet = statement.executeQuery();
            loaded = true;
            if (resultSet.next()) {
                VillagerWorkstation workstation = new VillagerWorkstation(resultSet.getInt("villager_id"),
                        resultSet.getString("world"), resultSet.getInt("block_x"),
                        resultSet.getInt("block_y"), resultSet.getInt("block_z"),
                        resultSet.getString("material"), resultSet.getInt("level"));
                WORKSTATIONS.put(villagerId, Optional.of(workstation));
                return workstation;
            }
        } catch (SQLException e) {
            logFailure("读取工作站", e);
        }
        if (loaded) WORKSTATIONS.put(villagerId, Optional.empty());
        return null;
    }

    public static void removeWorkstationCache(int villagerId) {
        WORKSTATIONS.remove(villagerId);
    }

    public static void shutdown() {
        pendingBindings.clear();
        WORKSTATIONS.clear();
    }

    public static void beginBinding(Player player, VillagerData villager) {
        if (!isEnabled()) {
            player.sendMessage("§c实体工作站功能当前未启用");
            return;
        }
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null || villager == null || villager.getVillageId() != village.getId()) {
            player.sendMessage("§c该村民不属于你的村庄");
            return;
        }
        pendingBindings.put(player.getUniqueId(), villager.getId());
        player.closeInventory();
        player.sendMessage("§e请右键一个 " + getExpectedMaterial(villager.getProfession()).name()
                + " 作为该村民的工作站，离线可取消本次选择");
    }

    public static boolean handleBlockSelection(Player player, Block block) {
        Integer villagerId = pendingBindings.get(player.getUniqueId());
        if (villagerId == null) return false;
        VillagerData villager = VillagerManager.getVillagerById(villagerId);
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (villager == null || village == null || villager.getVillageId() != village.getId()) {
            pendingBindings.remove(player.getUniqueId());
            player.sendMessage("§c绑定已取消：村民数据无效");
            return true;
        }
        Material expected = getExpectedMaterial(villager.getProfession());
        if (block.getType() != expected) {
            player.sendMessage("§c该职业需要 " + expected.name() + "，请重新选择");
            return true;
        }
        if (village.getLocation() == null) {
            village.setLocation(player.getLocation());
            if (!VillageManager.updateVillage(village)) {
                player.sendMessage("§c无法初始化旧村庄的中心位置，请重试");
                return true;
            }
        }
        if (village.getLocation() == null
                || !village.getLocation().getWorld().equals(block.getWorld())
                || village.getLocation().distance(block.getLocation())
                > VillagerPro.getInstance().getConfig().getDouble("defense.protection.range", 50)) {
            player.sendMessage("§c工作站必须位于村庄保护范围内");
            return true;
        }
        VillagerWorkstation current = getWorkstation(villagerId);
        int level = current == null ? 1 : current.getLevel();
        String sql = DatabaseManager.upsert("INSERT INTO villager_workstations " +
                "(villager_id, world, block_x, block_y, block_z, material, level) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)", new String[]{"villager_id"},
                "world", "block_x", "block_y", "block_z", "material");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, villagerId);
            statement.setString(2, block.getWorld().getName());
            statement.setInt(3, block.getX());
            statement.setInt(4, block.getY());
            statement.setInt(5, block.getZ());
            statement.setString(6, block.getType().name());
            statement.setInt(7, level);
            if (statement.executeUpdate() > 0) {
                pendingBindings.remove(player.getUniqueId());
                WORKSTATIONS.remove(villagerId);
                player.sendMessage("§a已绑定工作站 " + block.getType().name()
                        + " @ " + block.getX() + ", " + block.getY() + ", " + block.getZ());
                return true;
            }
        } catch (SQLException e) {
            player.sendMessage("§c该方块可能已被其他村民绑定");
            logFailure("绑定工作站", e);
        }
        return true;
    }

    public static String getOperationalIssue(VillagerData villager) {
        if (!isEnabled()) return null;
        VillagerWorkstation workstation = getWorkstation(villager.getId());
        if (workstation == null) return "未绑定实体工作站";
        if (villager.getEntity() == null || !villager.getEntity().isValid()) {
            return "村民实体不存在";
        }
        World world = Bukkit.getWorld(workstation.getWorld());
        if (world == null) return "工作站所在世界未加载";
        if (!world.isChunkLoaded(workstation.getBlockX() >> 4, workstation.getBlockZ() >> 4)) {
            return "工作站区块未加载";
        }
        Block block = world.getBlockAt(
                workstation.getBlockX(), workstation.getBlockY(), workstation.getBlockZ());
        if (!block.getType().name().equals(workstation.getMaterial())
                || block.getType() != getExpectedMaterial(villager.getProfession())) {
            return "工作站方块已被移除或替换";
        }
        if (!villager.getEntity().getWorld().equals(world)) return "村民与工作站不在同一世界";
        double range = VillagerPro.getInstance().getConfig()
                .getDouble("workstations.active_range", 8);
        if (villager.getEntity().getLocation().distance(block.getLocation().add(0.5, 0.5, 0.5)) > range) {
            return "村民距离工作站过远";
        }
        return null;
    }

    public static double getProductionMultiplier(VillagerData villager) {
        if (!isEnabled()) return 1.0;
        VillagerWorkstation workstation = getWorkstation(villager.getId());
        if (workstation == null || getOperationalIssue(villager) != null) return 1.0;
        double bonus = VillagerPro.getInstance().getConfig()
                .getDouble("workstations.bonus_per_level", 0.15);
        return GameplayMath.levelMultiplier(workstation.getLevel(), bonus);
    }

    public static VillagerData findOperationalVillager(int villageId, String profession) {
        return VillagerManager.getVillagers(villageId).stream()
                .filter(villager -> profession.equals(villager.getProfession()))
                .filter(villager -> getOperationalIssue(villager) == null)
                .findFirst().orElse(null);
    }

    public static boolean upgrade(Player player, VillagerData villager) {
        VillagerWorkstation workstation = getWorkstation(villager.getId());
        if (workstation == null) {
            player.sendMessage("§c请先绑定工作站");
            return false;
        }
        int maxLevel = getMaxLevel();
        int nextLevel = workstation.getLevel() + 1;
        if (nextLevel > maxLevel) {
            player.sendMessage("§c工作站已经满级");
            return false;
        }
        List<CostEntry> costs = getUpgradeCosts(nextLevel);
        if (!CostHandler.deduct(player, costs)) {
            player.sendMessage("§c升级工作站所需资源不足");
            return false;
        }
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE villager_workstations SET level = ? WHERE villager_id = ? AND level = ?")) {
            statement.setInt(1, nextLevel);
            statement.setInt(2, villager.getId());
            statement.setInt(3, workstation.getLevel());
            if (statement.executeUpdate() == 1) {
                WORKSTATIONS.remove(villager.getId());
                player.sendMessage("§a工作站已升级至 " + nextLevel + " 级");
                return true;
            }
        } catch (SQLException e) {
            logFailure("升级工作站", e);
        }
        boolean refunded = CostHandler.refund(player, costs);
        player.sendMessage(refunded ? "§c升级失败，费用已退回"
                : "§c升级失败且费用未完整退回，请联系管理员");
        return false;
    }

    public static List<CostEntry> getUpgradeCosts(int nextLevel) {
        List<CostEntry> costs = new ArrayList<>();
        String path = "workstations.upgrade_costs." + nextLevel;
        double money = VillagerPro.getInstance().getConfig().getDouble(path + ".vault", 250 * nextLevel);
        if (money > 0) costs.add(new CostEntry("vault", money));
        String item = VillagerPro.getInstance().getConfig().getString(path + ".item");
        int amount = VillagerPro.getInstance().getConfig().getInt(path + ".amount", 0);
        if (item != null && amount > 0) costs.add(new CostEntry("item", amount, item));
        return costs;
    }

    public static int getMaxLevel() {
        return Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("workstations.max_level", 3));
    }

    public static Material getExpectedMaterial(String profession) {
        Material material = Material.getMaterial(VillagerPro.getInstance().getConfig().getString(
                "workstations.materials." + profession, defaultMaterial(profession).name()));
        return material == null ? defaultMaterial(profession) : material;
    }

    public static void cancelBinding(Player player) {
        pendingBindings.remove(player.getUniqueId());
    }

    private static Material defaultMaterial(String profession) {
        switch (profession) {
            case "farmer": return Material.COMPOSTER;
            case "fisherman": return Material.BARREL;
            case "shepherd":
            case "weaver": return Material.LOOM;
            case "librarian": return Material.LECTERN;
            case "priest": return Material.BREWING_STAND;
            case "cartographer": return Material.CARTOGRAPHY_TABLE;
            case "baker": return Material.SMOKER;
            default: return Material.CRAFTING_TABLE;
        }
    }

    public static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.workstations", true)
                && VillagerPro.getInstance().getConfig().getBoolean("workstations.enabled", true);
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }
}
