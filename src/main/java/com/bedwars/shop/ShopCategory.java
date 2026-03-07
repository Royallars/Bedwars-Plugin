package com.bedwars.shop;

public enum ShopCategory {
    BLOCKS("Blocks", 0),
    WEAPONS("Weapons", 1),
    ARMOR("Armor", 2),
    TOOLS("Tools", 3),
    POTIONS("Potions", 4),
    UTILITY("Utility", 5);

    private final String displayName;
    private final int slot;

    ShopCategory(String displayName, int slot) {
        this.displayName = displayName;
        this.slot = slot;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSlot() {
        return slot;
    }
}
