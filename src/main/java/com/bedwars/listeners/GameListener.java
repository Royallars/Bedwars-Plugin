package com.bedwars.listeners;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.GameState;
import com.bedwars.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BedwarsGame game = plugin.getGameManager().getPlayerGame(player);
        if (game == null || game.getGameState() != GameState.PLAYING) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        org.bukkit.event.block.Action action = event.getAction();
        boolean isRightClick = action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        if (!isRightClick) return;

        switch (item.getType()) {
            case FIRE_CHARGE -> {
                event.setCancelled(true);
                consumeOne(player, item);
                Fireball fireball = player.launchProjectile(Fireball.class);
                fireball.setYield(2);
                fireball.setIsIncendiary(false);
            }
            case EGG -> {
                // Bridge Egg — place team-colored wool blocks ahead of the player
                event.setCancelled(true);
                consumeOne(player, item);
                placeBridgeEgg(player, game);
            }
            case IRON_GOLEM_SPAWN_EGG -> {
                // Dream Defender
                event.setCancelled(true);
                consumeOne(player, item);
                spawnDreamDefender(player, game);
            }
        }
    }

    private void placeBridgeEgg(Player player, BedwarsGame game) {
        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
        Material wool = team != null ? team.getColor().getWoolMaterial() : Material.WHITE_WOOL;

        // Horizontal direction the player is looking
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        // Start one block below feet so the bridge is walkable
        Location base = player.getLocation().clone().subtract(0, 1, 0);

        int placed = 0;
        for (int i = 1; i <= 12 && placed < 12; i++) {
            Location target = base.clone().add(dir.clone().multiply(i));
            Block block = target.getBlock();
            if (block.getType() == Material.AIR || block.isPassable()) {
                block.setType(wool);
                game.trackPlacedBlock(target);
                placed++;
            }
        }
        if (placed > 0) {
            MessageUtils.sendActionBar(player, MessageUtils.color("&aBridge Egg placed &f" + placed + " &ablocks!"));
        }
    }

    private void spawnDreamDefender(Player player, BedwarsGame game) {
        BedwarsTeam team = game.getPlayerTeam(player.getUniqueId());
        if (team == null) return;

        Location spawnLoc;
        Block target = player.getTargetBlockExact(5);
        if (target != null) {
            spawnLoc = target.getLocation().add(0, 1, 0);
        } else {
            spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2));
        }

        IronGolem golem = (IronGolem) player.getWorld().spawnEntity(spawnLoc, EntityType.IRON_GOLEM);
        golem.setPlayerCreated(true);
        game.addDreamDefender(golem, team);
        player.sendMessage(MessageUtils.color("&aYou summoned a " + team.getColor().getDisplayName() + " &aDream Defender!"));
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().removeItem(item);
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
        // Dream Defender: prevent targeting allies and spectators
        if (event.getEntity() instanceof IronGolem golem) {
            for (BedwarsGame game : plugin.getGameManager().getGames()) {
                if (!game.isDreamDefender(golem.getUniqueId())) continue;
                BedwarsTeam golemTeam = game.getDreamDefenderTeam(golem.getUniqueId());
                if (event.getTarget() instanceof Player targetPlayer) {
                    if (game.isSpectator(targetPlayer.getUniqueId()) ||
                            targetPlayer.getGameMode() == GameMode.SPECTATOR) {
                        event.setCancelled(true);
                        return;
                    }
                    BedwarsTeam targetTeam = game.getPlayerTeam(targetPlayer.getUniqueId());
                    if (golemTeam != null && targetTeam != null &&
                            targetTeam.getColor() == golemTeam.getColor()) {
                        event.setCancelled(true);
                    }
                }
                return;
            }
        }

        // Prevent any mob from targeting spectators
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
