package cn.popcraft.villagerpro.models;

public class VillageOrder {
    private final int id;
    private final int villageId;
    private final String dayKey;
    private final int slot;
    private final String itemType;
    private final int amountRequired;
    private final double rewardMoney;
    private final int rewardProsperity;
    private final double payoutMoney;
    private final String status;

    public VillageOrder(int id, int villageId, String dayKey, int slot, String itemType,
                        int amountRequired, double rewardMoney, int rewardProsperity, String status) {
        this(id, villageId, dayKey, slot, itemType, amountRequired,
                rewardMoney, rewardProsperity, 0, status);
    }

    public VillageOrder(int id, int villageId, String dayKey, int slot, String itemType,
                        int amountRequired, double rewardMoney, int rewardProsperity,
                        double payoutMoney, String status) {
        this.id = id;
        this.villageId = villageId;
        this.dayKey = dayKey;
        this.slot = slot;
        this.itemType = itemType;
        this.amountRequired = amountRequired;
        this.rewardMoney = rewardMoney;
        this.rewardProsperity = rewardProsperity;
        this.payoutMoney = payoutMoney;
        this.status = status;
    }

    public int getId() { return id; }
    public int getVillageId() { return villageId; }
    public String getDayKey() { return dayKey; }
    public int getSlot() { return slot; }
    public String getItemType() { return itemType; }
    public int getAmountRequired() { return amountRequired; }
    public double getRewardMoney() { return rewardMoney; }
    public int getRewardProsperity() { return rewardProsperity; }
    public double getPayoutMoney() { return payoutMoney; }
    public String getStatus() { return status; }
    public boolean isCompleted() { return "completed".equalsIgnoreCase(status); }
    public boolean isPending() { return "pending".equalsIgnoreCase(status); }
    public boolean isPayoutPending() { return "payout_pending".equalsIgnoreCase(status); }
    public boolean isPayoutProcessing() { return "payout_processing".equalsIgnoreCase(status); }
}
