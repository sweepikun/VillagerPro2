package cn.popcraft.villagerpro.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerManagerTest {
    @Test
    void entityNameAlwaysCarriesPersistentVillagerIdAndMode() {
        assertEquals("§a农民 §7(ID: 42) §8[自由]",
                VillagerManager.formatEntityDisplayName("农民", 42, "FREE"));
        assertEquals("§a农民 §7(ID: 42) §8[跟随]",
                VillagerManager.formatEntityDisplayName("农民", 42, "FOLLOW"));
        assertEquals("§a农民 §7(ID: 42) §8[停留]",
                VillagerManager.formatEntityDisplayName("农民", 42, "STAY"));
        assertEquals("§a农民 §7(ID: 42) §8[自由]",
                VillagerManager.formatEntityDisplayName("农民", 42, null));
    }
}
