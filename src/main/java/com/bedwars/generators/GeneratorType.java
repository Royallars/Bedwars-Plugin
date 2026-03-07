package com.bedwars.generators;

import org.bukkit.Material;

public enum GeneratorType {
    IRON(Material.IRON_INGOT, "Iron", 40, 64),
    GOLD(Material.GOLD_INGOT, "Gold", 80, 64),
    DIAMOND(Material.DIAMOND, "Diamond", 400, 6),
    EMERALD(Material.EMERALD, "Emerald", 600, 4);

    private final Material material;
    private final String displayName;
    private final int defaultDelay; // in ticks
    private final int maxStack;

    GeneratorType(Material material, String displayName, int defaultDelay, int maxStack) {
        this.material = material;
        this.displayName = displayName;
        this.defaultDelay = defaultDelay;
        this.maxStack = maxStack;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultDelay() {
        return defaultDelay;
    }

    public int getMaxStack() {
        return maxStack;
    }
}
