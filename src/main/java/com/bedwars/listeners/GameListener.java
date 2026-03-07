package com.bedwars.listeners;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final BedwarsPlugin plugin;

    public GameListener(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Protect non-placed blocks from explosions
        if (event.getEntity() instanceof TNTPrimed || event.getEntity().getType() == EntityType.FIREBALL) {
            BedwarsGame game = getGameAtLocation(event.getEntity().getLocation());
            if (game != null) {
                event.blockList().removeIf(block -> !game.isPlacedBlock(block.getLocation()));
                // Remove placed blocks that were blown up
                event.blockList().forEach(block -> game.removePlacedBlock(block.getLocation()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        BedwarsGame game = getGameAtLocation(event.getBlock().getLocation());
        if (game != null) {
            event.blockList().removeIf(block -> !game.isPlacedBlock(block.getLocation()));
            event.blockList().forEach(block -> game.removePlacedBlock(block.getLocation()));
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player player) {
            BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
            if (game == null || game.getGameState() != GameState.PLAYING) return;

            if (event.getEntity().getShooter() instanceof Player shooter) {
                BedwarsTeam shooterTeam = game.getPlayerTeam(shooter.getUniqueId());
                BedwarsTeam victimTeam = game.getPlayerTeam(player.getUniqueId());

                // Cancel friendly fire
                if (shooterTeam != null && victimTeam != null &&
                        shooterTeam.getColor() == victimTeam.getColor()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null || game.getGameState() != GameState.PLAYING) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        // Handle special items
        switch (item.getType()) {
            case FIRE_CHARGE -> {
                // Fireball
                event.setCancelled(true);
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }
                Fireball fireball = player.launchProjectile(Fireball.class);
                fireball.setYield(2);
                fireball.setIsIncendiary(false);
            }
            case EGG -> {
                // Bridge Egg (places blocks in a bridge pattern)
                // Standard bridge egg handled by vanilla
            }
            default -> { /* normal item */ }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Prevent mob spawning in game worlds
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            for (BedwarsGame game : plugin.getGameManager().getGames()) {
                if (game.getWorld().equals(event.getEntity().getWorld())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        // Prevent tools/armor from taking durability damage
        // Hypixel has Unbreaking on most gear
        ItemStack item = event.getItem();
        if (item != null && (item.getType().name().endsWith("_SWORD") ||
                item.getType().name().endsWith("_PICKAXE") ||
                item.getType().name().endsWith("_AXE") ||
                item.getType().name().endsWith("_SHOVEL") ||
                item.getType() == Material.SHEARS ||
                item.getType() == Material.BOW)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        // Prevent junk items from spawning
        // Items dropped on death are already cleared
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null) return;

        if (game.getGameState() != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Spectators take no damage
        if (game.isSpectator(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTargetPlayer(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
            if (game != null && (game.isSpectator(player.getUniqueId()) ||
                    player.getGameMode() == GameMode.SPECTATOR)) {
                event.setCancelled(true);
            }
        }
    }

    private BedwarsGame getGameAtLocation(org.bukkit.Location location) {
        for (BedwarsGame game : plugin.getGameManager().getGames()) {
            if (game.getWorld().equals(location.getWorld())) {
                return game;
            }
        }
        return null;
    }
}
