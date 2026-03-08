package com.bedwars.listeners;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

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

        // Feature 8: Detailed Death Message — include weapon info
        if (killer != null) {
            ItemStack weapon = killer.getInventory().getItemInHand();
            String weaponName = formatWeaponName(weapon);
            BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
            String teamPrefix = team != null ? team.getColor().getChatColor().toString() : "§7";
            game.broadcast(MessageUtils.color("&8[☠] " + teamPrefix + player.getName()
                    + " &7was killed by &e" + killer.getName()
                    + (weaponName != null ? " &7using &f" + weaponName : "") + "&7!"));
        }

        // Handle the death in game logic
        game.handleDeath(player, killer);
    }

    private String formatWeaponName(ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR) return null;
        org.bukkit.inventory.meta.ItemMeta weaponMeta = weapon.getItemMeta();
        if (weaponMeta != null && weaponMeta.hasDisplayName()) {
            return weaponMeta.getDisplayName();
        }
        String raw = weapon.getType().name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
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
            return;
        }

        // Feature 2: Spawn Shield — ignore damage while shielded
        if (game.hasSpawnShield(player.getUniqueId())) {
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

        // No PvP during grace period
        if (game.isInGracePeriod() && event.getDamager() instanceof Player) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player attacker) {
                MessageUtils.sendMessage(attacker, "&eGrace period is active — PvP not yet enabled!");
            }
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
        String message = event.getMessage();

        // "!" prefix = all-game chat; default = team-only chat
        boolean allGameChat = message.startsWith("!");
        String cleanMessage = allGameChat ? message.substring(1).trim() : message;

        if (team != null) {
            if (allGameChat) {
                // All players in this game see the message
                String format = "&7[ALL] " + team.getColor().getChatColor() + "[" + team.getColor().getRawName() + "] "
                        + "§r" + player.getName() + ": §7" + cleanMessage;
                event.setFormat(MessageUtils.color(format));
                event.getRecipients().removeIf(recipient ->
                        plugin.getGameManager().getPlayerGame(recipient) != game);
            } else {
                // Team-only chat
                String format = team.getColor().getChatColor() + "[" + team.getColor().getRawName() + "] "
                        + "§r" + player.getName() + ": " + cleanMessage;
                event.setFormat(format);
                event.getRecipients().removeIf(recipient -> {
                    BedwarsTeam recipientTeam = game.getPlayerTeam(recipient.getUniqueId());
                    return recipientTeam == null || recipientTeam.getColor() != team.getColor();
                });
            }
        } else {
            // Spectator — can see all game chat but no team prefix
            event.setFormat("§7[SPEC] §r" + player.getName() + ": " + cleanMessage);
            event.getRecipients().removeIf(recipient ->
                    plugin.getGameManager().getPlayerGame(recipient) != game);
        }
        event.setMessage(cleanMessage);
    }

    // PlayerJoinEvent is handled in GUIManager (gives lobby compass + welcome msg)
}
