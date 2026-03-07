package com.bedwars.gui;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.shop.ShopCategory;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * In-game compass menu: quick access to Item Shop, Upgrade Shop, stats, and leaving.
 */
public class GameMenuGUI {

    public static final String TITLE = "§6§lGame Menu";

    public void open(Player player, BedwarsGame game) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Background
        ItemStack bg = ArenaSelectionGUI.buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());

        // Item Shop
        inv.setItem(10, ArenaSelectionGUI.buildItem(Material.GOLD_INGOT,
                "&6&lItem Shop",
                List.of("&7Browse and buy items", "&7using your resources.", "", "&eClick to open!")));

        // Upgrade Shop
        inv.setItem(12, ArenaSelectionGUI.buildItem(Material.DIAMOND,
                "&b&lTeam Upgrades",
                List.of("&7Upgrade your team's", "&7swords, armor, forge & more.", "", "&eClick to open!")));

        // Team info
        if (team != null) {
            List<String> teamLore = List.of(
                    "&7Your team: " + team.getColor().getDisplayName(),
                    "&7Bed: " + (team.isBedAlive() ? "&a✔ Alive" : "&c✗ Destroyed"),
                    "&7Kills: &f" + team.getKills(),
                    "&7Finals: &f" + team.getFinalKills()
            );
            inv.setItem(14, ArenaSelectionGUI.buildItem(team.getColor().getWoolMaterial(),
                    team.getColor().getDisplayName() + "&r &7Team",
                    teamLore));
        }

        // Scoreboard / stats
        inv.setItem(16, ArenaSelectionGUI.buildItem(Material.BOOK,
                "&e&lMatch Stats",
                buildStatsLore(player, game)));

        // Leave game
        inv.setItem(22, ArenaSelectionGUI.buildItem(Material.BARRIER,
                "&c&lLeave Game",
                List.of("&7Leave the current game", "&7and return to the lobby.", "", "&c▶ Click to leave")));

        player.openInventory(inv);
    }

    private List<String> buildStatsLore(Player player, BedwarsGame game) {
        int kills = game.getPlayerKills(player.getUniqueId());
        String time = formatTime(game.getElapsedSeconds());

        return List.of(
                "&7Elapsed: &f" + time,
                "&7Your kills: &f" + kills,
                "",
                "&7Teams remaining: &f" + countAliveTeams(game)
        );
    }

    private int countAliveTeams(BedwarsGame game) {
        int count = 0;
        for (BedwarsTeam t : game.getTeams()) {
            if (!t.isEliminated() && !t.getPlayers().isEmpty()) count++;
        }
        return count;
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
