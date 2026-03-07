package com.bedwars.scoreboard;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameScoreboard {

    private final BedwarsGame game;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public GameScoreboard(BedwarsGame game) {
        this.game = game;
    }

    public void updateAll() {
        for (UUID uuid : game.getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                update(player);
            }
        }
    }

    public void update(Player player) {
        Scoreboard scoreboard = playerScoreboards.computeIfAbsent(player.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective objective = scoreboard.getObjective("bedwars");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("bedwars", Criteria.DUMMY,
                    MessageUtils.color("&6&lBED WARS"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear existing scores
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int line = 15;

        GameState state = game.getGameState();

        if (state == GameState.WAITING || state == GameState.STARTING) {
            setLine(objective, line--, " ");
            if (state == GameState.STARTING) {
                setLine(objective, line--, MessageUtils.color("&eStarting in: &a" + game.getCountdown() + "s"));
            } else {
                setLine(objective, line--, MessageUtils.color("&eWaiting for players..."));
            }
            setLine(objective, line--, MessageUtils.color("&7Players: &f" + game.getPlayerCount() + "/" + game.getMaxPlayers()));
            setLine(objective, line--, "  ");
            setLine(objective, line--, MessageUtils.color("&bMap: &f" + game.getArenaName()));
            setLine(objective, line--, "   ");
            setLine(objective, line, MessageUtils.color("&ewww.example.com"));

        } else if (state == GameState.PLAYING) {
            setLine(objective, line--, " ");
            setLine(objective, line--, MessageUtils.color("&7" + formatTime(game.getElapsedSeconds())));
            setLine(objective, line--, "  ");

            for (BedwarsTeam team : game.getTeams()) {
                String teamName = team.getColor().getDisplayName();
                String status;
                if (team.isEliminated()) {
                    status = ChatColor.DARK_GRAY + "✗";
                } else if (!team.isBedAlive()) {
                    status = ChatColor.RED + "✗";
                } else {
                    status = ChatColor.GREEN + "✔";
                }

                BedwarsTeam playerTeam = game.getPlayerTeam(player.getUniqueId());
                boolean isPlayerTeam = playerTeam != null && playerTeam.getColor() == team.getColor();

                String playerCount = isPlayerTeam ? "" : " &7(" + team.getSize() + ")";
                setLine(objective, line--, teamName + " " + status + MessageUtils.color(playerCount));

                if (line < 0) break;
            }

            setLine(objective, line--, "   ");

            // Show player's team stats
            BedwarsTeam playerTeam = game.getPlayerTeam(player.getUniqueId());
            if (playerTeam != null) {
                setLine(objective, line--, MessageUtils.color("&7Kills: &f" + getPlayerKills(player)));
                setLine(objective, line--, "    ");
            }

            setLine(objective, line, MessageUtils.color("&ewww.example.com"));

        } else if (state == GameState.ENDING) {
            setLine(objective, line--, " ");
            BedwarsTeam winner = game.getWinner();
            if (winner != null) {
                setLine(objective, line--, MessageUtils.color("&6Winner: " + winner.getColor().getDisplayName()));
            }
            setLine(objective, line--, "  ");
            setLine(objective, line--, MessageUtils.color("&7Kills: &f" + getPlayerKills(player)));
            setLine(objective, line--, "   ");
            setLine(objective, line, MessageUtils.color("&ewww.example.com"));
        }

        player.setScoreboard(scoreboard);
    }

    private void setLine(Objective objective, int score, String text) {
        if (score < 0) return;
        // Pad to make unique
        String entry = text + ChatColor.values()[score % ChatColor.values().length].toString() + ChatColor.RESET;
        Score s = objective.getScore(entry);
        s.setScore(score);
    }

    private int getPlayerKills(Player player) {
        return game.getPlayerKills(player.getUniqueId());
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public void removePlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        playerScoreboards.remove(uuid);
    }

    public void cleanup() {
        for (UUID uuid : playerScoreboards.keySet()) {
            removePlayer(uuid);
        }
        playerScoreboards.clear();
    }

    public void setupLobbyScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("lobby", Criteria.DUMMY,
                MessageUtils.color("&6&lBED WARS"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        setLine(objective, 5, " ");
        setLine(objective, 4, MessageUtils.color("&7Waiting for players..."));
        setLine(objective, 3, MessageUtils.color("&7Players: &f" + game.getPlayerCount() + "/" + game.getMaxPlayers()));
        setLine(objective, 2, "  ");
        setLine(objective, 1, MessageUtils.color("&bMap: &f" + game.getArenaName()));
        setLine(objective, 0, MessageUtils.color("&ewww.example.com"));

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
}
