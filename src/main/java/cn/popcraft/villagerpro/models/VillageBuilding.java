package cn.popcraft.villagerpro.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class VillageBuilding {
    private final int id;
    private final int villageId;
    private final BuildingType type;
    private final String world;
    private final int coreX;
    private final int coreY;
    private final int coreZ;
    private final int level;
    private final boolean active;
    private final String status;
    private final long lastValidatedMs;

    public VillageBuilding(int id, int villageId, BuildingType type, String world,
                           int coreX, int coreY, int coreZ, int level, boolean active,
                           String status, long lastValidatedMs) {
        this.id = id;
        this.villageId = villageId;
        this.type = type;
        this.world = world;
        this.coreX = coreX;
        this.coreY = coreY;
        this.coreZ = coreZ;
        this.level = level;
        this.active = active;
        this.status = status;
        this.lastValidatedMs = lastValidatedMs;
    }

    public int getId() { return id; }
    public int getVillageId() { return villageId; }
    public BuildingType getType() { return type; }
    public String getWorld() { return world; }
    public int getCoreX() { return coreX; }
    public int getCoreY() { return coreY; }
    public int getCoreZ() { return coreZ; }
    public int getLevel() { return level; }
    public boolean isActive() { return active; }
    public String getStatus() { return status; }
    public long getLastValidatedMs() { return lastValidatedMs; }

    public Location getLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, coreX, coreY, coreZ);
    }
}
