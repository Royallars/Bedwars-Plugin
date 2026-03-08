package com.bedwars.replay;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin-wide manager that records position snapshots for every player currently
 * in an active BedWars game, plus combat events forwarded from listeners.
 *
 * Data is retained until the server restarts or the plugin is disabled,
 * so admins can review recent games even after they've ended.
 */
public class ReplayManager {

    /** Horizontal speed threshold (blocks/tick) above which a snapshot is flagged. */
    public static final double SPEED_FLAG  = 0.55;
    /** Reach distance (blocks) above which an attack is flagged. */
    public static final double REACH_FLAG  = 3.2;

    private final BedwarsPlugin plugin;
    private final Map<UUID, PlayerRecording> recordings = new HashMap<>();
    private BukkitTask recordTask;

    public ReplayManager(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Start the background recording task. Call once on plugin enable. */
    public void start() {
        if (recordTask != null) return;
        recordTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (BedwarsGame game : plugin.getGameManager().getGames()) {
                    if (game.getGameState() != GameState.PLAYING) continue;
                    for (UUID uuid : game.getAllPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null || !p.isOnline()) continue;
                        PlayerRecording rec = recordings.computeIfAbsent(uuid,
                                k -> new PlayerRecording(uuid, p.getName()));
                        double hSpeed = Math.sqrt(
                                p.getVelocity().getX() * p.getVelocity().getX()
                                + p.getVelocity().getZ() * p.getVelocity().getZ());
                        rec.addSnapshot(new PlayerRecording.PositionSnapshot(
                                now, p.getLocation().clone(), hSpeed));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L); // every 4 ticks = 200 ms → 5 snapshots/s
    }

    /** Stop the recording task. Call on plugin disable. */
    public void stop() {
        if (recordTask != null) { recordTask.cancel(); recordTask = null; }
    }

    /**
     * Record a melee/projectile hit event.
     * Called from PlayerListener when a player damages another player.
     */
    public void recordAttack(UUID attackerId, String attackerName,
                             double reach,
                             UUID victimId, String victimName, double victimHealth) {
        PlayerRecording rec = recordings.computeIfAbsent(attackerId,
                k -> new PlayerRecording(attackerId, attackerName));
        rec.addAttack(new PlayerRecording.AttackEvent(
                System.currentTimeMillis(), victimId, victimName, reach, victimHealth));
    }

    /** Look up a recording by UUID. May return null. */
    public PlayerRecording getRecording(UUID uuid) { return recordings.get(uuid); }

    /** Case-insensitive name lookup. May return null. */
    public PlayerRecording getRecordingByName(String name) {
        for (PlayerRecording rec : recordings.values()) {
            if (rec.getPlayerName().equalsIgnoreCase(name)) return rec;
        }
        return null;
    }

    public Collection<PlayerRecording> getAllRecordings() { return recordings.values(); }

    public void cleanup() {
        stop();
        recordings.clear();
    }
}
