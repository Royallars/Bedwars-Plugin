package com.bedwars.commands;

import com.bedwars.BedwarsPlugin;
import com.bedwars.replay.PlayerRecording;
import com.bedwars.replay.ReplayManager;
import com.bedwars.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * /bwreplay <player> [trail]
 *
 * Shows a text report of a player's recent movement speed, attack reach, and
 * suspicious-behaviour flags.  With the optional "trail" argument an admin
 * also sees a particle visualisation of the player's last ~10 seconds of movement.
 *
 * This command is intended for admins who want to investigate potential hackers
 * during or after a BedWars game.
 */
public class ReplayCommand implements CommandExecutor, TabCompleter {

    private final BedwarsPlugin plugin;

    public ReplayCommand(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bedwars.admin")) {
            sender.sendMessage(MessageUtils.color("&cYou don't have permission."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageUtils.color("&eUsage: /bwreplay <player> [trail]"));
            sender.sendMessage(MessageUtils.color("&7  trail — show a particle path of the player's recent movement"));
            return true;
        }

        // Resolve recording
        String targetName = args[0];
        PlayerRecording rec = plugin.getReplayManager().getRecordingByName(targetName);
        if (rec == null) {
            Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) rec = plugin.getReplayManager().getRecording(online.getUniqueId());
        }
        if (rec == null) {
            sender.sendMessage(MessageUtils.color("&cNo replay data for &e" + targetName
                    + "&c. The player may not have been in a game recently."));
            return true;
        }

        displayReport(sender, rec);

