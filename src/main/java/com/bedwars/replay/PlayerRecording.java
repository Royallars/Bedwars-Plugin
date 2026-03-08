package com.bedwars.replay;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Circular-buffer recording of a single player's recent movement and combat data.
 * Keeps the last ~60 seconds of position snapshots (at 5/s) and up to 100 attack events.
 */
public class PlayerRecording {

    private static final int MAX_SNAPSHOTS = 300; // 60 s × 5/s
    private static final int MAX_ATTACKS   = 100;

    // Snapshot: timestamp + location + horizontal speed
    public record PositionSnapshot(long millis, Location loc, double hSpeed) {}

    // Attack: timestamp + victim info + reach + victim health at moment of hit
    public record AttackEvent(long millis, UUID victimId, String victimName, double reach, double victimHealth) {}

    private final UUID   playerId;
    private final String playerName;

    private final Deque<PositionSnapshot> snapshots = new ArrayDeque<>();
    private final Deque<AttackEvent>      attacks   = new ArrayDeque<>();

    // Running stats (updated on every add — no scan needed)
    private double maxHSpeed = 0;
    private double totalHSpeed = 0;
    private int    speedSamples = 0;
    private double maxReach = 0;
    private double totalReach = 0;

    public PlayerRecording(UUID playerId, String playerName) {
        this.playerId   = playerId;
        this.playerName = playerName;
    }

    public synchronized void addSnapshot(PositionSnapshot snap) {
        if (snapshots.size() >= MAX_SNAPSHOTS) snapshots.pollFirst();
        snapshots.addLast(snap);
        if (snap.hSpeed() > maxHSpeed) maxHSpeed = snap.hSpeed();
        if (snap.hSpeed() > 0) { totalHSpeed += snap.hSpeed(); speedSamples++; }
    }

    public synchronized void addAttack(AttackEvent event) {
        if (attacks.size() >= MAX_ATTACKS) attacks.pollFirst();
        attacks.addLast(event);
        if (event.reach() > maxReach) maxReach = event.reach();
        totalReach += event.reach();
    }

    public synchronized List<PositionSnapshot> getSnapshotsCopy() { return new ArrayList<>(snapshots); }
    public synchronized List<AttackEvent>      getAttacksCopy()    { return new ArrayList<>(attacks); }

    public double getMaxHSpeed()  { return maxHSpeed; }
    public double getAvgHSpeed()  { return speedSamples > 0 ? totalHSpeed / speedSamples : 0; }
    public double getMaxReach()   { return maxReach; }
    public double getAvgReach()   { return attacks.isEmpty() ? 0 : totalReach / attacks.size(); }
    public int    getAttackCount(){ return attacks.size(); }
    public UUID   getPlayerId()   { return playerId; }
    public String getPlayerName() { return playerName; }
}
