package com.bedwars.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class MessageUtils {

    private static final String PREFIX = ChatColor.translateAlternateColorCodes('&', "&8[&6BedWars&8] &r");

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void sendMessage(Player player, String message) {
        player.sendMessage(PREFIX + color(message));
    }

    public static void broadcast(String message) {
        Bukkit.broadcastMessage(PREFIX + color(message));
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(color(message));
    }

    public static void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static String formatSeconds(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
        int minutes = seconds / 60;
        return minutes + " minute" + (minutes == 1 ? "" : "s");
    }
}
