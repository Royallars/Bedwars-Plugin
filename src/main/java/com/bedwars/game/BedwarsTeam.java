package com.bedwars.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BedwarsTeam {

    private final TeamColor color;
    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> eliminatedPlayers = new ArrayList<>();
    private Location spawnLocation;
    private Location bedLocation;
    private boolean bedAlive = true;
    private int kills = 0;
    private int finalKills = 0;
    private int bedsDestroyed = 0;

    // Upgrades
    private int sharpenLevel = 0;    // 0-4 (levels 1-5 of Sharpness)
    private int protectionLevel = 0; // 0-3 (levels 1-4 of Protection)
    private int forgeLevel = 0;      // 0-3 (4 tiers: Iron Forge, Golden Forge, Emerald Forge, Molten Forge)
    private int hasteLevel = 0;      // 0-1 (Haste I, Haste II)
    private boolean healPool = false;
    private int trapLevel = 0;       // current trap tier

    public BedwarsTeam(TeamColor color) {
        this.color = color;
    }

    public TeamColor getColor() {
        return color;
    }

    public List<UUID> getPlayers() {
        return players;
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public List<UUID> getEliminatedPlayers() {
        return eliminatedPlayers;
    }

    public void eliminatePlayer(UUID uuid) {
        players.remove(uuid);
        eliminatedPlayers.add(uuid);
    }

    public boolean isEliminated() {
        return !bedAlive && players.isEmpty();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    public Location getBedLocation() {
        return bedLocation;
    }

    public void setBedLocation(Location location) {
        this.bedLocation = location;
    }

    public boolean isBedAlive() {
        return bedAlive;
    }

    public void destroyBed() {
        this.bedAlive = false;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public void addFinalKill() {
        this.finalKills++;
    }

    public int getBedsDestroyed() {
        return bedsDestroyed;
    }

    public void addBedDestroyed() {
        this.bedsDestroyed++;
    }

    public int getSize() {
        return players.size();
    }

    // Upgrade getters/setters
    public int getSharpenLevel() { return sharpenLevel; }
    public void setSharpenLevel(int level) { this.sharpenLevel = level; }

    public int getProtectionLevel() { return protectionLevel; }
    public void setProtectionLevel(int level) { this.protectionLevel = level; }

    public int getForgeLevel() { return forgeLevel; }
    public void setForgeLevel(int level) { this.forgeLevel = level; }

    public int getHasteLevel() { return hasteLevel; }
    public void setHasteLevel(int level) { this.hasteLevel = level; }

    public boolean hasHealPool() { return healPool; }
    public void setHealPool(boolean healPool) { this.healPool = healPool; }

    public int getTrapLevel() { return trapLevel; }
    public void setTrapLevel(int level) { this.trapLevel = level; }

    /**
     * Get the iron generator delay multiplier based on forge level.
     * Forge reduces time between iron spawns.
     */
    public double getForgeMultiplier() {
        return switch (forgeLevel) {
            case 1 -> 0.75; // Iron Forge: 25% faster
            case 2 -> 0.5;  // Golden Forge: 50% faster
            case 3 -> 0.25; // Emerald Forge: 75% faster (emergency refill)
            default -> 1.0;
        };
    }

    public String getStatusSymbol() {
        if (isEliminated()) {
            return color.getChatColor() + "✗";
        } else if (!bedAlive) {
            return color.getChatColor() + "§c✗";
        } else {
            return color.getChatColor() + "✔";
        }
    }
}
