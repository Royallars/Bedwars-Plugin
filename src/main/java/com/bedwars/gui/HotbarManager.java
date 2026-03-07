package com.bedwars.gui;

import com.bedwars.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Manages hotbar items given to players in different game states.
 */
public class HotbarManager {

    // Slot assignments
    public static final int SLOT_COMPASS = 0;      // Lobby: Arena selector | In-game: Game menu
    public static final int SLOT_SHOP = 4;         // In-game: Item Shop
    public static final int SLOT_UPGRADES = 8;     // In-game: Team Upgrades

    /** Items given in the global lobby (before joining an arena). */
    public static void giveLobbyItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(SLOT_COMPASS, buildItem(
                Material.COMPASS, "&6&lJoin Game",
                List.of("&7Click to open the arena selector.")));
    }

    /** Items given while waiting / in the arena lobby before the game starts. */
    public static void giveWaitingItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(SLOT_COMPASS, buildItem(
                Material.COMPASS, "&e&lArena Menu",
                List.of("&7Click to select a team or", "&7leave the game.")));
    }

    /** Items given during the active game (in-game hotbar). */
    public static void giveIngameItems(Player player) {
        // Keep existing items but ensure the hotbar utilities are present
        player.getInventory().setItem(SLOT_SHOP, buildItem(
                Material.GOLD_INGOT, "&6&lItem Shop",
                List.of("&7Click to open the Item Shop.")));
        player.getInventory().setItem(SLOT_UPGRADES, buildItem(
                Material.DIAMOND, "&b&lTeam Upgrades",
                List.of("&7Click to open the Upgrade Shop.")));
    }

    /** Items given to spectators (compass to open player list). */
    public static void giveSpectatorItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(SLOT_COMPASS, buildItem(
                Material.COMPASS, "&b&lSpectate Players",
                List.of("&7Click to teleport to a living player.")));
    }

    public static boolean isCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS;
    }

    public static boolean isShopItem(ItemStack item) {
        return item != null && item.getType() == Material.GOLD_INGOT && hasDisplayName(item, "Item Shop");
    }

    public static boolean isUpgradeItem(ItemStack item) {
        return item != null && item.getType() == Material.DIAMOND && hasDisplayName(item, "Team Upgrades");
    }

    private static boolean hasDisplayName(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() &&
                meta.getDisplayName().replaceAll("§[0-9a-fklmnor]", "").contains(name);
    }

    private static ItemStack buildItem(Material material, String name, List<String> lore) {
        return ArenaSelectionGUI.buildItem(material, name, lore);
    }
}
