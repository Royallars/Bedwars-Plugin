package com.bedwars.commands;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.game.TeamColor;
import com.bedwars.generators.GeneratorType;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BedwarsCommand implements CommandExecutor, TabCompleter {

    private final BedwarsPlugin plugin;

    public BedwarsCommand(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("bedwars.admin")) {
            sender.sendMessage(MessageUtils.color("&cYou don't have permission!"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars create <name>"));
                    return true;
                }
                createArena(player, args[1]);
            }
            case "setlobby" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                setMainLobby(player);
            }
            case "setgamelobby" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars setgamelobby <arena>"));
                    return true;
                }
                setGameLobby(player, args[1]);
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 3) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars setspawn <arena> <team>"));
                    return true;
                }
                setTeamSpawn(player, args[1], args[2]);
            }
            case "setbed" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 3) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars setbed <arena> <team>"));
                    return true;
                }
                setTeamBed(player, args[1], args[2]);
            }
            case "addgenerator" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 3) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars addgenerator <arena> <type>"));
                    return true;
                }
                addGenerator(player, args[1], args[2]);
            }
            case "save" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars save <arena>"));
                    return true;
                }
                saveArena(sender, args[1]);
            }
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars start <arena>"));
                    return true;
                }
                forceStart(sender, args[1]);
            }
            case "stop" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars stop <arena>"));
                    return true;
                }
                forceStop(sender, args[1]);
            }
            case "list" -> listArenas(sender);
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.color("&cUsage: /bedwars info <arena>"));
                    return true;
                }
                arenaInfo(sender, args[1]);
            }
            case "reload" -> reloadPlugin(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void createArena(Player player, String name) {
        if (plugin.getGameManager().getGame(name) != null) {
            player.sendMessage(MessageUtils.color("&cArena '" + name + "' already exists!"));
            return;
        }

        World world = player.getWorld();
        BedwarsGame game = new BedwarsGame(plugin, name, world, 2, 16);

        // Add default teams
        for (TeamColor color : TeamColor.values()) {
            game.getOrCreateTeam(color);
        }

        plugin.getGameManager().registerGame(game);
        player.sendMessage(MessageUtils.color("&aArena &e" + name + " &acreated! Now set up the arena:"));
        player.sendMessage(MessageUtils.color("&71. &e/bedwars setgamelobby " + name + " &7- Set game lobby"));
        player.sendMessage(MessageUtils.color("&72. &e/bedwars setspawn " + name + " <team> &7- Set team spawns"));
        player.sendMessage(MessageUtils.color("&73. &e/bedwars setbed " + name + " <team> &7- Set team beds (stand next to them)"));
        player.sendMessage(MessageUtils.color("&74. &e/bedwars addgenerator " + name + " <iron/gold/diamond/emerald> &7- Add generators"));
        player.sendMessage(MessageUtils.color("&75. &e/bedwars save " + name + " &7- Save the arena"));
    }

    private void setMainLobby(Player player) {
        plugin.getConfigManager().setLobbyLocation(player.getLocation());
        player.sendMessage(MessageUtils.color("&aMain lobby location set!"));
    }

    private void setGameLobby(Player player, String arenaName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            player.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }
        game.setLobbyLocation(player.getLocation());
        game.setSpectatorLocation(player.getLocation());
        player.sendMessage(MessageUtils.color("&aGame lobby set for arena &e" + arenaName + "&a!"));
    }

    private void setTeamSpawn(Player player, String arenaName, String teamName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            player.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }

        TeamColor color = TeamColor.fromString(teamName);
        if (color == null) {
            player.sendMessage(MessageUtils.color("&cUnknown team color: " + teamName));
            return;
        }

        BedwarsTeam team = game.getOrCreateTeam(color);
        team.setSpawnLocation(player.getLocation());
        player.sendMessage(MessageUtils.color("&aSpawn for &e" + color.getDisplayName() + " &aset!"));
    }

    private void setTeamBed(Player player, String arenaName, String teamName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            player.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }

        TeamColor color = TeamColor.fromString(teamName);
        if (color == null) {
            player.sendMessage(MessageUtils.color("&cUnknown team color: " + teamName));
            return;
        }

        // Find nearest bed block
        org.bukkit.block.Block block = player.getTargetBlockExact(5);
        if (block == null || !block.getType().name().endsWith("_BED")) {
            player.sendMessage(MessageUtils.color("&cLook at a bed block to set the bed location!"));
            return;
        }

        BedwarsTeam team = game.getOrCreateTeam(color);
        team.setBedLocation(block.getLocation());
        player.sendMessage(MessageUtils.color("&aBed for &e" + color.getDisplayName() + " &aset at &7" +
                block.getX() + ", " + block.getY() + ", " + block.getZ() + "&a!"));
    }

    private void addGenerator(Player player, String arenaName, String typeName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            player.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }

        GeneratorType type;
        try {
            type = GeneratorType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(MessageUtils.color("&cUnknown generator type: " + typeName + " (iron/gold/diamond/emerald)"));
            return;
        }

        game.addGeneratorLocation(type, player.getLocation(), null);
        player.sendMessage(MessageUtils.color("&a" + type.getDisplayName() + " generator added at your location!"));
    }

    private void saveArena(CommandSender sender, String arenaName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            sender.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }
        plugin.getConfigManager().saveArena(game);
        sender.sendMessage(MessageUtils.color("&aArena &e" + arenaName + " &asaved!"));
    }

    private void forceStart(CommandSender sender, String arenaName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            sender.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }
        if (game.getGameState() != GameState.WAITING && game.getGameState() != GameState.STARTING) {
            sender.sendMessage(MessageUtils.color("&cGame is already in progress!"));
            return;
        }
        game.startGame();
        sender.sendMessage(MessageUtils.color("&aForce started arena &e" + arenaName + "&a!"));
    }

    private void forceStop(CommandSender sender, String arenaName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            sender.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }
        game.endGame(null);
        sender.sendMessage(MessageUtils.color("&cForce stopped arena &e" + arenaName + "&c!"));
    }

    private void listArenas(CommandSender sender) {
        sender.sendMessage(MessageUtils.color("&6=== Bedwars Arenas ==="));
        if (plugin.getGameManager().getGames().isEmpty()) {
            sender.sendMessage(MessageUtils.color("&7No arenas registered."));
            return;
        }
        for (BedwarsGame game : plugin.getGameManager().getGames()) {
            sender.sendMessage(MessageUtils.color("&e" + game.getArenaName() + " &7- &f" +
                    game.getGameState().name() + " &7[" + game.getPlayerCount() + "/" + game.getMaxPlayers() + "]"));
        }
    }

    private void arenaInfo(CommandSender sender, String arenaName) {
        BedwarsGame game = plugin.getGameManager().getGame(arenaName);
        if (game == null) {
            sender.sendMessage(MessageUtils.color("&cArena not found: " + arenaName));
            return;
        }
        sender.sendMessage(MessageUtils.color("&6=== Arena: &e" + arenaName + " &6==="));
        sender.sendMessage(MessageUtils.color("&7State: &f" + game.getGameState()));
        sender.sendMessage(MessageUtils.color("&7World: &f" + game.getWorld().getName()));
        sender.sendMessage(MessageUtils.color("&7Players: &f" + game.getPlayerCount() + "/" + game.getMaxPlayers()));
        sender.sendMessage(MessageUtils.color("&7Teams: &f" + game.getTeams().size()));
        sender.sendMessage(MessageUtils.color("&7Generators: &f" + game.getGenerators().size()));
        for (BedwarsTeam team : game.getTeams()) {
            String bedStatus = team.isBedAlive() ? "&a✔ BED ALIVE" : "&c✗ BED GONE";
            sender.sendMessage(MessageUtils.color("  &7" + team.getColor().getDisplayName() + " &7- " +
                    team.getSize() + " players - " + bedStatus));
        }
    }

    private void reloadPlugin(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(MessageUtils.color("&aBedWars configuration reloaded!"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtils.color("&6=== BedWars Admin Commands ==="));
        sender.sendMessage(MessageUtils.color("&e/bw create <name> &7- Create a new arena"));
        sender.sendMessage(MessageUtils.color("&e/bw setlobby &7- Set main lobby"));
        sender.sendMessage(MessageUtils.color("&e/bw setgamelobby <arena> &7- Set game lobby"));
        sender.sendMessage(MessageUtils.color("&e/bw setspawn <arena> <team> &7- Set team spawn"));
        sender.sendMessage(MessageUtils.color("&e/bw setbed <arena> <team> &7- Set team bed"));
        sender.sendMessage(MessageUtils.color("&e/bw addgenerator <arena> <type> &7- Add resource generator"));
        sender.sendMessage(MessageUtils.color("&e/bw save <arena> &7- Save arena"));
        sender.sendMessage(MessageUtils.color("&e/bw start <arena> &7- Force start"));
        sender.sendMessage(MessageUtils.color("&e/bw stop <arena> &7- Force stop"));
        sender.sendMessage(MessageUtils.color("&e/bw list &7- List arenas"));
        sender.sendMessage(MessageUtils.color("&e/bw info <arena> &7- Arena info"));
        sender.sendMessage(MessageUtils.color("&e/bw reload &7- Reload config"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "setlobby", "setgamelobby", "setspawn",
                    "setbed", "addgenerator", "save", "start", "stop", "list", "info", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "setgamelobby", "setspawn", "setbed", "addgenerator", "save", "start", "stop", "info" ->
                    plugin.getGameManager().getGameMap().keySet().forEach(completions::add);
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "setspawn", "setbed" -> {
                    for (TeamColor color : TeamColor.values()) {
                        completions.add(color.getConfigName());
                    }
                }
                case "addgenerator" -> {
                    for (GeneratorType type : GeneratorType.values()) {
                        completions.add(type.name().toLowerCase());
                    }
                }
            }
        }

        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        return completions;
    }
}
