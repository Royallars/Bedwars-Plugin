package com.bedwars.game;

import com.bedwars.BedwarsPlugin;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {

    private final BedwarsPlugin plugin;
    private final Map<String, BedwarsGame> games = new HashMap<>();
    private final Map<UUID, BedwarsGame> playerGameMap = new HashMap<>();

    public GameManager(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerGame(BedwarsGame game) {
        games.put(game.getArenaName().toLowerCase(), game);
    }

    public void unregisterGame(String name) {
        BedwarsGame game = games.remove(name.toLowerCase());
        if (game != null) {
            // Remove all players from tracking
            for (UUID uuid : game.getAllPlayers()) {
                playerGameMap.remove(uuid);
            }
        }
    }

    public BedwarsGame getGame(String name) {
        return games.get(name.toLowerCase());
    }

    public BedwarsGame getPlayerGame(Player player) {
        return playerGameMap.get(player.getUniqueId());
    }

    public BedwarsGame getPlayerGame(UUID uuid) {
        return playerGameMap.get(uuid);
    }

    public boolean isInGame(Player player) {
        return playerGameMap.containsKey(player.getUniqueId());
    }

    public boolean joinGame(Player player, BedwarsGame game) {
        if (isInGame(player)) {
            return false;
        }

        if (game.addPlayer(player)) {
            playerGameMap.put(player.getUniqueId(), game);
            return true;
        }
        return false;
    }

    public boolean joinAnyGame(Player player) {
        // Find an open game
        for (BedwarsGame game : games.values()) {
            GameState state = game.getGameState();
            if (state == GameState.WAITING || state == GameState.STARTING) {
                if (game.getPlayerCount() < game.getMaxPlayers()) {
                    return joinGame(player, game);
                }
            }
        }
        return false;
    }

    public void leaveGame(Player player) {
        BedwarsGame game = playerGameMap.remove(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player, true);
        }
    }

    public Collection<BedwarsGame> getGames() {
        return games.values();
    }

    public Map<String, BedwarsGame> getGameMap() {
        return Collections.unmodifiableMap(games);
    }

    public void shutdown() {
        for (BedwarsGame game : games.values()) {
            for (UUID uuid : game.getAllPlayers()) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null) {
                    game.removePlayer(player, false);
                }
            }
        }
        games.clear();
        playerGameMap.clear();
    }
}
