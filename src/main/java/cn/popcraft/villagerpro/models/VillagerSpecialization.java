package cn.popcraft.villagerpro.models;

public class VillagerSpecialization {
    private final int villagerId;
    private final String branchId;
    private final int level;

    public VillagerSpecialization(int villagerId, String branchId, int level) {
        this.villagerId = villagerId;
        this.branchId = branchId;
        this.level = level;
    }

    public int getVillagerId() { return villagerId; }
    public String getBranchId() { return branchId; }
    public int getLevel() { return level; }
}
