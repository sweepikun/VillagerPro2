package cn.popcraft.villagerpro.models;

import org.bukkit.Material;

import java.util.Locale;

public enum BuildingType {
    GRANARY("granary", "粮仓", Material.BARREL),
    WORKSHOP("workshop", "工坊", Material.CRAFTING_TABLE),
    CLINIC("clinic", "诊所", Material.BREWING_STAND);

    private final String id;
    private final String displayName;
    private final Material defaultCore;

    BuildingType(String id, String displayName, Material defaultCore) {
        this.id = id;
        this.displayName = displayName;
        this.defaultCore = defaultCore;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getDefaultCore() {
        return defaultCore;
    }

    public static BuildingType fromInput(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (BuildingType type : values()) {
            if (type.id.equals(normalized)) return type;
        }
        return null;
    }
}
