package cn.popcraft.villagerpro.models;

public class WarehouseItem {
    private int id;
    private int villageId;
    private String itemType;
    private int amount;
    
    /**
     * 构造函数
     * @param id ID
     * @param villageId 村庄ID
     * @param itemType 物品类型
     * @param amount 数量
     */
    public WarehouseItem(int id, int villageId, String itemType, int amount) {
        this.id = id;
        this.villageId = villageId;
        this.itemType = itemType;
        this.amount = amount;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getVillageId() {
        return villageId;
    }
    
    public void setVillageId(int villageId) {
        this.villageId = villageId;
    }
    
    public String getItemType() {
        return itemType;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    
    public int getAmount() {
        return amount;
    }
    
    public void setAmount(int amount) {
        this.amount = amount;
    }
    
    /**
     * 添加物品
     * @param amount 添加的数量
     */
    public void addAmount(int amount) {
        this.amount += amount;
    }
    
    /**
     * 移除物品
     * @param amount 移除的数量
     * @return 实际移除的数量
     */
    public int removeAmount(int amount) {
        int removed = Math.min(this.amount, amount);
        this.amount -= removed;
        return removed;
    }
    
    /**
     * 检查是否有足够的物品
     * @param amount 需要的数量
     * @return 是否足够
     */
    public boolean hasEnough(int amount) {
        return this.amount >= amount;
    }
    
    /**
     * 检查是否为空
     * @return 是否为空
     */
    public boolean isEmpty() {
        return amount <= 0;
    }
    
    @Override
    public String toString() {
        return "WarehouseItem{" +
                "id=" + id +
                ", villageId=" + villageId +
                ", itemType='" + itemType + '\'' +
                ", amount=" + amount +
                '}';
    }
}