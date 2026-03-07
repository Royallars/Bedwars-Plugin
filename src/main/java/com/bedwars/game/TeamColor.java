package com.bedwars.game;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum TeamColor {
    RED(ChatColor.RED, DyeColor.RED, "Red", "RED"),
    BLUE(ChatColor.BLUE, DyeColor.BLUE, "Blue", "BLUE"),
    GREEN(ChatColor.GREEN, DyeColor.GREEN, "Green", "GREEN"),
    YELLOW(ChatColor.YELLOW, DyeColor.YELLOW, "Yellow", "YELLOW"),
    AQUA(ChatColor.AQUA, DyeColor.CYAN, "Aqua", "AQUA"),
    WHITE(ChatColor.WHITE, DyeColor.WHITE, "White", "WHITE"),
    PINK(ChatColor.LIGHT_PURPLE, DyeColor.PINK, "Pink", "PINK"),
    GRAY(ChatColor.DARK_GRAY, DyeColor.GRAY, "Gray", "GRAY");

    private final ChatColor chatColor;
    private final DyeColor dyeColor;
    private final String displayName;
    private final String configName;

    TeamColor(ChatColor chatColor, DyeColor dyeColor, String displayName, String configName) {
        this.chatColor = chatColor;
        this.dyeColor = dyeColor;
        this.displayName = displayName;
        this.configName = configName;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public DyeColor getDyeColor() {
        return dyeColor;
    }

    public String getDisplayName() {
        return chatColor + displayName;
    }

    public String getRawName() {
        return displayName;
    }

    public String getConfigName() {
        return configName;
    }

    public Material getWoolMaterial() {
        return switch (this) {
            case RED -> Material.RED_WOOL;
            case BLUE -> Material.BLUE_WOOL;
            case GREEN -> Material.GREEN_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case AQUA -> Material.CYAN_WOOL;
            case WHITE -> Material.WHITE_WOOL;
            case PINK -> Material.PINK_WOOL;
            case GRAY -> Material.GRAY_WOOL;
        };
    }

    public Material getBedMaterial() {
        return switch (this) {
            case RED -> Material.RED_BED;
            case BLUE -> Material.BLUE_BED;
            case GREEN -> Material.GREEN_BED;
            case YELLOW -> Material.YELLOW_BED;
            case AQUA -> Material.CYAN_BED;
            case WHITE -> Material.WHITE_BED;
            case PINK -> Material.PINK_BED;
            case GRAY -> Material.GRAY_BED;
        };
    }

    public Material getGlassMaterial() {
        return switch (this) {
            case RED -> Material.RED_STAINED_GLASS_PANE;
            case BLUE -> Material.BLUE_STAINED_GLASS_PANE;
            case GREEN -> Material.GREEN_STAINED_GLASS_PANE;
            case YELLOW -> Material.YELLOW_STAINED_GLASS_PANE;
            case AQUA -> Material.CYAN_STAINED_GLASS_PANE;
            case WHITE -> Material.WHITE_STAINED_GLASS_PANE;
            case PINK -> Material.PINK_STAINED_GLASS_PANE;
            case GRAY -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }

    public Color getFireworkColor() {
        return switch (this) {
            case RED -> Color.RED;
            case BLUE -> Color.BLUE;
            case GREEN -> Color.GREEN;
            case YELLOW -> Color.YELLOW;
            case AQUA -> Color.AQUA;
            case WHITE -> Color.WHITE;
            case PINK -> Color.FUCHSIA;
            case GRAY -> Color.GRAY;
        };
    }

    public static TeamColor fromString(String name) {
        for (TeamColor color : values()) {
            if (color.configName.equalsIgnoreCase(name) || color.displayName.equalsIgnoreCase(name)) {
                return color;
            }
        }
        return null;
    }
}
