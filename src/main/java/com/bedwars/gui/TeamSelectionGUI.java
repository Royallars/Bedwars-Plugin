package com.bedwars.gui;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.game.TeamColor;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TeamSelectionGUI {

    public static final String TITLE = "§6§lTeam Selection";

    public void open(Player player, BedwarsGame game) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Fill background
        ItemStack bg = ArenaSelectionGUI.buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // Title banner
        ItemStack banner = ArenaSelectionGUI.buildItem(Material.YELLOW_WOOL,
                "&6&lChoose Your Team",
                List.of("&7Click a team to join it.", "&7Teams are assigned automatically on game start."));
        inv.setItem(4, banner);

        // Team slots: row 2-3, centered
        int[] teamSlots = {10, 12, 14, 16, 28, 30, 32, 34};
        List<BedwarsTeam> teams = game.getTeams();

        for (int i = 0; i < teams.size() && i < teamSlots.length; i++) {
            inv.setItem(teamSlots[i], buildTeamItem(teams.get(i), game, player));
        }

        // Spectator option (if game is in progress)
        if (game.getGameState() == GameState.PLAYING) {
            inv.setItem(49, ArenaSelectionGUI.buildItem(Material.ENDER_EYE,
                    "&b&lSpectate",
                    List.of("&7Watch the game as a spectator.", "", "&eClick to spectate!")));
        }

        // Back button
        inv.setItem(45, ArenaSelectionGUI.buildItem(Material.ARROW,
                "&7&l« Back",
                List.of("&7Return to arena selection")));

        // Leave button
        inv.setItem(53, ArenaSelectionGUI.buildItem(Material.DARK_OAK_DOOR,
                "&c&lLeave Game",
                List.of("&7Leave the current game")));

        player.openInventory(inv);
    }

    private ItemStack buildTeamItem(BedwarsTeam team, BedwarsGame game, Player viewer) {
        TeamColor color = team.getColor();
        boolean isFull = team.getSize() >= (game.getMaxPlayers() / Math.max(1, game.getTeams().size()));
        boolean isPlayerTeam = team.hasPlayer(viewer.getUniqueId());

        Material icon = color.getWoolMaterial();
        String header = (isPlayerTeam ? "&l✔ " : "") + color.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtils.color("&7Players: &f" + team.getSize()));

        // Show player names
        for (var uuid : team.getPlayers()) {
            Player p = game.getPlugin().getServer().getPlayer(uuid);
            String pName = p != null ? p.getName() : "Offline";
            lore.add(MessageUtils.color("  &7- &f" + pName));
        }

        lore.add("");
        if (team.isEliminated()) {
            lore.add(MessageUtils.color("&c&l✗ ELIMINATED"));
        } else if (!team.isBedAlive()) {
            lore.add(MessageUtils.color("&c✗ &7Bed destroyed"));
        } else {
            lore.add(MessageUtils.color("&a✔ &7Bed alive"));
        }
        lore.add("");

        if (isPlayerTeam) {
            lore.add(MessageUtils.color("&a&l✔ Your team"));
        } else if (isFull) {
            lore.add(MessageUtils.color("&c&l✗ Team is full!"));
        } else {
            lore.add(MessageUtils.color("&e▶ Click to join!"));
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(MessageUtils.color(header));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) coloredLore.add(MessageUtils.color(line));
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the team color for the item at the given slot, or null if not a team slot.
     */
    public TeamColor getTeamColorFromSlot(Player player, int slot, BedwarsGame game) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) return null;

        String displayName = item.getItemMeta().getDisplayName();
        if (displayName == null) return null;
        String plain = displayName.replaceAll("§[0-9a-fklmnorA-F]", "").replaceAll("[✔ ]", "").trim();

        for (TeamColor color : TeamColor.values()) {
            if (color.getRawName().equalsIgnoreCase(plain)) return color;
        }
        return null;
    }
}
