package com.bedwars.commands;

import com.bedwars.BedwarsPlugin;
import com.bedwars.stats.StatsManager;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


import java.util.UUID;

public class StatsCommand implements CommandExecutor {

    private final BedwarsPlugin plugin;

    public StatsCommand(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        UUID targetUUID;
        String targetName;

        if (args.length >= 1) {
            // Look up another player by name
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
            targetUUID = op.getUniqueId();
            targetName = op.getName() != null ? op.getName() : args[0];
        } else if (sender instanceof Player player) {
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(MessageUtils.color("&cUsage: /bwstats <player>"));
            return true;
        }

        StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(targetUUID);

        if (stats == null) {
            sender.sendMessage(MessageUtils.color("&7No stats found for &e" + targetName + "&7."));
            return true;
        }

        sender.sendMessage(MessageUtils.color("&6&m══════════════════════"));
        sender.sendMessage(MessageUtils.color("   &6&lBedWars Stats — &e" + targetName));
        sender.sendMessage(MessageUtils.color("&6&m══════════════════════"));
        sender.sendMessage(MessageUtils.color(" &7Games Played: &f" + stats.gamesPlayed()));
        sender.sendMessage(MessageUtils.color(" &7Wins:         &a" + stats.wins()));
        sender.sendMessage(MessageUtils.color(" &7Win Rate:     &b" + String.format("%.1f", stats.winRate()) + "%"));
        sender.sendMessage(MessageUtils.color(" &7Kills:        &f" + stats.kills()));
        sender.sendMessage(MessageUtils.color(" &7Final Kills:  &f" + stats.finalKills()));
        sender.sendMessage(MessageUtils.color(" &7Beds Broken:  &f" + stats.bedsDestroyed()));
        sender.sendMessage(MessageUtils.color(" &7Deaths:       &c" + stats.deaths()));
        sender.sendMessage(MessageUtils.color("&6&m══════════════════════"));

        return true;
    }
}
