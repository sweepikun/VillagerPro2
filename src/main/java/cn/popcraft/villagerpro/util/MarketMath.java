package cn.popcraft.villagerpro.util;

public final class MarketMath {
    private MarketMath() {
    }

    public static double decayPressure(double pressure, long elapsedMillis, double halfLifeHours) {
        if (halfLifeHours <= 0 || elapsedMillis <= 0) return pressure;
        double elapsedHours = elapsedMillis / 3_600_000.0;
        return pressure * Math.pow(0.5, elapsedHours / halfLifeHours);
    }

    public static double spotPrice(double basePrice, double pressure, double sensitivity,
                                   double minimumMultiplier, double maximumMultiplier) {
        double multiplier = Math.exp(-pressure * Math.max(0, sensitivity));
        multiplier = Math.max(minimumMultiplier, Math.min(maximumMultiplier, multiplier));
        return Math.max(0, basePrice) * multiplier;
    }

    public static double tradePrice(double spotPrice, boolean buying, double sellFactor,
                                    double buyFactor) {
        return Math.max(0, spotPrice) * (buying ? Math.max(1, buyFactor)
                : Math.max(0, Math.min(1, sellFactor)));
    }

    public static double pressureAfterTrade(double pressure, int amount, boolean buying,
                                            double liquidity, double maximumPressure) {
        double delta = Math.max(0, amount) / Math.max(1, liquidity);
        double updated = pressure + (buying ? -delta : delta);
        double limit = Math.max(0, maximumPressure);
        return Math.max(-limit, Math.min(limit, updated));
    }

    public static double roundCurrency(double amount) {
        return Math.round(Math.max(0, amount) * 100.0) / 100.0;
    }

    public static double cappedOrderReward(double configuredReward, double policyMultiplier,
                                           double liquidationValue, double capMultiplier) {
        double configured = Math.max(0, configuredReward) * Math.max(0, policyMultiplier);
        double cap = Math.max(0, liquidationValue)
                * Math.max(0, Math.min(1, capMultiplier));
        return roundCurrency(Math.min(configured, cap));
    }
}
