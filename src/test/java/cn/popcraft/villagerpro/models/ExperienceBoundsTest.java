package cn.popcraft.villagerpro.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperienceBoundsTest {
    @Test
    void villageExperienceDoesNotOverflowOrBecomeNegative() {
        Village village = new Village(1, UUID.randomUUID(), "test", 1,
                Integer.MAX_VALUE - 1, 0);
        village.addExperience(100);
        assertEquals(Integer.MAX_VALUE, village.getExperience());
        village.addExperience(-Integer.MAX_VALUE);
        assertEquals(0, village.getExperience());
    }

    @Test
    void villagerExperienceDoesNotOverflowOrBecomeNegative() {
        VillagerData villager = new VillagerData(1, 1, UUID.randomUUID(),
                "farmer", 1, Integer.MAX_VALUE - 1, "FREE");
        villager.addExperience(100);
        assertEquals(Integer.MAX_VALUE, villager.getExperience());
        villager.addExperience(-Integer.MAX_VALUE);
        assertEquals(0, villager.getExperience());
    }

    @Test
    void villageProsperityDoesNotOverflowOrBecomeNegative() {
        Village village = new Village(1, UUID.randomUUID(), "test", 1, 0,
                Integer.MAX_VALUE - 1);
        village.addProsperity(100);
        assertEquals(Integer.MAX_VALUE, village.getProsperity());
        village.addProsperity(-Integer.MAX_VALUE);
        assertEquals(0, village.getProsperity());
        village.setProsperity(-10);
        assertEquals(0, village.getProsperity());
        village.setExperience(-10);
        assertEquals(0, village.getExperience());
    }
}
