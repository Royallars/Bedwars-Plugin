package com.bedwars.shop;

import org.bukkit.Material;

public class ShopItem {

    private final String name;
    private final Material material;
    private final int amount;
    private final Material currency;
    private final int cost;
    private final ShopCategory category;
    private final boolean onePerGame; // Like Dragon Buff - can only buy once

    public ShopItem(String name, Material material, int amount, Material currency, int cost, ShopCategory category) {
        this(name, material, amount, currency, cost, category, false);
    }

    public ShopItem(String name, Material material, int amount, Material currency, int cost,
                    ShopCategory category, boolean onePerGame) {
        this.name = name;
        this.material = material;
        this.amount = amount;
        this.currency = currency;
        this.cost = cost;
        this.category = category;
        this.onePerGame = onePerGame;
    }

    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public Material getCurrency() { return currency; }
    public int getCost() { return cost; }
    public ShopCategory getCategory() { return category; }
    public boolean isOnePerGame() { return onePerGame; }

    public String getCurrencyName() {
        return switch (currency) {
            case IRON_INGOT -> "Iron";
            case GOLD_INGOT -> "Gold";
            case DIAMOND -> "Diamond";
            case EMERALD -> "Emerald";
            default -> currency.name();
        };
    }
}
