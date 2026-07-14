package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillageUpgradeManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import cn.popcraft.villagerpro.util.GameplayMath;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 防御系统事件监听器
 * 实现村庄守护：怪物生成减少、村民受到伤害保护
 */
public class DefenseListener implements Listener {

    private final VillagerPro plugin;

    public DefenseListener() {
        this.plugin = VillagerPro.getInstance();
    }

    /**
     * 根据 mob_reduction 升级减少村庄附近怪物生成
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("features.defense", true) ||
            !plugin.getConfig().getBoolean("defense.enabled", true)) {
            return;
        }

        if (!(event.getEntity() instanceof Monster)) {
            return;
        }

        Village village = findNearestVillage(event.getLocation());
        if (village == null) {
            return;
        }

        Location center = village.getLocation();
        if (center == null || !center.getWorld().equals(event.getLocation().getWorld())) {
            return;
        }

        double protectionRange = plugin.getConfig().getDouble("defense.protection.range", 50);
        if (center.distance(event.getLocation()) > protectionRange) {
            return;
        }

        int reductionLevel = VillageUpgradeManager.getVillageUpgradeLevel(village.getId(), "mob_reduction");
        if (reductionLevel <= 0) {
            return;
        }

        double legacyReduction = GameplayMath.configuredPercentage(
                plugin.getConfig().getDouble("defense.protection.monster_reduction", 10));
        double reductionPerLevel = VillageUpgradeManager.getDoubleEffect(
                "mob_reduction", "spawn_reduction_per_level", legacyReduction);
        double reductionChance = Math.min(1.0,
                reductionLevel * Math.max(0.0, reductionPerLevel));

        if (ThreadLocalRandom.current().nextDouble() < reductionChance) {
            event.setCancelled(true);
        }
    }

    /**
     * 根据 villager_protection 设置减少村庄村民受到的伤害
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("features.defense", true) ||
            !plugin.getConfig().getBoolean("defense.enabled", true)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("defense.protection.villager_protection", true)) {
            return;
        }

        if (!(event.getEntity() instanceof Villager)) {
            return;
        }

        VillagerData villagerData = VillagerManager.getVillager(event.getEntity().getUniqueId());
        if (villagerData == null) {
            return;
        }

        Village village = VillageManager.getVillageById(villagerData.getVillageId());
        if (village == null) {
            return;
        }

        Location center = village.getLocation();
        Location entityLocation = event.getEntity().getLocation();
        if (center == null || !center.getWorld().equals(entityLocation.getWorld())) {
            return;
        }

        double protectionRange = plugin.getConfig().getDouble("defense.protection.range", 50);
        if (center.distance(entityLocation) > protectionRange) {
            return;
        }

        // 减少 50% 伤害（可在配置中扩展）
        event.setDamage(event.getDamage() * 0.5);
    }

    private Village findNearestVillage(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Village nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double checkRange = plugin.getConfig().getDouble("defense.protection.range", 50);

        for (Village village : VillageManager.getAllVillages()) {
            Location center = village.getLocation();
            if (center == null || !center.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = center.distance(location);
            if (distance <= checkRange && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = village;
            }
        }

        return nearest;
    }
}
