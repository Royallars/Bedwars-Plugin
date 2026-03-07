package com.bedwars.commands;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JoinCommand implements CommandExecutor, TabCompleter {

    private final BedwarsPlugin plugin;
    private final boolean isLeave;

    public JoinCommand(BedwarsPlugin plugin, boolean isLeave) {
        this.plugin = plugin;
        this.isLeave = isLeave;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (isLeave) {
            handleLeave(player);
            return true;
        }

        handleJoin(player, args);
        return true;
    }

    private void handleJoin(Player player, String[] args) {
        if (plugin.getGameManager().isInGame(player)) {
            MessageUtils.sendMessage(player, "&cYou are already in a game! Use /bwleave to leave.");
            return;
        }

        if (args.length == 0) {
            // Join any available game
            boolean joined = plugin.getGameManager().joinAnyGame(player);
            if (!joined) {
                MessageUtils.sendMessage(player, "&cNo available games to join! Try again later.");
            } else {
                MessageUtils.sendMessage(player, "&aYou joined the game!");
            }
            return;
        }

        // Join specific arena
        String arenaName = args[0];
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);

        if (game == null) {
            MessageUtils.sendMessage(player, "&cArena not found: &e" + arenaName);
            return;
        }

        GameState state = game.getGameState();
        if (state != GameState.WAITING && state != GameState.STARTING) {
            MessageUtils.sendMessage(player, "&cThis game is already in progress!");
            return;
        }

        if (game.getPlayerCount() >= game.getMaxPlayers()) {
            MessageUtils.sendMessage(player, "&cThis game is full!");
            return;
        }

        boolean joined = plugin.getGameManager().joinGame(player, game);
        if (joined) {
            MessageUtils.sendMessage(player, "&aYou joined &e" + arenaName + "&a!");
        } else {
            MessageUtils.sendMessage(player, "&cFailed to join the game!");
        }
    }

    private void handleLeave(Player player) {
        if (!plugin.getGameManager().isInGame(player)) {
            MessageUtils.sendMessage(player, "&cYou are not in a game!");
            return;
        }
        plugin.getGameManager().leaveGame(player);
        MessageUtils.sendMessage(player, "&cYou left the game.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!isLeave && args.length == 1) {
            plugin.getGameManager().getGameMap().keySet().forEach(completions::add);
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }
        return completions;
    }
}
