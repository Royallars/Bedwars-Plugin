package com.bedwars.gui;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * GUI shown to spectators when they right-click the compass.
 * Displays all living players as skull icons; clicking teleports the spectator.
 */
public class SpectatorGUI {

    public static final String TITLE = "§b§lSpectate Players";

    public void open(Player spectator, BedwarsGame game) {
        List<UUID> alivePlayers = game.getAllPlayers().stream()
                .filter(uuid -> !game.isSpectator(uuid))
                .toList();

        int size = Math.max(9, ((alivePlayers.size() + 8) / 9) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        for (int i = 0; i < alivePlayers.size() && i < size; i++) {
            UUID uuid = alivePlayers.get(i);
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;

            BedwarsTeam team = game.getPlayerTeam(uuid);
            String teamDisplay = team != null ? team.getColor().getDisplayName() : "§7Unknown";

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            // Safe instanceof cast — avoids ClassCastException if meta type changes
            ItemMeta rawMeta = skull.getItemMeta();
            if (!(rawMeta instanceof SkullMeta meta)) {
                inv.setItem(i, skull);
                continue;
            }
            meta.setOwningPlayer(target);
            meta.setDisplayName(teamDisplay + " §r§f" + target.getName());
            meta.setLore(Arrays.asList(
                    "§7Click to teleport",
                    "§7Health: §c" + String.format("%.1f", target.getHealth() / 2.0) + " ❤",
                    "§7Location: §f" + target.getLocation().getBlockX() +
                            ", " + target.getLocation().getBlockY() +
                            ", " + target.getLocation().getBlockZ()
            ));
            skull.setItemMeta(meta);
            inv.setItem(i, skull);
        }

        if (alivePlayers.isEmpty()) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta meta = barrier.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.color("&cNo players to spectate!"));
                barrier.setItemMeta(meta);
            }
            inv.setItem(4, barrier);
        }

        spectator.openInventory(inv);
    }
}
