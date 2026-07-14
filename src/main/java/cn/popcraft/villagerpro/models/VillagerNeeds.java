package cn.popcraft.villagerpro.models;

public class VillagerNeeds {
    private final int villagerId;
    private final double hunger;
    private final double comfort;
    private final double health;
    private final long lastUpdatedMs;
    private final String lastConsumed;

    public VillagerNeeds(int villagerId, double hunger, double comfort, double health,
                         long lastUpdatedMs, String lastConsumed) {
        this.villagerId = villagerId;
        this.hunger = hunger;
        this.comfort = comfort;
        this.health = health;
        this.lastUpdatedMs = lastUpdatedMs;
        this.lastConsumed = lastConsumed;
    }

    public int getVillagerId() { return villagerId; }
    public double getHunger() { return hunger; }
    public double getComfort() { return comfort; }
    public double getHealth() { return health; }
    public long getLastUpdatedMs() { return lastUpdatedMs; }
    public String getLastConsumed() { return lastConsumed; }
    public double getLowestValue() { return Math.min(hunger, Math.min(comfort, health)); }
}
