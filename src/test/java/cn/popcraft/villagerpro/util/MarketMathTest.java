package cn.popcraft.villagerpro.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketMathTest {
    @Test
    void supplyLowersPriceAndDemandRaisesItWithinBounds() {
        double base = MarketMath.spotPrice(10, 0, 1, 0.5, 2.0);
        double supplied = MarketMath.spotPrice(10, 1, 1, 0.5, 2.0);
        double demanded = MarketMath.spotPrice(10, -1, 1, 0.5, 2.0);

        assertEquals(10, base, 0.0001);
        assertEquals(5, supplied, 0.0001);
        assertEquals(20, demanded, 0.0001);
    }

    @Test
    void spreadPreventsImmediateBuySellArbitrage() {
        double sell = MarketMath.tradePrice(10, false, 0.85, 1.15);
        double buy = MarketMath.tradePrice(10, true, 0.85, 1.15);
        assertTrue(sell < buy);
        assertEquals(8.5, sell, 0.0001);
        assertEquals(11.5, buy, 0.0001);
    }

    @Test
    void pressureMovesWithTradesAndDecaysTowardEquilibrium() {
        double afterSupply = MarketMath.pressureAfterTrade(0, 128, false, 256, 3);
        double afterDemand = MarketMath.pressureAfterTrade(afterSupply, 256, true, 256, 3);
        assertEquals(0.5, afterSupply, 0.0001);
        assertEquals(-0.5, afterDemand, 0.0001);
        assertEquals(0.25, MarketMath.decayPressure(afterSupply,
                24L * 60 * 60 * 1000, 24), 0.0001);
    }

    @Test
    void midpointSlippageKeepsLargeRoundTripUnprofitable() {
        int amount = 64;
        double pressureBefore = 0;
        double pressureAfterSell = MarketMath.pressureAfterTrade(
                pressureBefore, amount, false, 256, 3);
        double sellMidpoint = (pressureBefore + pressureAfterSell) / 2;
        double sellUnit = MarketMath.tradePrice(
                MarketMath.spotPrice(2, sellMidpoint, 1, 0.5, 2), false, 0.85, 1.15);

        double pressureAfterBuy = MarketMath.pressureAfterTrade(
                pressureAfterSell, amount, true, 256, 3);
        double buyMidpoint = (pressureAfterSell + pressureAfterBuy) / 2;
        double buyUnit = MarketMath.tradePrice(
                MarketMath.spotPrice(2, buyMidpoint, 1, 0.5, 2), true, 0.85, 1.15);

        assertEquals(pressureBefore, pressureAfterBuy, 0.0001);
        assertTrue(sellUnit < buyUnit);
    }

    @Test
    void orderRewardCannotExceedLiquidationValue() {
        assertEquals(60, MarketMath.cappedOrderReward(160, 1, 60, 1), 0.0001);
        assertEquals(66, MarketMath.cappedOrderReward(160, .85, 66, 1), 0.0001);
        assertEquals(60, MarketMath.cappedOrderReward(160, 1, 60, 2), 0.0001);
        assertEquals(30, MarketMath.cappedOrderReward(160, 1, 60, .5), 0.0001);
    }
}
