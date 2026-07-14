package cn.popcraft.villagerpro.economy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostHandlerTest {
    @Test
    void failedCompositeDeductionRefundsEarlierCurrenciesInReverseOrder() {
        List<String> refunded = new ArrayList<>();
        CostHandler.DeductionResult result = CostHandler.executeCosts(costs(),
                cost -> true,
                cost -> !"playerpoints".equals(cost.getType()),
                cost -> {
                    refunded.add(cost.getType());
                    return true;
                });

        assertFalse(result.success());
        assertEquals(CostHandler.DeductionStatus.DEDUCTION_FAILED, result.status());
        assertEquals(2, result.deductedCount());
        assertEquals(2, result.refundedCount());
        assertEquals(List.of("item", "vault"), refunded);
    }

    @Test
    void providerExceptionStillTriggersCompensation() {
        CostHandler.DeductionResult result = CostHandler.executeCosts(costs(),
                cost -> true,
                cost -> {
                    if ("playerpoints".equals(cost.getType())) throw new IllegalStateException("offline");
                    return true;
                },
                cost -> true);

        assertEquals(CostHandler.DeductionStatus.DEDUCTION_FAILED, result.status());
        assertEquals(2, result.refundedCount());
    }

    @Test
    void oneBrokenRefundDoesNotPreventOtherRefunds() {
        List<String> attempted = new ArrayList<>();
        CostHandler.DeductionResult result = CostHandler.executeCosts(costs(),
                cost -> true,
                cost -> !"playerpoints".equals(cost.getType()),
                cost -> {
                    attempted.add(cost.getType());
                    if ("item".equals(cost.getType())) throw new IllegalStateException("inventory failure");
                    return true;
                });

        assertEquals(CostHandler.DeductionStatus.COMPENSATION_FAILED, result.status());
        assertEquals(2, result.deductedCount());
        assertEquals(1, result.refundedCount());
        assertEquals(List.of("item", "vault"), attempted);
    }

    @Test
    void affordabilityExceptionNeverStartsDeduction() {
        List<String> deducted = new ArrayList<>();
        CostHandler.DeductionResult result = CostHandler.executeCosts(costs(),
                cost -> {
                    if ("item".equals(cost.getType())) throw new IllegalStateException("provider failure");
                    return true;
                },
                cost -> {
                    deducted.add(cost.getType());
                    return true;
                },
                cost -> true);

        assertEquals(CostHandler.DeductionStatus.UNAFFORDABLE, result.status());
        assertTrue(deducted.isEmpty());
    }

    private static List<CostEntry> costs() {
        return List.of(
                new CostEntry("vault", 100),
                new CostEntry("item", 2, "DIAMOND"),
                new CostEntry("playerpoints", 20));
    }
}
