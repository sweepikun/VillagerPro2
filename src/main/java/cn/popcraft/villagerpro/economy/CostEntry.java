package cn.popcraft.villagerpro.economy;

public class CostEntry {
    private final String type;      // 成本类型 (vault, playerpoints, itemsadder)
    private final double amount;    // 数量
    private final String item;      // 物品ID (仅itemsadder类型需要)
    
    /**
     * 构造函数 (用于vault和playerpoints类型)
     * @param type 类型
     * @param amount 数量
     */
    public CostEntry(String type, double amount) {
        this.type = type;
        this.amount = amount;
        this.item = null;
    }
    
    /**
     * 构造函数 (用于itemsadder类型)
     * @param type 类型
     * @param amount 数量
     * @param item 物品ID
     */
    public CostEntry(String type, double amount, String item) {
        this.type = type;
        this.amount = amount;
        this.item = item;
    }
    
    // Getters
    public String getType() {
        return type;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public String getItem() {
        return item;
    }
    
    /**
     * 验证成本条目是否有效
     * @return 是否有效
     */
    public boolean isValid() {
        // 检查类型是否有效
        if (type == null || type.isEmpty()) {
            return false;
        }
        
        // 检查数量是否有效
        if (amount < 0) {
            return false;
        }
        
        // 对于itemsadder类型，检查物品ID是否有效
        if ("itemsadder".equals(type)) {
            return item != null && !item.isEmpty();
        }
        
        // 对于vault和playerpoints类型，只需要类型和数量有效
        return "vault".equals(type) || "playerpoints".equals(type);
    }
    
    @Override
    public String toString() {
        if ("itemsadder".equals(type)) {
            return String.format("CostEntry{type='%s', amount=%.2f, item='%s'}", type, amount, item);
        } else {
            return String.format("CostEntry{type='%s', amount=%.2f}", type, amount);
        }
    }
}