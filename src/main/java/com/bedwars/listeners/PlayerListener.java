package com.bedwars.listeners;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {

    private final BedwarsPlugin plugin;

    public PlayerListener(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game != null) {
            plugin.getGameManager().leaveGame(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null || game.getGameState() != GameState.PLAYING) return;

        // Suppress default death message
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Determine killer
        Player killer = player.getKiller();

        // Handle the death in game logic
        game.handleDeath(player, killer);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        GameState state = game.getGameState();

        // No damage in lobby/starting
        if (state == GameState.WAITING || state == GameState.STARTING || state == GameState.ENDING) {
            event.setCancelled(true);
            return;
        }

        // Spectators take no damage
        if (game.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        BedwarsGame game = plugin.getGameManager().getPlayerGame(victim);
        if (game == null) return;

        // No PvP in waiting/starting
        GameState state = game.getGameState();
        if (state != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Prevent friendly fire
        if (event.getDamager() instanceof Player attacker) {
            BedwarsTeam victimTeam = game.getPlayerTeam(victim.getUniqueId());
            BedwarsTeam attackerTeam = game.getPlayerTeam(attacker.getUniqueId());
            if (victimTeam != null && attackerTeam != null && victimTeam.getColor() == attackerTeam.getColor()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        // No hunger in waiting/starting/ending
        GameState state = game.getGameState();
        if (state != GameState.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        // Respawn at spawn (handled by our countdown system; just use lobby as fallback)
        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
        if (team != null && team.getSpawnLocation() != null) {
            event.setRespawnLocation(team.getSpawnLocation());
        } else if (game.getLobbyLocation() != null) {
            event.setRespawnLocation(game.getLobbyLocation());
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        // Allow dropping in game, prevent in lobby/starting
        GameState state = game.getGameState();
        if (state == GameState.WAITING || state == GameState.STARTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
        if (team != null) {
            String format = team.getColor().getChatColor() + "[" + team.getColor().getRawName() + "] "
                    + "§r" + player.getName() + ": " + event.getMessage();
            event.setFormat(format);

            // Only send to players in the same game
            event.getRecipients().removeIf(recipient -> plugin.getGameManager().getPlayerGame(recipient) != game);
        }
    }

    // PlayerJoinEvent is handled in GUIManager (gives lobby compass + welcome msg)
}
