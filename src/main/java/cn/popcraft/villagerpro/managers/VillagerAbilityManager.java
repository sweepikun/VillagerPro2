package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

public final class VillagerAbilityManager {
    private static final NamespacedKey HEALTH_BONUS_KEY =
            new NamespacedKey(VillagerPro.getInstance(), "health_boost_applied");

    private VillagerAbilityManager() {
    }

    public static int getTotalSkillLevel(Village village, String profession, String skillId) {
        return VillagerManager.getVillagers(village.getId()).stream()
                .filter(villager -> profession.equals(villager.getProfession()))
                .mapToInt(villager -> villager.getSkills().getOrDefault(skillId, 0))
                .sum();
    }

    public static double getKnowledgeMultiplier(Village village) {
        int level = getTotalSkillLevel(village, "librarian", "knowledge_bonus");
        double bonusPerLevel = VillagerUpgradeManager.getDoubleEffect(
                "librarian", "knowledge_bonus", "experience_bonus_per_level", 0.1);
        return 1.0 + level * bonusPerLevel;
    }

    public static void applyVillageHealthBoost(Village village) {
        double healthPerLevel = VillagerUpgradeManager.getDoubleEffect(
                "priest", "health_boost", "max_health_per_level", 1.0);
        double villageBonus = getTotalSkillLevel(village, "priest", "health_boost")
                * healthPerLevel;
        for (VillagerData data : VillagerManager.getVillagers(village.getId())) {
            Villager entity = data.getEntity();
            if (entity == null || !entity.isValid()) continue;
            AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth == null) continue;

            double desiredBonus = villageBonus
                    + SpecializationManager.getEffect(data, "health_bonus");
            Double appliedBonus = entity.getPersistentDataContainer()
                    .get(HEALTH_BONUS_KEY, PersistentDataType.DOUBLE);
            double previousBonus = appliedBonus == null ? 0.0 : appliedBonus;
            if (Double.compare(previousBonus, desiredBonus) == 0) continue;

            maxHealth.setBaseValue(Math.max(1.0,
                    maxHealth.getBaseValue() - previousBonus + desiredBonus));
            entity.getPersistentDataContainer().set(
                    HEALTH_BONUS_KEY, PersistentDataType.DOUBLE, desiredBonus);
            if (entity.getHealth() > maxHealth.getValue()) {
                entity.setHealth(maxHealth.getValue());
            }
        }
    }
}
