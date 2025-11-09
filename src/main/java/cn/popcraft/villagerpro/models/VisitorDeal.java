package cn.popcraft.villagerpro.models;

import java.sql.Timestamp;

/**
 * 访客交易/委托数据模型
 * 记录访客与玩家之间的交易和委托信息
 */
public class VisitorDeal {
    
    private int id;
    private int visitorId;
    private String dealType; // 'purchase', 'commission'
    private String itemType;
    private int amountRequired;
    private int amountDelivered;
    private double price; // 购买价格（如果是购买）
    private String rewardType; // 奖励类型
    private int rewardAmount;
    private String rewardItem; // 奖励物品
    private String playerName;
    private String status; // 'pending', 'completed', 'cancelled', 'expired'
    private Timestamp createdAt;
    private Timestamp completedAt;
    private Timestamp expiresAt;
    
    public VisitorDeal(int id, int visitorId, String dealType, String itemType, 
                      int amountRequired, double price, String rewardType, 
                      int rewardAmount, String rewardItem, String playerName, 
                      Timestamp createdAt, Timestamp expiresAt) {
        this.id = id;
        this.visitorId = visitorId;
        this.dealType = dealType;
        this.itemType = itemType;
        this.amountRequired = amountRequired;
        this.amountDelivered = 0;
        this.price = price;
        this.rewardType = rewardType;
        this.rewardAmount = rewardAmount;
        this.rewardItem = rewardItem;
        this.playerName = playerName;
        this.status = "pending";
        this.createdAt = createdAt;
        this.completedAt = null;
        this.expiresAt = expiresAt;
    }
    
    /**
     * 检查交易是否完成
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }
    
    /**
     * 检查交易是否过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt.getTime();
    }
    
    /**
     * 检查是否可以交付物品
     */
    public boolean canDeliver() {
        return !isExpired() && !isCompleted() && amountDelivered < amountRequired;
    }
    
    /**
     * 交付物品
     */
    public boolean deliverItem(int amount) {
        if (!canDeliver()) return false;
        
        amountDelivered += amount;
        
        // 检查是否完成交付
        if (amountDelivered >= amountRequired) {
            complete();
        }
        
        return true;
    }
    
    /**
     * 完成交易
     */
    public void complete() {
        this.status = "completed";
        this.completedAt = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * 取消交易
     */
    public void cancel() {
        this.status = "cancelled";
    }
    
    /**
     * 过期交易
     */
    public void expire() {
        this.status = "expired";
    }
    
    /**
     * 获取交付进度百分比
     */
    public double getProgress() {
        if (amountRequired == 0) return 0;
        return (double) amountDelivered / amountRequired;
    }
    
    /**
     * 获取剩余需要交付的数量
     */
    public int getRemainingAmount() {
        return Math.max(0, amountRequired - amountDelivered);
    }
    
    /**
     * 获取剩余时间（秒）
     */
    public long getRemainingTimeInSeconds() {
        return Math.max(0, (expiresAt.getTime() - System.currentTimeMillis()) / 1000);
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getVisitorId() {
        return visitorId;
    }
    
    public void setVisitorId(int visitorId) {
        this.visitorId = visitorId;
    }
    
    public String getDealType() {
        return dealType;
    }
    
    public void setDealType(String dealType) {
        this.dealType = dealType;
    }
    
    public String getItemType() {
        return itemType;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    
    public int getAmountRequired() {
        return amountRequired;
    }
    
    public void setAmountRequired(int amountRequired) {
        this.amountRequired = amountRequired;
    }
    
    public int getAmountDelivered() {
        return amountDelivered;
    }
    
    public void setAmountDelivered(int amountDelivered) {
        this.amountDelivered = amountDelivered;
    }
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public String getRewardType() {
        return rewardType;
    }
    
    public void setRewardType(String rewardType) {
        this.rewardType = rewardType;
    }
    
    public int getRewardAmount() {
        return rewardAmount;
    }
    
    public void setRewardAmount(int rewardAmount) {
        this.rewardAmount = rewardAmount;
    }
    
    public String getRewardItem() {
        return rewardItem;
    }
    
    public void setRewardItem(String rewardItem) {
        this.rewardItem = rewardItem;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }
    
    public Timestamp getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    @Override
    public String toString() {
        return "VisitorDeal{" +
                "id=" + id +
                ", visitorId=" + visitorId +
                ", dealType='" + dealType + '\'' +
                ", itemType='" + itemType + '\'' +
                ", amountRequired=" + amountRequired +
                ", amountDelivered=" + amountDelivered +
                ", playerName='" + playerName + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}