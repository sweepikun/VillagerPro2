package cn.popcraft.villagerpro.economy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CostEntryTest {
    @Test
    void combinesCostsUsingTheSameCurrency() {
        List<CostEntry> normalized = CostEntry.normalize(List.of(
                new CostEntry("vault", 500),
                new CostEntry("vault", 100)));

        assertEquals(1, normalized.size());
        assertEquals(600.0, normalized.get(0).getAmount());
    }

    @Test
    void keepsDifferentCustomItemsSeparate() {
        List<CostEntry> normalized = CostEntry.normalize(List.of(
                new CostEntry("itemsadder", 2, "vp:first"),
                new CostEntry("itemsadder", 3, "vp:second")));

        assertEquals(2, normalized.size());
    }

    @Test
    void combinesMatchingVanillaItemCosts() {
        List<CostEntry> normalized = CostEntry.normalize(List.of(
                new CostEntry("item", 2, "GLASS"),
                new CostEntry("item", 3, "GLASS")));

        assertEquals(1, normalized.size());
        assertEquals("GLASS", normalized.get(0).getItem());
        assertEquals(5.0, normalized.get(0).getAmount());
    }

    @Test
    void rejectsInvalidAmounts() {
        assertNull(CostEntry.normalize(List.of(new CostEntry("vault", Double.NaN))));
        assertNull(CostEntry.normalize(List.of(new CostEntry("vault", 0))));
    }

    @Test
    void acceptsCurrencyTypeCaseInsensitively() {
        List<CostEntry> normalized = CostEntry.normalize(List.of(new CostEntry("Vault", 10)));
        assertEquals("vault", normalized.get(0).getType());
    }
}
