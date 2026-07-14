package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseManager;
import cn.popcraft.villagerpro.models.BuildingType;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillageBuilding;
import cn.popcraft.villagerpro.util.BuildingRuleEvaluator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuildingManager {
    private static final Map<String, VillageBuilding> BUILDINGS = new HashMap<>();
    private static final Set<String> DIRTY_BUILDINGS = new LinkedHashSet<>();
    private static BukkitTask auditTask;
    private static BukkitTask dirtyTask;
    private static int auditCursor;

    private BuildingManager() {
    }

    public static void initialize() {
        if (!isEnabled()) return;
        loadAll();
        auditTask = VillagerPro.getInstance().getServer().getScheduler().runTaskTimer(
                VillagerPro.getInstance(), BuildingManager::validateStaleBatch, 20L, 20L);
    }

    public static void shutdown() {
        if (auditTask != null) auditTask.cancel();
        if (dirtyTask != null) dirtyTask.cancel();
        auditTask = null;
        dirtyTask = null;
        auditCursor = 0;
        DIRTY_BUILDINGS.clear();
        BUILDINGS.clear();
    }

    public static boolean registerBuilding(Player player, BuildingType type, Block core) {
        if (!isEnabled()) {
            player.sendMessage("§c功能建筑系统未启用");
            return false;
        }
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        if (!isInsideVillage(village, core.getLocation())) {
            player.sendMessage("§c建筑核心必须位于自己的村庄保护范围内");
            return false;
        }
        Material expectedCore = getCoreMaterial(type);
        if (core.getType() != expectedCore) {
            player.sendMessage("§c" + type.getDisplayName() + "核心必须是 " + expectedCore.name());
            return false;
        }
        if (overlapsOtherBuilding(village.getId(), type, core.getLocation())) {
            player.sendMessage("§c该位置离另一座功能建筑太近，检测范围不能重叠");
            return false;
        }

        ValidationResult validation = validateStructure(type, core.getWorld(),
                core.getX(), core.getY(), core.getZ());
        String sql = DatabaseManager.upsert(
                "INSERT INTO village_buildings (village_id, building_type, world, core_x, core_y, core_z, "
                        + "level, active, status, last_validated_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new String[]{"village_id", "building_type"},
                "world", "core_x", "core_y", "core_z", "level", "active", "status", "last_validated_ms");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindBuilding(statement, village.getId(), type, core.getLocation(), validation);
            if (statement.executeUpdate() <= 0) return false;
        } catch (SQLException exception) {
            logFailure("登记功能建筑", exception);
            player.sendMessage("§c建筑登记失败，请查看控制台日志");
            return false;
        }
        try {
            VillageBuilding saved = loadBuilding(village.getId(), type);
            if (saved == null) {
                player.sendMessage("§c建筑已经保存，但无法重新读取，请查看控制台日志");
                return false;
            }
            BUILDINGS.put(key(saved), saved);
            sendValidation(player, type, validation);
            return true;
        } catch (SQLException exception) {
            logFailure("重新读取功能建筑", exception);
            player.sendMessage("§c建筑已经保存，但无法重新读取，请查看控制台日志");
            return false;
        }
    }

    public static boolean removeBuilding(Player player, BuildingType type) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return false;
        }
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM village_buildings WHERE village_id = ? AND building_type = ?")) {
            statement.setInt(1, village.getId());
            statement.setString(2, type.getId());
            if (statement.executeUpdate() <= 0) {
                player.sendMessage("§c尚未登记" + type.getDisplayName());
                return false;
            }
            BUILDINGS.remove(key(village.getId(), type));
            player.sendMessage("§a已移除" + type.getDisplayName() + "登记，世界中的方块不会被删除");
            return true;
        } catch (SQLException exception) {
            logFailure("移除功能建筑", exception);
            return false;
        }
    }

    public static void listBuildings(Player player) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return;
        }
        player.sendMessage("§6§l" + village.getName() + " - 功能建筑");
        for (BuildingType type : BuildingType.values()) {
            VillageBuilding building = BUILDINGS.get(key(village.getId(), type));
            if (building == null) {
                player.sendMessage("§7- " + type.getDisplayName() + "：未登记（核心 "
                        + getCoreMaterial(type).name() + "）");
                continue;
            }
            int effectiveLevel = getActiveLevel(village.getId(), type);
            boolean effective = effectiveLevel > 0;
            String color = effective ? "§a" : "§c";
            String displayedStatus = !effective && building.isActive()
                    ? "建筑区块未完整加载" : building.getStatus();
            player.sendMessage(color + "- " + type.getDisplayName() + "：" + displayedStatus
                    + " §7@ " + building.getCoreX() + ", " + building.getCoreY() + ", " + building.getCoreZ()
                    + (effective ? " §8[" + effectDescription(type, effectiveLevel) + "]" : ""));
        }
        player.sendMessage("§7登记时看向核心方块：/village building register <类型>");
    }

    public static void inspectBuilding(Player player, Block core) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) {
            player.sendMessage("§c请先创建村庄");
            return;
        }
        VillageBuilding building = BUILDINGS.values().stream()
                .filter(value -> value.getVillageId() == village.getId())
                .filter(value -> sameBlock(value, core.getLocation()))
                .findFirst().orElse(null);
        if (building == null) {
            player.sendMessage("§c你看向的方块不是已登记的建筑核心");
            return;
        }
        ValidationResult validation = validateAndPersist(building);
        sendValidation(player, building.getType(), validation);
    }

    public static List<VillageBuilding> getBuildings(int villageId) {
        return BUILDINGS.values().stream()
                .filter(building -> building.getVillageId() == villageId)
                .sorted(Comparator.comparing(building -> building.getType().ordinal()))
                .toList();
    }

    public static int getActiveLevel(int villageId, BuildingType type) {
        if (!isEnabled()) return 0;
        VillageBuilding building = BUILDINGS.get(key(villageId, type));
        if (building == null || !building.isActive()) return 0;
        World world = VillagerPro.getInstance().getServer().getWorld(building.getWorld());
        return world != null && areChunksLoaded(world, building.getCoreX(), building.getCoreZ())
                ? building.getLevel() : 0;
    }

    public static int getWarehouseCapacityBonus(int villageId) {
        int level = getActiveLevel(villageId, BuildingType.GRANARY);
        return level * VillagerPro.getInstance().getConfig()
                .getInt("buildings.types.granary.capacity_per_level", 100);
    }

    public static double getProductionMultiplier(int villageId) {
        int level = getActiveLevel(villageId, BuildingType.WORKSHOP);
        double bonus = VillagerPro.getInstance().getConfig()
                .getDouble("buildings.types.workshop.production_bonus_per_level", 0.10);
        return 1.0 + level * Math.max(0, bonus);
    }

    public static double getNeedsDecayMultiplier(int villageId) {
        int level = getActiveLevel(villageId, BuildingType.CLINIC);
        double reduction = VillagerPro.getInstance().getConfig()
                .getDouble("buildings.types.clinic.decay_reduction_per_level", 0.15);
        return Math.max(0.25, 1.0 - level * Math.max(0, reduction));
    }

    public static double getHealthSupplyMultiplier(int villageId) {
        int level = getActiveLevel(villageId, BuildingType.CLINIC);
        double bonus = VillagerPro.getInstance().getConfig()
                .getDouble("buildings.types.clinic.health_restore_bonus_per_level", 0.15);
        return 1.0 + level * Math.max(0, bonus);
    }

    public static void markNearbyDirty(Location changed) {
        if (!isEnabled() || changed.getWorld() == null) return;
        int radius = scanRadius() + 1;
        int vertical = scanUp() + scanDown() + 2;
        for (VillageBuilding building : BUILDINGS.values()) {
            if (!building.getWorld().equals(changed.getWorld().getName())) continue;
            if (Math.abs(building.getCoreX() - changed.getBlockX()) <= radius
                    && Math.abs(building.getCoreY() - changed.getBlockY()) <= vertical
                    && Math.abs(building.getCoreZ() - changed.getBlockZ()) <= radius) {
                DIRTY_BUILDINGS.add(key(building));
            }
        }
        scheduleDirtyValidation();
    }

    public static void markChunkDirty(World world, int chunkX, int chunkZ) {
        int radius = scanRadius();
        for (VillageBuilding building : BUILDINGS.values()) {
            if (building.getWorld().equals(world.getName())
                    && chunkX >= ((building.getCoreX() - radius) >> 4)
                    && chunkX <= ((building.getCoreX() + radius) >> 4)
                    && chunkZ >= ((building.getCoreZ() - radius) >> 4)
                    && chunkZ <= ((building.getCoreZ() + radius) >> 4)) {
                DIRTY_BUILDINGS.add(key(building));
            }
        }
        scheduleDirtyValidation();
    }

    private static void scheduleDirtyValidation() {
        if (dirtyTask != null || DIRTY_BUILDINGS.isEmpty()) return;
        long delay = Math.max(1L, VillagerPro.getInstance().getConfig()
                .getLong("buildings.revalidation_delay_ticks", 20L));
        dirtyTask = VillagerPro.getInstance().getServer().getScheduler().runTaskLater(
                VillagerPro.getInstance(), () -> {
                    Set<String> pending = new LinkedHashSet<>(DIRTY_BUILDINGS);
                    DIRTY_BUILDINGS.removeAll(pending);
                    dirtyTask = null;
                    for (String key : pending) {
                        VillageBuilding building = BUILDINGS.get(key);
                        if (building != null) validateAndPersist(building);
                    }
                    scheduleDirtyValidation();
                }, delay);
    }

    private static void validateStaleBatch() {
        List<VillageBuilding> buildings = new ArrayList<>(BUILDINGS.values());
        if (buildings.isEmpty()) return;
        buildings.sort(Comparator.comparing(BuildingManager::key));
        int batchSize = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("buildings.audit_batch_size", 2));
        long staleAfterMs = Math.max(200L, VillagerPro.getInstance().getConfig()
                .getLong("buildings.audit_interval_ticks", 2400L)) * 50L;
        for (int processed = 0; processed < Math.min(batchSize, buildings.size()); processed++) {
            if (auditCursor >= buildings.size()) auditCursor = 0;
            VillageBuilding building = buildings.get(auditCursor++);
            if (System.currentTimeMillis() - building.getLastValidatedMs() >= staleAfterMs) {
                validateAndPersist(building);
            }
        }
    }

    private static ValidationResult validateAndPersist(VillageBuilding building) {
        World world = VillagerPro.getInstance().getServer().getWorld(building.getWorld());
        ValidationResult result = validateStructure(building.getType(), world,
                building.getCoreX(), building.getCoreY(), building.getCoreZ());
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE village_buildings SET level = ?, active = ?, status = ?, last_validated_ms = ? WHERE id = ?")) {
            statement.setInt(1, result.level());
            statement.setBoolean(2, result.active());
            statement.setString(3, result.status());
            statement.setLong(4, result.validatedAt());
            statement.setInt(5, building.getId());
            if (statement.executeUpdate() > 0) {
                VillageBuilding updated = new VillageBuilding(building.getId(), building.getVillageId(),
                        building.getType(), building.getWorld(), building.getCoreX(), building.getCoreY(),
                        building.getCoreZ(), result.level(), result.active(), result.status(), result.validatedAt());
                BUILDINGS.put(key(updated), updated);
            }
        } catch (SQLException exception) {
            logFailure("更新建筑状态", exception);
        }
        return result;
    }

    private static ValidationResult validateStructure(BuildingType type, World world, int x, int y, int z) {
        long now = System.currentTimeMillis();
        if (world == null) return new ValidationResult(0, false, "世界未加载", now, Map.of());
        if (!areChunksLoaded(world, x, z)) {
            return new ValidationResult(0, false, "建筑区块未加载", now, Map.of());
        }
        if (world.getBlockAt(x, y, z).getType() != getCoreMaterial(type)) {
            return new ValidationResult(0, false, "核心方块缺失", now, Map.of());
        }

        Map<String, Integer> metrics = scanMetrics(world, x, y, z);
        int maxLevel = Math.max(1, VillagerPro.getInstance().getConfig()
                .getInt("buildings.max_level", 3));
        Map<Integer, Map<String, Integer>> requirements = getRequirements(type, maxLevel);
        int level = BuildingRuleEvaluator.calculateLevel(metrics, requirements, maxLevel);
        if (level <= 0) {
            return new ValidationResult(0, false,
                    "结构不完整：" + formatMissing(metrics, requirements.getOrDefault(1, Map.of())), now, metrics);
        }
        String status = "有效 " + level + "级";
        if (level < maxLevel && requirements.containsKey(level + 1)) {
            status += "；下级还需 " + formatMissing(metrics, requirements.get(level + 1));
        }
        return new ValidationResult(level, true, status, now, metrics);
    }

    private static Map<String, Integer> scanMetrics(World world, int coreX, int coreY, int coreZ) {
        Map<String, Integer> metrics = new LinkedHashMap<>();
        int radius = scanRadius();
        for (int x = coreX - radius; x <= coreX + radius; x++) {
            for (int y = coreY - scanDown(); y <= coreY + scanUp(); y++) {
                for (int z = coreZ - radius; z <= coreZ + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    if (material == Material.BARREL) increment(metrics, "barrels");
                    if (Tag.PLANKS.isTagged(material)) increment(metrics, "planks");
                    if (material == Material.FURNACE || material == Material.BLAST_FURNACE
                            || material == Material.SMOKER) increment(metrics, "furnaces");
                    if (material.name().endsWith("ANVIL")) increment(metrics, "anvils");
                    if (Tag.BEDS.isTagged(material)
                            && block.getBlockData() instanceof org.bukkit.block.data.type.Bed bed
                            && bed.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) {
                        increment(metrics, "beds");
                    }
                    if (material == Material.BREWING_STAND) increment(metrics, "brewing_stands");
                    if (material == Material.BOOKSHELF) increment(metrics, "bookshelves");
                }
            }
        }
        int roofColumns = 0;
        for (int x = coreX - 1; x <= coreX + 1; x++) {
            for (int z = coreZ - 1; z <= coreZ + 1; z++) {
                for (int y = coreY + 2; y <= coreY + scanUp(); y++) {
                    if (isRoofMaterial(world.getBlockAt(x, y, z).getType())) {
                        roofColumns++;
                        break;
                    }
                }
            }
        }
        metrics.put("roof_columns", roofColumns);
        return metrics;
    }

    private static boolean isRoofMaterial(Material material) {
        return material.isSolid()
                && material != Material.BARREL
                && material != Material.CRAFTING_TABLE
                && material != Material.FURNACE
                && material != Material.BLAST_FURNACE
                && material != Material.SMOKER
                && !material.name().endsWith("ANVIL")
                && !Tag.BEDS.isTagged(material)
                && material != Material.BREWING_STAND
                && material != Material.BOOKSHELF;
    }

    private static Map<Integer, Map<String, Integer>> getRequirements(BuildingType type, int maxLevel) {
        Map<Integer, Map<String, Integer>> requirements = new LinkedHashMap<>();
        for (int level = 1; level <= maxLevel; level++) {
            String path = "buildings.types." + type.getId() + ".levels." + level;
            ConfigurationSection section = VillagerPro.getInstance().getConfig().getConfigurationSection(path);
            Map<String, Integer> values = new LinkedHashMap<>();
            if (section != null) {
                for (String metric : section.getKeys(false)) {
                    values.put(metric, Math.max(0, section.getInt(metric)));
                }
            } else {
                values.putAll(defaultRequirements(type, level));
            }
            if (!values.isEmpty()) requirements.put(level, values);
        }
        return requirements;
    }

    private static Map<String, Integer> defaultRequirements(BuildingType type, int level) {
        Map<String, Integer> values = new LinkedHashMap<>();
        switch (type) {
            case GRANARY -> {
                values.put("barrels", level * 4);
                values.put("planks", level * 16);
                values.put("roof_columns", level == 1 ? 6 : level == 2 ? 8 : 9);
            }
            case WORKSHOP -> {
                values.put("furnaces", level * 2);
                values.put("anvils", level);
                values.put("planks", level * 12);
                values.put("roof_columns", level == 1 ? 6 : level == 2 ? 8 : 9);
            }
            case CLINIC -> {
                values.put("beds", level * 2);
                values.put("brewing_stands", level);
                values.put("bookshelves", level * 4);
                values.put("roof_columns", level == 1 ? 6 : level == 2 ? 8 : 9);
            }
        }
        return values;
    }

    private static String formatMissing(Map<String, Integer> metrics, Map<String, Integer> requirements) {
        Map<String, Integer> missing = BuildingRuleEvaluator.missingRequirements(metrics, requirements);
        if (missing.isEmpty()) return "无";
        return missing.entrySet().stream()
                .map(entry -> metricName(entry.getKey()) + "×" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private static String metricName(String key) {
        return switch (key) {
            case "barrels" -> "木桶";
            case "planks" -> "木板";
            case "furnaces" -> "熔炉";
            case "anvils" -> "铁砧";
            case "beds" -> "床";
            case "brewing_stands" -> "酿造台";
            case "bookshelves" -> "书架";
            case "roof_columns" -> "屋顶覆盖";
            default -> key;
        };
    }

    private static void sendValidation(Player player, BuildingType type, ValidationResult validation) {
        String color = validation.active() ? "§a" : "§e";
        player.sendMessage(color + type.getDisplayName() + "：" + validation.status());
        if (validation.active()) {
            player.sendMessage("§7当前效果：" + effectDescription(type, validation.level()));
        }
    }

    private static String effectDescription(BuildingType type, int level) {
        return switch (type) {
            case GRANARY -> "仓库容量 +" + (level * VillagerPro.getInstance().getConfig()
                    .getInt("buildings.types.granary.capacity_per_level", 100));
            case WORKSHOP -> "所有村民实际产量 +" + Math.round((getConfiguredBonus(
                    "buildings.types.workshop.production_bonus_per_level", 0.10) * level) * 100) + "%";
            case CLINIC -> "需求衰减 -" + Math.round((getConfiguredBonus(
                    "buildings.types.clinic.decay_reduction_per_level", 0.15) * level) * 100)
                    + "%、药水恢复 +" + Math.round((getConfiguredBonus(
                    "buildings.types.clinic.health_restore_bonus_per_level", 0.15) * level) * 100) + "%";
        };
    }

    private static double getConfiguredBonus(String path, double fallback) {
        return VillagerPro.getInstance().getConfig().getDouble(path, fallback);
    }

    private static boolean overlapsOtherBuilding(int villageId, BuildingType type, Location core) {
        int minimum = Math.max(scanRadius() * 2 + 1, VillagerPro.getInstance().getConfig()
                .getInt("buildings.minimum_core_distance", 7));
        return BUILDINGS.values().stream()
                .filter(building -> building.getVillageId() != villageId || building.getType() != type)
                .map(VillageBuilding::getLocation)
                .filter(location -> location != null && location.getWorld().equals(core.getWorld()))
                .anyMatch(location -> Math.abs(location.getBlockX() - core.getBlockX()) < minimum
                        && Math.abs(location.getBlockZ() - core.getBlockZ()) < minimum
                        && Math.abs(location.getBlockY() - core.getBlockY())
                        <= scanUp() + scanDown());
    }

    private static boolean isInsideVillage(Village village, Location location) {
        Location center = village.getLocation();
        if (center == null || center.getWorld() == null || location.getWorld() == null
                || !center.getWorld().equals(location.getWorld())) return false;
        double range = VillagerPro.getInstance().getConfig().getDouble("defense.protection.range", 50);
        return center.distanceSquared(location) <= range * range;
    }

    private static boolean areChunksLoaded(World world, int coreX, int coreZ) {
        int radius = scanRadius();
        int minChunkX = (coreX - radius) >> 4;
        int maxChunkX = (coreX + radius) >> 4;
        int minChunkZ = (coreZ - radius) >> 4;
        int maxChunkZ = (coreZ + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private static Material getCoreMaterial(BuildingType type) {
        String configured = VillagerPro.getInstance().getConfig()
                .getString("buildings.types." + type.getId() + ".core", type.getDefaultCore().name());
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? type.getDefaultCore() : material;
    }

    private static int scanRadius() {
        return Math.max(1, VillagerPro.getInstance().getConfig().getInt("buildings.scan_radius", 3));
    }

    private static int scanDown() {
        return Math.max(0, VillagerPro.getInstance().getConfig().getInt("buildings.scan_down", 1));
    }

    private static int scanUp() {
        return Math.max(2, VillagerPro.getInstance().getConfig().getInt("buildings.scan_up", 4));
    }

    private static void increment(Map<String, Integer> metrics, String key) {
        metrics.merge(key, 1, Integer::sum);
    }

    private static void bindBuilding(PreparedStatement statement, int villageId, BuildingType type,
                                     Location location, ValidationResult validation) throws SQLException {
        statement.setInt(1, villageId);
        statement.setString(2, type.getId());
        statement.setString(3, location.getWorld().getName());
        statement.setInt(4, location.getBlockX());
        statement.setInt(5, location.getBlockY());
        statement.setInt(6, location.getBlockZ());
        statement.setInt(7, validation.level());
        statement.setBoolean(8, validation.active());
        statement.setString(9, validation.status());
        statement.setLong(10, validation.validatedAt());
    }

    private static void loadAll() {
        BUILDINGS.clear();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, building_type, world, core_x, core_y, core_z, level, active, "
                             + "status, last_validated_ms FROM village_buildings");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                VillageBuilding building = readBuilding(resultSet);
                if (building != null) BUILDINGS.put(key(building), building);
            }
        } catch (SQLException exception) {
            logFailure("加载功能建筑", exception);
        }
    }

    private static VillageBuilding loadBuilding(int villageId, BuildingType type) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, village_id, building_type, world, core_x, core_y, core_z, level, active, "
                             + "status, last_validated_ms FROM village_buildings WHERE village_id = ? AND building_type = ?")) {
            statement.setInt(1, villageId);
            statement.setString(2, type.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readBuilding(resultSet) : null;
            }
        }
    }

    private static VillageBuilding readBuilding(ResultSet resultSet) throws SQLException {
        BuildingType type = BuildingType.fromInput(resultSet.getString("building_type"));
        if (type == null) return null;
        return new VillageBuilding(resultSet.getInt("id"), resultSet.getInt("village_id"), type,
                resultSet.getString("world"), resultSet.getInt("core_x"), resultSet.getInt("core_y"),
                resultSet.getInt("core_z"), resultSet.getInt("level"), resultSet.getBoolean("active"),
                resultSet.getString("status"), resultSet.getLong("last_validated_ms"));
    }

    private static String key(VillageBuilding building) {
        return key(building.getVillageId(), building.getType());
    }

    private static String key(int villageId, BuildingType type) {
        return villageId + ":" + type.getId();
    }

    private static boolean sameBlock(VillageBuilding building, Location location) {
        return location.getWorld() != null && building.getWorld().equals(location.getWorld().getName())
                && building.getCoreX() == location.getBlockX()
                && building.getCoreY() == location.getBlockY()
                && building.getCoreZ() == location.getBlockZ();
    }

    public static boolean isEnabled() {
        return VillagerPro.getInstance().getConfig().getBoolean("features.buildings", true)
                && VillagerPro.getInstance().getConfig().getBoolean("buildings.enabled", true);
    }

    private static void logFailure(String action, SQLException exception) {
        VillagerPro.getInstance().getLogger().warning(action + "失败: " + exception.getMessage());
    }

    private record ValidationResult(int level, boolean active, String status, long validatedAt,
                                    Map<String, Integer> metrics) {
    }
}