        boolean trail = args.length >= 2 && args[1].equalsIgnoreCase("trail");
        if (trail) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(MessageUtils.color("&cParticle trail can only be shown to an in-game admin."));
            } else {
                showParticleTrail(admin, rec);
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------

    private void displayReport(CommandSender sender, PlayerRecording rec) {
        List<PlayerRecording.PositionSnapshot> snaps   = rec.getSnapshotsCopy();
        List<PlayerRecording.AttackEvent>      attacks = rec.getAttacksCopy();

        double maxSpeed = rec.getMaxHSpeed();
        double avgSpeed = rec.getAvgHSpeed();
        double maxReach = rec.getMaxReach();
        double avgReach = rec.getAvgReach();

        boolean speedFlag = maxSpeed > ReplayManager.SPEED_FLAG;
        boolean reachFlag = maxReach > ReplayManager.REACH_FLAG;

        sender.sendMessage(MessageUtils.color("&6&m══════════════════════════════════"));
        sender.sendMessage(MessageUtils.color("    &6&lREPLAY REPORT &8— &e" + rec.getPlayerName()));
        sender.sendMessage(MessageUtils.color("&6&m══════════════════════════════════"));
        sender.sendMessage(MessageUtils.color(" &7Snapshots: &f" + snaps.size()
                + " &8(≈" + (snaps.size() / 5) + "s of data)  &7Attacks: &f" + attacks.size()));
        sender.sendMessage("");

        // ---- Movement ----
        String sc = speedFlag ? "&c" : "&a";
        sender.sendMessage(MessageUtils.color(" &e▶ Movement &8(horizontal blocks/tick)"));
        sender.sendMessage(MessageUtils.color("   Max speed: " + sc + String.format("%.4f", maxSpeed)
                + (speedFlag ? "  &c⚠ FLAGGED &8(>" + ReplayManager.SPEED_FLAG + ")" : "  &a✔")));
        sender.sendMessage(MessageUtils.color("   Avg speed: &f" + String.format("%.4f", avgSpeed)));

        // ---- Combat ----
        sender.sendMessage("");
        sender.sendMessage(MessageUtils.color(" &e▶ Combat &8(attack reach, blocks)"));
        if (attacks.isEmpty()) {
            sender.sendMessage(MessageUtils.color("   &7No attack data recorded."));
        } else {
            String rc = reachFlag ? "&c" : "&a";
            sender.sendMessage(MessageUtils.color("   Max reach: " + rc + String.format("%.2f", maxReach)
                    + (reachFlag ? "  &c⚠ FLAGGED &8(>" + ReplayManager.REACH_FLAG + ")" : "  &a✔")));
            sender.sendMessage(MessageUtils.color("   Avg reach: &f" + String.format("%.2f", avgReach)));

            // Last 10 attack events
            sender.sendMessage("");
            sender.sendMessage(MessageUtils.color(" &e▶ Recent attacks &8(last 10)"));
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
            List<PlayerRecording.AttackEvent> recent =
                    attacks.subList(Math.max(0, attacks.size() - 10), attacks.size());
            for (PlayerRecording.AttackEvent ev : recent) {
                String rStr = String.format("%.2f", ev.reach());
                String rCol = ev.reach() > ReplayManager.REACH_FLAG ? "&c" : "&7";
                sender.sendMessage(MessageUtils.color("   &8[" + fmt.format(new Date(ev.millis())) + "] "
                        + "&7→ &f" + ev.victimName()
                        + "  reach: " + rCol + rStr + "  &7hp: &f" + String.format("%.1f", ev.victimHealth())));
            }
        }

        // ---- Summary ----
        sender.sendMessage("");
        if (speedFlag || reachFlag) {
            sender.sendMessage(MessageUtils.color(" &c&l⚠ SUSPICIOUS BEHAVIOUR DETECTED"));
            if (speedFlag) sender.sendMessage(MessageUtils.color("   &c• Movement speed exceeds vanilla maximum"));
            if (reachFlag) sender.sendMessage(MessageUtils.color("   &c• Attack reach exceeds normal range (3.0 blocks)"));
        } else {
            sender.sendMessage(MessageUtils.color(" &a✔ No obvious anomalies detected."));
        }
        sender.sendMessage(MessageUtils.color(" &7Tip: /bwreplay " + rec.getPlayerName()
                + " trail &7to visualise their movement path"));
        sender.sendMessage(MessageUtils.color("&6&m══════════════════════════════════"));
    }

    /**
     * Spawns particles at the player's recorded positions so the admin can see
     * exactly where they moved.  FLAME particles mark high-speed positions,
     * HAPPY_VILLAGER/VILLAGER_HAPPY (green sparkle) marks normal-speed positions.
     *
     * Particle enum names changed in 1.20.5 (VILLAGER_HAPPY → HAPPY_VILLAGER).
     * We resolve via reflection at runtime so this code compiles against any
     * Spigot version (1.20–1.21+) and picks the right constant automatically.
     */
    private void showParticleTrail(Player admin, PlayerRecording rec) {
        List<PlayerRecording.PositionSnapshot> snaps = rec.getSnapshotsCopy();
        if (snaps.isEmpty()) {
            admin.sendMessage(MessageUtils.color("&cNo position data to visualise."));
            return;
        }
        // Show last ~10 seconds = 50 snapshots at 5/s
        List<PlayerRecording.PositionSnapshot> trail =
                snaps.subList(Math.max(0, snaps.size() - 50), snaps.size());

        Object flame  = resolveParticle("FLAME");
        // HAPPY_VILLAGER (1.20.5+) falls back to VILLAGER_HAPPY (1.20.0–1.20.4)
        Object normal = resolveParticle("HAPPY_VILLAGER", "VILLAGER_HAPPY", "FIREWORKS_SPARK");
        if (flame == null || normal == null) {
            admin.sendMessage(MessageUtils.color("&cParticle API unavailable on this server version."));
            return;
        }

        java.lang.reflect.Method spawnMethod = findSpawnParticleMethod(admin);
        if (spawnMethod == null) {
            admin.sendMessage(MessageUtils.color("&cCannot find spawnParticle method."));
            return;
        }

        for (PlayerRecording.PositionSnapshot snap : trail) {
            Object p = snap.hSpeed() > ReplayManager.SPEED_FLAG ? flame : normal;
            try {
                spawnMethod.invoke(admin, p, snap.loc(), 4, 0.1, 0.1, 0.1, 0.0);
            } catch (Exception ignored) {}
        }

        admin.sendMessage(MessageUtils.color("&aParticle trail spawned for &e" + rec.getPlayerName()
                + " &a(" + trail.size() + " positions)."));
        admin.sendMessage(MessageUtils.color("&c● Flame &8= high speed  &a● Green &8= normal speed"));
    }

    /**
     * Looks up a Particle enum constant by name using reflection.
     * Tries each name in order and returns the first match, or null if none found.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolveParticle(String... names) {
        try {
            Class<Enum> cls = (Class<Enum>) Class.forName("org.bukkit.Particle");
            for (String name : names) {
                try { return Enum.valueOf(cls, name); } catch (IllegalArgumentException ignored) {}
            }
        } catch (ClassNotFoundException ignored) {}
        return null;
    }

    /**
     * Finds the Player#spawnParticle(Particle, Location, int, double, double, double, double)
     * method via reflection so we don't need a compile-time dependency on the Particle class.
     */
    private static java.lang.reflect.Method findSpawnParticleMethod(Player player) {
        for (java.lang.reflect.Method m : player.getClass().getMethods()) {
            if (!m.getName().equals("spawnParticle")) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 7
                    && pt[1] == Location.class
                    && pt[2] == int.class
                    && pt[3] == double.class) {
                return m;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (PlayerRecording rec : plugin.getReplayManager().getAllRecordings()) {
                if (rec.getPlayerName().toLowerCase().startsWith(partial)) {
                    names.add(rec.getPlayerName());
                }
            }
            return names;
        }
        if (args.length == 2) return List.of("trail");
        return Collections.emptyList();
    }
}
