package com.bedwars.listeners;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class BedListener implements Listener {

    private final BedwarsPlugin plugin;

    public BedListener(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);

        if (game == null) return;
        if (game.getGameState() != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Check if breaking a bed
        if (isBed(block.getType())) {
            BedwarsTeam bedTeam = game.getTeamByBedLocation(block.getLocation());
            if (bedTeam == null) {
                // Try the other half of the bed
                bedTeam = game.getTeamByBedLocation(getOtherBedHalf(block));
            }

            if (bedTeam != null) {
                BedwarsTeam breakerTeam = game.getPlayerTeam(player.getUniqueId());

                // Prevent breaking own bed
                if (breakerTeam != null && breakerTeam.getColor() == bedTeam.getColor()) {
                    player.sendMessage(MessageUtils.color("&cYou cannot break your own bed!"));
                    event.setCancelled(true);
                    return;
                }

                if (!bedTeam.isBedAlive()) {
                    // Already destroyed
                    event.setCancelled(true);
                    return;
                }

                // Destroy the bed
                event.setCancelled(true);
                destroyBed(game, bedTeam, block, player);
                return;
            }

            // Regular bed not tracked - prevent breaking
            event.setCancelled(true);
            return;
        }

        // Check if it's a placed block (player placed it)
        if (!game.isPlacedBlock(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.color("&cYou can only break blocks you placed!"));
            return;
        }

        // Allow breaking placed blocks
        event.setDropItems(false);
        game.removePlacedBlock(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);

        if (game == null) return;

        GameState state = game.getGameState();
        if (state != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Track placed block
        game.trackPlacedBlock(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);

        if (game == null) return;

        // Open shop on right-clicking a villager NPC / item frame / or just when right-clicking a sign etc.
        // For now, beds should not be slept in
        Block block = event.getClickedBlock();
        if (block != null && isBed(block.getType())) {
            event.setCancelled(true);
        }
    }

    private void destroyBed(BedwarsGame game, BedwarsTeam bedTeam, Block block, Player destroyer) {
        // Remove both halves of the bed
        block.setType(Material.AIR);
        Block otherHalf = getOtherBedHalf(block);
        if (otherHalf != null && isBed(otherHalf.getType())) {
            otherHalf.setType(Material.AIR);
        }

        // Explosion effect
        block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 0f, false, false);
        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);

        game.destroyBed(bedTeam, destroyer);
    }

    private Block getOtherBedHalf(Block block) {
        if (!isBed(block.getType())) return null;
        try {
            if (block.getBlockData() instanceof Bed bedData) {
                BlockFace facing = bedData.getFacing();
                if (bedData.getPart() == Bed.Part.FOOT) {
                    return block.getRelative(facing);
                } else {
                    return block.getRelative(facing.getOppositeFace());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isBed(Material material) {
        return material.name().endsWith("_BED");
    }
}
