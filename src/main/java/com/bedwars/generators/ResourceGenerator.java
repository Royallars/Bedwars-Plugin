package com.bedwars.generators;

import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.utils.MessageUtils;
import org.bukkit.entity.EntityType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

public class ResourceGenerator {

    private final BedwarsGame game;
    private final Location location;
    private final GeneratorType type;
    private final BedwarsTeam team; // null for mid generators
    private int currentDelay;
    private int ticksElapsed = 0;
    private BukkitRunnable task;

    // For upgradeable mid generators (Diamond/Emerald tier)
    private int tier = 1;
    private int tierTicksElapsed = 0;
    private static final int[] DIAMOND_TIER_UPGRADES = {0, 1200, 2400}; // ticks until next tier
    private static final int[] DIAMOND_DELAYS = {400, 280, 200}; // delay per tier

    // Hologram
    private ArmorStand hologram;

    public ResourceGenerator(BedwarsGame game, Location location, GeneratorType type, BedwarsTeam team) {
        this.game = game;
        this.location = location.clone();
        this.type = type;
        this.team = team;
        this.currentDelay = type.getDefaultDelay();
    }

    public void start() {
        spawnHologram();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                ticksElapsed++;
                tierTicksElapsed++;

                // Handle mid generator tier upgrades
                if ((type == GeneratorType.DIAMOND || type == GeneratorType.EMERALD) && team == null) {
                    if (tier < 3 && tierTicksElapsed >= DIAMOND_TIER_UPGRADES[tier]) {
                        tier++;
                        currentDelay = DIAMOND_DELAYS[tier - 1];
                        String tierName = type == GeneratorType.EMERALD ? "EMERALD" : "DIAMOND";
                        game.broadcast(MessageUtils.color("&b&l" + tierName + " &r&bGENERATOR TIER " + tier + " UNLOCKED!"));
                        updateHologram();
                    }
                }

                // Team forge upgrade applied to team generators
                int effectiveDelay = currentDelay;
                if (team != null && (type == GeneratorType.IRON || type == GeneratorType.GOLD)) {
                    effectiveDelay = (int) (currentDelay * team.getForgeMultiplier());
                    effectiveDelay = Math.max(effectiveDelay, 10);
                }

                if (ticksElapsed >= effectiveDelay) {
                    ticksElapsed = 0;
                    spawnResource();
                }
            }
        };
        task.runTaskTimer(game.getPlugin(), 1L, 1L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
            hologram = null;
        }
    }

    private void spawnHologram() {
        World world = location.getWorld();
        if (world == null) return;
        Location hologramLoc = location.clone().add(0, 2.2, 0);
        hologram = (ArmorStand) world.spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        hologram.setInvisible(true);
        hologram.setGravity(false);
        hologram.setSmall(true);
        hologram.setInvulnerable(true);
        hologram.setMarker(true);
        hologram.setCustomNameVisible(true);
        hologram.setCustomName(buildHologramName());
    }

    private void updateHologram() {
        if (hologram != null && !hologram.isDead()) {
            hologram.setCustomName(buildHologramName());
        }
    }

    private String buildHologramName() {
        String color = type.getHologramColor();
        String name = type.getDisplayName().toUpperCase();
        if ((type == GeneratorType.DIAMOND || type == GeneratorType.EMERALD) && team == null) {
            String[] tierNames = {"I", "II", "III"};
            return color + "§l" + name + " §r§7Tier " + tierNames[tier - 1];
        }
        return color + "§l" + name + " §r§7Generator";
    }

    private void spawnResource() {
        World world = location.getWorld();
        if (world == null) return;

        // Count existing items of this type near the generator to prevent overflow
        Collection<Entity> nearby = world.getNearbyEntities(location, 1.5, 1.5, 1.5);
        int existingCount = 0;
        for (Entity e : nearby) {
            if (e instanceof Item item) {
                if (item.getItemStack().getType() == type.getMaterial()) {
                    existingCount += item.getItemStack().getAmount();
                }
            }
        }

        if (existingCount >= type.getMaxStack()) return;

        // Drop item at generator location with slight upward velocity
        ItemStack itemStack = new ItemStack(type.getMaterial());
        Item dropped = world.dropItem(location.clone().add(0, 0.5, 0), itemStack);
        dropped.setVelocity(new Vector(0, 0.1, 0));
        dropped.setPickupDelay(0);
    }

    public GeneratorType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public BedwarsTeam getTeam() {
        return team;
    }

    public int getTier() {
        return tier;
    }

    public void setCurrentDelay(int delay) {
        this.currentDelay = delay;
    }

    /** Feature 6: Rush Mode — halve the generator delay for 2x speed. */
    public void applyRushMode() {
        this.currentDelay = Math.max(10, this.currentDelay / 2);
    }
}
