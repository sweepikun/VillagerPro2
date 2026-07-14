package cn.popcraft.villagerpro.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (!Double.isFinite(amount) || amount <= 0) {
            return false;
        }
        
        // 对于itemsadder类型，检查物品ID是否有效
        if ("itemsadder".equalsIgnoreCase(type) || "item".equalsIgnoreCase(type)) {
            return item != null && !item.isEmpty();
        }
        
        // 对于vault和playerpoints类型，只需要类型和数量有效
        return "vault".equalsIgnoreCase(type) || "playerpoints".equalsIgnoreCase(type);
    }

    public static List<CostEntry> normalize(List<CostEntry> costs) {
        if (costs == null) {
            return null;
        }

        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, String> items = new LinkedHashMap<>();
        for (CostEntry cost : costs) {
            if (cost == null || !cost.isValid()) {
                return null;
            }
            String type = cost.getType().toLowerCase();
            String item = ("itemsadder".equals(type) || "item".equals(type)) ? cost.getItem() : null;
            String key = type + "\u0000" + (item == null ? "" : item);
            totals.merge(key, cost.getAmount(), Double::sum);
            items.put(key, item);
        }

        List<CostEntry> normalized = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            String key = entry.getKey();
            String type = key.substring(0, key.indexOf('\u0000'));
            String item = items.get(key);
            normalized.add(item == null
                    ? new CostEntry(type, entry.getValue())
                    : new CostEntry(type, entry.getValue(), item));
        }
        return normalized;
    }
    
    @Override
    public String toString() {
        if ("itemsadder".equalsIgnoreCase(type) || "item".equalsIgnoreCase(type)) {
            return String.format("CostEntry{type='%s', amount=%.2f, item='%s'}", type, amount, item);
        } else {
            return String.format("CostEntry{type='%s', amount=%.2f}", type, amount);
        }
    }
}
