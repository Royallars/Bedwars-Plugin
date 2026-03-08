package com.bedwars.commands;

import com.bedwars.BedwarsPlugin;
import com.bedwars.stats.StatsManager;
import com.bedwars.stats.StatsManager.LeaderboardStat;
import com.bedwars.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /bwtop [wins|kills|finals|beds|games]
 *
 * Displays the top 10 players for the chosen stat, fetched asynchronously
 * so the main thread is never blocked.
 */
public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final BedwarsPlugin plugin;

    public LeaderboardCommand(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        LeaderboardStat stat = LeaderboardStat.WINS; // default

        if (args.length >= 1) {
            try {
                stat = LeaderboardStat.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(MessageUtils.color(
                    "&cUnknown stat. Choose: wins, kills, finals, beds, games"));
                return true;
            }
        }

        final LeaderboardStat finalStat = stat;
        sender.sendMessage(MessageUtils.color("&6Fetching leaderboard..."));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<StatsManager.PlayerStats> top = plugin.getStatsManager().getTopPlayers(finalStat, 10);

            // Send results back on the main thread (safe for CommandSender)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(MessageUtils.color("&6&m══════════════════════════"));
                sender.sendMessage(MessageUtils.color("   &6&lTop 10 — " + finalStat.displayName));
                sender.sendMessage(MessageUtils.color("&6&m══════════════════════════"));

                if (top.isEmpty()) {
                    sender.sendMessage(MessageUtils.color("&7No data yet. Play some games!"));
                } else {
                    String[] medals = {"&6①", "&7②", "&7③", "&e④", "&e⑤",
                                       "&e⑥", "&e⑦", "&e⑧", "&e⑨", "&e⑩"};
                    for (int i = 0; i < top.size(); i++) {
                        StatsManager.PlayerStats s = top.get(i);
                        int value = switch (finalStat) {
                            case WINS   -> s.wins();
                            case KILLS  -> s.kills();
                            case FINALS -> s.finalKills();
                            case BEDS   -> s.bedsDestroyed();
                            case GAMES  -> s.gamesPlayed();
                        };
                        sender.sendMessage(MessageUtils.color(
                            " " + medals[i] + " &f" + s.username() +
                            " &8— &e" + value + " &7" + finalStat.displayName));
                    }
                }
                sender.sendMessage(MessageUtils.color("&6&m══════════════════════════"));
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(LeaderboardStat.values())
                    .map(s -> s.name().toLowerCase())
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
