package cn.popcraft.villagerpro.models;

public class VillagerWorkstation {
    private final int villagerId;
    private final String world;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final String material;
    private final int level;

    public VillagerWorkstation(int villagerId, String world, int blockX, int blockY,
                               int blockZ, String material, int level) {
        this.villagerId = villagerId;
        this.world = world;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.material = material;
        this.level = level;
    }

    public int getVillagerId() { return villagerId; }
    public String getWorld() { return world; }
    public int getBlockX() { return blockX; }
    public int getBlockY() { return blockY; }
    public int getBlockZ() { return blockZ; }
    public String getMaterial() { return material; }
    public int getLevel() { return level; }
}
