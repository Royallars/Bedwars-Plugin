package com.bedwars.gui;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArenaSelectionGUI {

    public static final String TITLE = "§6§lArena Selection";

    private final BedwarsPlugin plugin;

    public ArenaSelectionGUI(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Collection<BedwarsGame> games = plugin.getGameManager().getGames();
        int size = Math.max(27, (int) (Math.ceil(games.size() / 7.0) + 2) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        int slot = 10;
        for (BedwarsGame game : games) {
            if (slot >= size - 8) break;
            inv.setItem(slot, buildArenaItem(game));
            slot++;
            // Skip border column
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        // Back / no-games fallback
        if (games.isEmpty()) {
            ItemStack noGame = buildItem(Material.BARRIER, "&c&lNo arenas available",
                    List.of("&7There are no arenas", "&7configured on this server."));
            inv.setItem(13, noGame);
        }

        // Close button
        inv.setItem(size - 5, buildItem(Material.DARK_OAK_DOOR, "&c&lClose", List.of("&7Click to close")));

        player.openInventory(inv);
    }

    private ItemStack buildArenaItem(BedwarsGame game) {
        Material icon = getStateIcon(game.getGameState());
        String stateColor = getStateColor(game.getGameState());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtils.color("&7Map: &f" + game.getArenaName()));
        lore.add(MessageUtils.color("&7Status: " + stateColor + formatState(game.getGameState())));
        lore.add(MessageUtils.color("&7Players: &f" + game.getPlayerCount() + "&7/&f" + game.getMaxPlayers()));
        lore.add(MessageUtils.color("&7Teams: &f" + game.getTeams().size()));
        lore.add("");

        // Team status overview
        lore.add(MessageUtils.color("&7Teams:"));
        for (BedwarsTeam team : game.getTeams()) {
            String bedIcon = team.isBedAlive() ? "&a✔" : "&c✗";
            lore.add(MessageUtils.color("  " + team.getColor().getDisplayName() + " " + bedIcon +
                    " &7- &f" + team.getSize() + " players"));
        }
        lore.add("");

        if (game.getGameState() == GameState.WAITING || game.getGameState() == GameState.STARTING) {
            lore.add(MessageUtils.color("&e▶ &aClick to join!"));
        } else {
            lore.add(MessageUtils.color("&cGame in progress"));
        }

        return buildItem(icon, stateColor + game.getArenaName(), lore);
    }

    private Material getStateIcon(GameState state) {
        return switch (state) {
            case WAITING -> Material.LIME_WOOL;
            case STARTING -> Material.YELLOW_WOOL;
            case PLAYING -> Material.RED_WOOL;
            case ENDING, RESTARTING -> Material.GRAY_WOOL;
        };
    }

    private String getStateColor(GameState state) {
        return switch (state) {
            case WAITING -> "&a";
            case STARTING -> "&e";
            case PLAYING -> "&c";
            case ENDING, RESTARTING -> "&7";
        };
    }

    private String formatState(GameState state) {
        return switch (state) {
            case WAITING -> "Waiting";
            case STARTING -> "Starting";
            case PLAYING -> "In Game";
            case ENDING -> "Ending";
            case RESTARTING -> "Restarting";
        };
    }

    private void fillBorder(Inventory inv, Material material) {
        ItemStack glass = buildItem(material, " ", List.of());
        int size = inv.getSize();
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = size - 9; i < size; i++) inv.setItem(i, glass);
        for (int i = 0; i < size; i += 9) inv.setItem(i, glass);
        for (int i = 8; i < size; i += 9) inv.setItem(i, glass);
    }

    static ItemStack buildItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(MessageUtils.color(name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) coloredLore.add(MessageUtils.color(line));
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    public String getArenaNameFromSlot(Player player, int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) return null;
        String displayName = item.getItemMeta().getDisplayName();
        if (displayName == null || displayName.isBlank()) return null;
        // Strip color codes
        String plain = displayName.replaceAll("§[0-9a-fA-Fklmnor]", "").trim();
        return plain;
    }
}
