package com.bedwars.game;

import com.bedwars.BedwarsPlugin;
import com.bedwars.generators.GeneratorType;
import com.bedwars.generators.ResourceGenerator;
import com.bedwars.gui.HotbarManager;
import com.bedwars.scoreboard.GameScoreboard;
import com.bedwars.shop.ShopManager;
import com.bedwars.shop.UpgradeShopManager;
import com.bedwars.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BedwarsGame {

    private final BedwarsPlugin plugin;
    private final String arenaName;
    private final World world;
    private final int minPlayers;
    private final int maxPlayers;

    private GameState state = GameState.WAITING;
    private final List<BedwarsTeam> teams = new ArrayList<>();
    private final Map<UUID, BedwarsTeam> playerTeamMap = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Boolean> spectators = new HashMap<>();
    private final Set<Location> placedBlocks = new HashSet<>();

    private Location lobbyLocation;
    private Location spectatorLocation;

    private final List<ResourceGenerator> generators = new ArrayList<>();
    private final List<GeneratorLocation> pendingGeneratorLocations = new ArrayList<>();

    private GameScoreboard scoreboard;
    private ShopManager shopManager;
    private UpgradeShopManager upgradeShopManager;

    private int countdown = 30;
    private int elapsedSeconds = 0;
    private BedwarsTeam winner;

    private BukkitTask countdownTask;
    private BukkitTask gameTask;
    private BukkitTask scoreboardTask;
    private BukkitTask compassTask;
    private BukkitTask hudTask;

    // Track respawning players
    private final Map<UUID, Integer> respawnCountdowns = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    // Grace period (first 5 seconds of game — no PvP)
    private boolean gracePeriod = false;

    // Kill streaks: player UUID → consecutive kills without dying
    private final Map<UUID, Integer> killStreaks = new HashMap<>();

    // Dream Defenders: golem UUID → owning team
    private final Map<UUID, BedwarsTeam> dreamDefenders = new HashMap<>();

    // Feature: Spawn Shield — players who just respawned get 3s invincibility
    private final Set<UUID> spawnShieldPlayers = new HashSet<>();

    // Feature: Rush Mode — all generators run at 2x speed
    private boolean rushMode = false;

    // Feature: Private Game Password (null = no password)
    private String password = null;

    // Feature: Bed Guard — track guard blocks placed around beds
    private final Set<Location> bedGuardBlocks = new HashSet<>();

    public BedwarsGame(BedwarsPlugin plugin, String arenaName, World world, int minPlayers, int maxPlayers) {
        this.plugin = plugin;
        this.arenaName = arenaName;
        this.world = world;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.scoreboard = new GameScoreboard(this);
        this.shopManager = new ShopManager(this);
        this.upgradeShopManager = new UpgradeShopManager(this);
    }

    // ============================================================
    // PLAYER MANAGEMENT
    // ============================================================

    public boolean addPlayer(Player player) {
        if (state != GameState.WAITING && state != GameState.STARTING) return false;
        if (getAllPlayers().size() >= maxPlayers) return false;
        if (getAllPlayers().contains(player.getUniqueId())) return false;

        // Assign to team with fewest players
        BedwarsTeam team = getSmallestTeam();
        if (team == null) return false;

        team.addPlayer(player.getUniqueId());
        playerTeamMap.put(player.getUniqueId(), team);
        playerKills.put(player.getUniqueId(), 0);

        // Teleport to lobby
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }

        setupPlayerForLobby(player);
        scoreboard.setupLobbyScoreboard(player);

        broadcast(MessageUtils.color("&a" + player.getName() + " &7joined the game! &8[&e" +
                getAllPlayers().size() + "&7/&e" + maxPlayers + "&8]"));

        // Start countdown if enough players
        if (getAllPlayers().size() >= minPlayers && state == GameState.WAITING) {
            startCountdown();
        }

        return true;
    }

    public void removePlayer(Player player, boolean sendToLobby) {
        UUID uuid = player.getUniqueId();
        BedwarsTeam team = playerTeamMap.remove(uuid);
        if (team != null) {
            team.removePlayer(uuid);
        }
        playerKills.remove(uuid);
        spectators.remove(uuid);

        // Cancel respawn task if any
        BukkitTask task = respawnTasks.remove(uuid);
        if (task != null) task.cancel();

        shopManager.clearPlayerData(uuid);
        scoreboard.removePlayer(uuid);

        if (sendToLobby) {
            sendToMainLobby(player);
        }

        broadcast(MessageUtils.color("&c" + player.getName() + " &7left the game."));

        // Check if game should end
        if (state == GameState.PLAYING) {
            checkWinCondition();
        }

        // If not enough players during countdown, cancel
        if (state == GameState.STARTING && getAllPlayers().size() < minPlayers) {
            cancelCountdown();
        }
    }

    public void eliminatePlayer(Player player, Player killer) {
        UUID uuid = player.getUniqueId();
        BedwarsTeam team = playerTeamMap.get(uuid);
        if (team == null) return;

        if (killer != null) {
            BedwarsTeam killerTeam = playerTeamMap.get(killer.getUniqueId());
            if (killerTeam != null) {
                killerTeam.addFinalKill();
            }
            playerKills.merge(killer.getUniqueId(), 1, Integer::sum);
            int streak = killStreaks.merge(killer.getUniqueId(), 1, Integer::sum);
            announceKillStreak(killer, streak);
            broadcast(MessageUtils.color("&c&l" + killer.getName() + " &r&eFinally killed &c&l" + player.getName() + "&r&e!"));

            MessageUtils.sendTitle(killer, "&6&lFINAL KILL", "&e+" + 1 + " Kill", 10, 40, 10);
            MessageUtils.playSound(killer, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            // Feature 1: Kill Rewards — final kill gives gold + iron
            giveKillReward(killer, true);
        } else {
            broadcast(MessageUtils.color("&7&l" + player.getName() + " &r&7was eliminated!"));
        }

        // Reset victim's kill streak
        killStreaks.remove(uuid);

        team.eliminatePlayer(uuid);

        // Send death title
        MessageUtils.sendTitle(player, "&c&lYOU DIED!", "&7You have been eliminated", 10, 60, 10);
        MessageUtils.playSound(player, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);

        // Make spectator
        makeSpectator(player);

        // Check if team is eliminated
        if (team.isEliminated()) {
            broadcastTeamEliminated(team);
        }

        checkWinCondition();
        scoreboard.updateAll();
    }

    public void handleDeath(Player player, Player killer) {
        UUID uuid = player.getUniqueId();
        BedwarsTeam team = playerTeamMap.get(uuid);
        if (team == null) return;

        if (!team.isBedAlive()) {
            // Final death - eliminate
            eliminatePlayer(player, killer);
            return;
        }

        // Bed alive - respawn after countdown
        if (killer != null) {
            BedwarsTeam killerTeam = playerTeamMap.get(killer.getUniqueId());
            if (killerTeam != null) {
                killerTeam.addKill();
            }
            playerKills.merge(killer.getUniqueId(), 1, Integer::sum);
            int streak = killStreaks.merge(killer.getUniqueId(), 1, Integer::sum);
            announceKillStreak(killer, streak);
            // Feature 1: Kill Rewards — regular kill gives iron
            giveKillReward(killer, false);
        }

        // Reset victim's kill streak
        killStreaks.remove(uuid);

        int respawnTime = plugin.getConfig().getInt("game.respawn-time", 5);
        startRespawnCountdown(player, respawnTime);
        scoreboard.updateAll();
    }

    private void startRespawnCountdown(Player player, int seconds) {
        UUID uuid = player.getUniqueId();
        respawnCountdowns.put(uuid, seconds);

        // Put player in spectator mode temporarily
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spectatorLocation != null ? spectatorLocation : lobbyLocation);

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!player.isOnline() || !playerTeamMap.containsKey(uuid)) {
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    respawnPlayer(player);
                    respawnCountdowns.remove(uuid);
                    respawnTasks.remove(uuid);
                    cancel();
                    return;
                }

                MessageUtils.sendTitle(player, "&c&lYOU DIED!", "&eRespawning in &6" + remaining + " &esecond" + (remaining == 1 ? "" : "s"), 0, 25, 0);
                MessageUtils.sendActionBar(player, MessageUtils.color("&eRespawning in &6" + remaining + " &esecond" + (remaining == 1 ? "" : "s")));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        BukkitTask existing = respawnTasks.put(uuid, task);
        if (existing != null) existing.cancel();
    }

    private void respawnPlayer(Player player) {
        BedwarsTeam team = playerTeamMap.get(player.getUniqueId());
        if (team == null) return;

        player.setGameMode(GameMode.SURVIVAL);
        Location spawn = team.getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
        }

        setupPlayerForGame(player, team);
        HotbarManager.giveIngameItems(player);

        MessageUtils.sendTitle(player, "&a&lRESPAWNED!", "&b✦ Spawn Shield active for 3s", 10, 20, 10);
        MessageUtils.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT);
        scoreboard.update(player);
        // Feature 2: Spawn Shield — 3-second damage immunity after respawn
        activateSpawnShield(player);
    }

    // ============================================================
    // GAME FLOW
    // ============================================================

    private void startCountdown() {
        state = GameState.STARTING;
        countdown = plugin.getConfig().getInt("game.countdown", 30);

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (getAllPlayers().size() < minPlayers) {
                    cancelCountdown();
                    return;
                }

                if (countdown <= 0) {
                    startGame();
                    cancel();
                    return;
                }

                // Announce at specific intervals
                if (countdown <= 5 || countdown == 10 || countdown == 15 || countdown == 30) {
                    for (UUID uuid : getAllPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null) continue;
                        MessageUtils.sendTitle(p, "&e&l" + countdown, "&7Game starting soon...", 5, 15, 5);
                        MessageUtils.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, countdown <= 5 ? 1.5f : 1.0f);
                    }
                    broadcast(MessageUtils.color("&eGame starting in &6" + countdown + " &esecond" + (countdown == 1 ? "" : "s") + "!"));
                }

                countdown--;
                scoreboard.updateAll();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        state = GameState.WAITING;
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        broadcast(MessageUtils.color("&cNot enough players to start the game!"));
        scoreboard.updateAll();
    }

    public void startGame() {
        state = GameState.PLAYING;
        elapsedSeconds = 0;
        gracePeriod = true;

        // Cancel countdown task
        if (countdownTask != null) countdownTask.cancel();

        // Feature 7: Auto Team Balance — spread out lone players
        autoBalanceTeams();

        // Feature 9: Bed Guard — place protective wool around beds
        placeBedGuardBlocks();

        // Feature 6: Rush Mode announcement
        if (rushMode) {
            broadcast(MessageUtils.color("&c&l⚡ RUSH MODE &r&c— All generators are running at &e2x speed&c!"));
        }

        // Start resource generators (Feature 6: Rush Mode applies doubled speed)
        for (ResourceGenerator gen : generators) {
            if (rushMode) gen.applyRushMode();
            gen.start();
        }

        // Teleport players to their team spawns and setup
        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            BedwarsTeam team = playerTeamMap.get(uuid);
            if (team == null) continue;

            Location spawn = team.getSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
            setupPlayerForGame(player, team);
            HotbarManager.giveIngameItems(player);
        }

        broadcast(MessageUtils.color("&6&lThe game has started! &eMay the best team win!"));
        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            MessageUtils.sendTitle(player, "&6&lBED WARS", "&eProtect your bed, destroy theirs!", 10, 60, 10);
            MessageUtils.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
        }

        // End grace period after 5 seconds
        int graceDuration = plugin.getConfig().getInt("game.grace-period", 5);
        new BukkitRunnable() {
            @Override
            public void run() {
                gracePeriod = false;
                broadcast(MessageUtils.color("&c&lGrace period has ended! &rPvP is now enabled!"));
                for (UUID uuid : getAllPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) MessageUtils.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                }
            }
        }.runTaskLater(plugin, graceDuration * 20L);

        // Start game timer task
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                elapsedSeconds++;
                applyOngoingEffects();

                // Feature 5: Game Timer Announcements
                int maxTime = plugin.getConfig().getInt("game.max-time", 40);
                if (maxTime > 0) {
                    int remaining = maxTime * 60 - elapsedSeconds;
                    if (remaining == 1800) {
                        broadcast(MessageUtils.color("&e&l30 minutes &r&eremaining!"));
                    } else if (remaining == 600) {
                        broadcast(MessageUtils.color("&6&l10 minutes &r&eremaining!"));
                        for (UUID uid : getAllPlayers()) { Player p = Bukkit.getPlayer(uid); if (p != null) MessageUtils.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f); }
                    } else if (remaining == 300) {
                        broadcast(MessageUtils.color("&c&l5 minutes &r&eremaining!"));
                        for (UUID uid : getAllPlayers()) { Player p = Bukkit.getPlayer(uid); if (p != null) MessageUtils.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.3f); }
                    } else if (remaining == 60) {
                        broadcast(MessageUtils.color("&4&l1 minute &r&eremaining! &cGame will end soon!"));
                        for (UUID uid : getAllPlayers()) { Player p = Bukkit.getPlayer(uid); if (p != null) { MessageUtils.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f); MessageUtils.sendTitle(p, "&4&l1 MINUTE!", "&cGame ending soon!", 10, 40, 10); } }
                    } else if (remaining <= 0) {
                        endGameByKills();
                        cancel();
                        return;
                    }
                }

                // Update scoreboard every 5 seconds
                if (elapsedSeconds % 5 == 0) {
                    scoreboard.updateAll();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Start scoreboard update task
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                scoreboard.updateAll();
            }
        }.runTaskTimer(plugin, 0L, 40L);

        // Compass enemy-tracking task — every second point each player's compass
        // toward their nearest living enemy (skips spectators and grace period)
        compassTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gracePeriod) return;
                for (UUID uuid : getAllPlayers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || player.getGameMode() == GameMode.SPECTATOR) continue;
                    BedwarsTeam myTeam = playerTeamMap.get(uuid);
                    if (myTeam == null) continue;

                    Location nearest = null;
                    double nearestDist = Double.MAX_VALUE;

                    for (UUID enemyUuid : getAllPlayers()) {
                        if (enemyUuid.equals(uuid)) continue;
                        BedwarsTeam enemyTeam = playerTeamMap.get(enemyUuid);
                        if (enemyTeam == null || enemyTeam == myTeam) continue;
                        Player enemy = Bukkit.getPlayer(enemyUuid);
                        if (enemy == null || enemy.getGameMode() == GameMode.SPECTATOR) continue;
                        double dist = player.getLocation().distanceSquared(enemy.getLocation());
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = enemy.getLocation();
                        }
                    }

                    if (nearest != null) {
                        player.setCompassTarget(nearest);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Action-bar HUD task — every second show iron/gold/diamond counts
        hudTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : getAllPlayers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || player.getGameMode() == GameMode.SPECTATOR) continue;
                    sendResourceHud(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void applyOngoingEffects() {
        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) continue;

            BedwarsTeam team = playerTeamMap.get(uuid);
            if (team == null) continue;

            // Heal pool
            if (team.hasHealPool() && team.getSpawnLocation() != null) {
                if (player.getLocation().distance(team.getSpawnLocation()) <= 15) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
                }
            }

            // Haste
            if (team.getHasteLevel() > 0 && team.getSpawnLocation() != null) {
                if (player.getLocation().distance(team.getSpawnLocation()) <= 20) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING,
                            60, team.getHasteLevel() - 1, true, false));
                }
            }

            // Feature 3: Void Protection — teleport back to island instead of instant void death
            int voidY = plugin.getConfig().getInt("game.void-y", -64);
            if (player.getLocation().getY() < voidY) {
                Location safeSpot = team.getSpawnLocation();
                if (safeSpot != null) {
                    player.teleport(safeSpot);
                    activateSpawnShield(player);
                    MessageUtils.sendTitle(player, "&c&lVOID!", "&eYou were teleported back!", 5, 25, 5);
                    MessageUtils.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                } else {
                    player.setHealth(0);
                }
            }
        }

        // Trap activation: check every second if an enemy is near a team's spawn
        for (BedwarsTeam defTeam : teams) {
            if (!defTeam.hasTraps() || defTeam.getSpawnLocation() == null) continue;

            for (UUID uuid : getAllPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || p.getGameMode() == GameMode.SPECTATOR) continue;
                BedwarsTeam playerTeam = playerTeamMap.get(uuid);
                if (playerTeam == null || playerTeam == defTeam) continue; // ignore teammates

                double dist = p.getLocation().distanceSquared(defTeam.getSpawnLocation());
                if (dist <= 100) { // 10 block radius
                    TrapType trap = defTeam.pollTrap();
                    if (trap != null) {
                        activateTrap(trap, defTeam, p);
                    }
                    break; // only trigger once per team per tick
                }
            }
        }
    }

    private void activateTrap(TrapType trap, BedwarsTeam team, Player trigger) {
        broadcastToTeam(team, MessageUtils.color(trap.getActivationMessage()));
        trigger.sendMessage(MessageUtils.color("&c&lTRAP! &r&cYou triggered the " + team.getColor().getDisplayName() + "'s &c" + trap.getDisplayName() + "!"));

        switch (trap) {
            case ALARM -> {
                for (UUID uuid : team.getPlayers()) {
                    Player defender = Bukkit.getPlayer(uuid);
                    if (defender != null) {
                        MessageUtils.playSound(defender, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                        defender.sendMessage(MessageUtils.color("&c" + trigger.getName() + " is in your base!"));
                    }
                }
            }
            case COUNTER_OFFENSE -> {
                for (UUID uuid : team.getPlayers()) {
                    Player defender = Bukkit.getPlayer(uuid);
                    if (defender == null) continue;
                    defender.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, true, false));
                    defender.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 200, 1, true, false));
                }
            }
            case MINER_FATIGUE -> {
                trigger.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 200, 2, true, false));
                MessageUtils.playSound(trigger, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
            }
            case REGEN_BOOST -> {
                for (UUID uuid : team.getPlayers()) {
                    Player defender = Bukkit.getPlayer(uuid);
                    if (defender == null) continue;
                    defender.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, true, false));
                }
            }
        }
    }

    public void endGame(BedwarsTeam winnerTeam) {
        if (state == GameState.ENDING) return;
        state = GameState.ENDING;
        winner = winnerTeam;

        // Stop generators
        for (ResourceGenerator gen : generators) {
            gen.stop();
        }

        // Cancel game tasks
        if (gameTask != null) gameTask.cancel();
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (compassTask != null) compassTask.cancel();
        if (hudTask != null) hudTask.cancel();

        // Announce winner
        broadcast(MessageUtils.color("&6&l" + (winnerTeam != null ? winnerTeam.getColor().getDisplayName() : "&7Nobody") + " &r&6has won the game!"));

        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            boolean isWinner = winnerTeam != null && winnerTeam.hasPlayer(uuid);
            if (isWinner) {
                MessageUtils.sendTitle(player, "&6&lVICTORY!", "&eYour team won!", 10, 100, 10);
                MessageUtils.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.setGameMode(GameMode.SURVIVAL);
            } else {
                MessageUtils.sendTitle(player, "&c&lGAME OVER!", "&7Better luck next time!", 10, 100, 10);
                MessageUtils.playSound(player, Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
                player.setGameMode(GameMode.SPECTATOR);
            }
        }

        scoreboard.updateAll();

        // Record persistent stats
        plugin.getStatsManager().recordGameEnd(this, winnerTeam);

        // Show stats 3 seconds after game ends
        new BukkitRunnable() {
            @Override
            public void run() {
                broadcastEndStats();
            }
        }.runTaskLater(plugin, 60L);

        // Schedule restart
        new BukkitRunnable() {
            @Override
            public void run() {
                resetGame();
            }
        }.runTaskLater(plugin, 200L); // 10 seconds
    }

    private void broadcastEndStats() {
        broadcast(MessageUtils.color("&6&m════════════════════════════"));
        broadcast(MessageUtils.color("         &6&lGAME RESULTS"));
        broadcast(MessageUtils.color("&6&m════════════════════════════"));

        for (BedwarsTeam team : teams) {
            String bedStatus = team.isBedAlive() ? "&a✔" : "&c✗";
            broadcast(MessageUtils.color(team.getColor().getDisplayName() +
                    " &r&7| Kills: &f" + team.getKills() +
                    " &7| Finals: &f" + team.getFinalKills() +
                    " &7| Beds: &f" + team.getBedsDestroyed() +
                    " &7| Bed: " + bedStatus));
        }

        broadcast(MessageUtils.color("&6&m════════════════════════════"));
        broadcast(MessageUtils.color("  &e&lTop Players"));

        playerKills.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    String name = p != null ? p.getName() : "Unknown";
                    BedwarsTeam t = playerTeamMap.get(entry.getKey());
                    String prefix = t != null ? t.getColor().getChatColor().toString() : "§7";
                    broadcast(MessageUtils.color("  " + prefix + name + " &7- &f" + entry.getValue() + " kills"));
                });

        broadcast(MessageUtils.color("&6&m════════════════════════════"));
    }

    private void endGameByKills() {
        // Find team with most kills
        BedwarsTeam topTeam = null;
        int topKills = -1;
        for (BedwarsTeam team : getAliveTeams()) {
            if (team.getKills() > topKills) {
                topKills = team.getKills();
                topTeam = team;
            }
        }
        endGame(topTeam);
    }

    public void checkWinCondition() {
        List<BedwarsTeam> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            endGame(aliveTeams.get(0));
        } else if (aliveTeams.isEmpty()) {
            endGame(null);
        }
    }

    private List<BedwarsTeam> getAliveTeams() {
        List<BedwarsTeam> alive = new ArrayList<>();
        for (BedwarsTeam team : teams) {
            if (!team.getPlayers().isEmpty() && !team.isEliminated()) {
                alive.add(team);
            }
        }
        return alive;
    }

    public void resetGame() {
        state = GameState.RESTARTING;

        // Stop generators
        for (ResourceGenerator gen : generators) {
            gen.stop();
        }
        generators.clear();

        // Send all players to main lobby
        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendToMainLobby(player);
            }
        }

        // Kill Dream Defenders
        for (UUID defenderUUID : dreamDefenders.keySet()) {
            for (Entity e : world.getEntities()) {
                if (e.getUniqueId().equals(defenderUUID)) {
                    e.remove();
                    break;
                }
            }
        }
        dreamDefenders.clear();

        // Clean up placed blocks
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            block.setType(Material.AIR);
        }
        placedBlocks.clear();

        // Feature 9: Remove bed guard blocks
        for (Location loc : bedGuardBlocks) {
            loc.getBlock().setType(Material.AIR);
        }
        bedGuardBlocks.clear();

        // Reset team state — keep spawn/bed locations but clear players/stats/upgrades
        for (BedwarsTeam team : teams) {
            if (!team.isBedAlive()) {
                restoreBed(team); // physically replace the bed block
            }
            team.reset(); // clears players, kills, upgrades, traps, restores bedAlive flag
        }
        // NOTE: teams list is NOT cleared — the arena configuration stays intact

        playerTeamMap.clear();
        playerKills.clear();
        killStreaks.clear();
        spectators.clear();
        respawnCountdowns.clear();
        respawnTasks.values().forEach(BukkitTask::cancel);
        respawnTasks.clear();
        gracePeriod = false;
        winner = null;
        elapsedSeconds = 0;

        scoreboard.cleanup();
        scoreboard = new GameScoreboard(this);
        shopManager = new ShopManager(this);
        upgradeShopManager = new UpgradeShopManager(this);

        // Re-register generators from pending locations (preserve original team assignment)
        for (GeneratorLocation gl : pendingGeneratorLocations) {
            ResourceGenerator gen = new ResourceGenerator(this, gl.location(), gl.type(), gl.team());
            generators.add(gen);
        }

        state = GameState.WAITING;
    }

    private void restoreBed(BedwarsTeam team) {
        Location bedLoc = team.getBedLocation();
        if (bedLoc != null) {
            bedLoc.getBlock().setType(team.getColor().getBedMaterial());
        }
    }

    // ============================================================
    // BED DESTRUCTION
    // ============================================================

    public void destroyBed(BedwarsTeam team, Player destroyer) {
        team.destroyBed();

        // Firework effect at bed location
        Location bedLoc = team.getBedLocation();
        if (bedLoc != null) {
            spawnBedFirework(bedLoc, team.getColor());
        }

        if (destroyer != null) {
            BedwarsTeam destroyerTeam = playerTeamMap.get(destroyer.getUniqueId());
            if (destroyerTeam != null) {
                destroyerTeam.addBedDestroyed();
            }
            broadcast(MessageUtils.color("&c&l" + team.getColor().getDisplayName() + "&r&c's BED was destroyed by &e&l" + destroyer.getName() + "&r&c!"));
        } else {
            broadcast(MessageUtils.color("&c&l" + team.getColor().getDisplayName() + "&r&c's BED was destroyed!"));
        }

        // Notify team members
        for (UUID uuid : team.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            MessageUtils.sendTitle(player, "&c&lBED DESTROYED!", "&7You will no longer respawn!", 10, 60, 10);
            MessageUtils.playSound(player, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }

        // Show title to destroyer
        if (destroyer != null) {
            MessageUtils.sendTitle(destroyer, "&a&lBED DESTROYED!", "&7+" + 1 + " Bed Broken", 10, 40, 10);
            MessageUtils.playSound(destroyer, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        scoreboard.updateAll();
    }

    // ============================================================
    // SETUP HELPERS
    // ============================================================

    private void setupPlayerForLobby(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExp(0f);
        player.setLevel(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        // Give compass to open team selector / arena menu
        HotbarManager.giveWaitingItems(player);
    }

    private void setupPlayerForGame(Player player, BedwarsTeam team) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExp(0f);
        player.setLevel(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Give basic items: 16 wool of team color
        player.getInventory().addItem(new ItemStack(team.getColor().getWoolMaterial(), 16));
    }

    private void makeSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        spectators.put(player.getUniqueId(), true);
        if (spectatorLocation != null) {
            player.teleport(spectatorLocation);
        }
        HotbarManager.giveSpectatorItems(player);
        // Feature 4: Spectator Buffs — night vision so spectators can see clearly
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
    }

    private void sendToMainLobby(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        Location mainLobby = plugin.getConfigManager().getLobbyLocation();
        if (mainLobby != null) {
            player.teleport(mainLobby);
        }

        // Restore lobby hotbar (compass to open arena selector)
        HotbarManager.giveLobbyItems(player);
        player.sendMessage(MessageUtils.color("&aYou have been sent to the lobby."));
    }

    private void broadcastTeamEliminated(BedwarsTeam team) {
        broadcast(MessageUtils.color("&c&l" + team.getColor().getDisplayName() + " &r&cTEAM has been eliminated!"));
    }

    private void announceKillStreak(Player player, int streak) {
        String title = null;
        String msg = null;
        switch (streak) {
            case 3  -> { title = "&63 KILL STREAK!"; msg = "&e" + player.getName() + " &7is on a &63 kill streak!"; }
            case 5  -> { title = "&65 KILL STREAK!"; msg = "&e" + player.getName() + " &7is on a &65 kill streak!"; }
            case 7  -> { title = "&c7 KILL STREAK!"; msg = "&e" + player.getName() + " &7is on a &c7 kill streak!"; }
            case 10 -> { title = "&c&l10 KILL STREAK!"; msg = "&e&l" + player.getName() + " &r&7is on a &c&l10 kill streak!"; }
            default -> {
                if (streak > 10 && streak % 5 == 0) {
                    title = "&c&l" + streak + " KILL STREAK!";
                    msg = "&e&l" + player.getName() + " &r&7is on a &c&l" + streak + " kill streak!";
                }
            }
        }
        if (msg != null) {
            broadcast(MessageUtils.color(msg));
            MessageUtils.sendTitle(player, MessageUtils.color(title), "", 5, 30, 5);
            MessageUtils.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
        }
    }

    /**
     * Sends an action-bar line showing the player's current iron / gold / diamond count,
     * plus whether their bed is still alive.
     */
    private void sendResourceHud(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        int iron     = countMaterial(inv, org.bukkit.Material.IRON_INGOT);
        int gold     = countMaterial(inv, org.bukkit.Material.GOLD_INGOT);
        int diamond  = countMaterial(inv, org.bukkit.Material.DIAMOND);
        int emerald  = countMaterial(inv, org.bukkit.Material.EMERALD);

        BedwarsTeam team = playerTeamMap.get(player.getUniqueId());
        String bedIcon = (team != null && team.isBedAlive()) ? "&a✔ Bed" : "&c✗ Bed";

        String hud = MessageUtils.color(
            "&fIron: &7" + iron +
            "  &6Gold: &7" + gold +
            (diamond  > 0 ? "  &bDiamond: &7"  + diamond  : "") +
            (emerald  > 0 ? "  &aEmerald: &7"  + emerald  : "") +
            "   " + bedIcon
        );
        MessageUtils.sendActionBar(player, hud);
    }

    private int countMaterial(org.bukkit.inventory.PlayerInventory inv, org.bukkit.Material mat) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == mat) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void spawnBedFirework(Location loc, TeamColor color) {
        World w = loc.getWorld();
        if (w == null) return;
        Firework fw = (Firework) w.spawnEntity(loc.clone().add(0, 1, 0), EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder()
                .withColor(color.getFireworkColor())
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .build());
        fw.setFireworkMeta(meta);
    }

    // ============================================================
    // TEAM MANAGEMENT
    // ============================================================

    private BedwarsTeam getSmallestTeam() {
        BedwarsTeam smallest = null;
        int minSize = Integer.MAX_VALUE;
        for (BedwarsTeam team : teams) {
            if (team.getSize() < minSize) {
                minSize = team.getSize();
                smallest = team;
            }
        }
        return smallest;
    }

    /**
     * Atomically moves a player from their current team to the given color's team.
     * Updates both the team roster and the player-team map.
     * Returns false if the target team is full.
     */
    public boolean switchPlayerTeam(UUID uuid, TeamColor newColor) {
        BedwarsTeam newTeam = getOrCreateTeam(newColor);
        int maxPerTeam = maxPlayers / Math.max(1, teams.size());
        if (newTeam.getSize() >= maxPerTeam) return false;

        BedwarsTeam currentTeam = playerTeamMap.get(uuid);
        if (currentTeam != null) {
            currentTeam.removePlayer(uuid);
        }
        newTeam.addPlayer(uuid);
        playerTeamMap.put(uuid, newTeam);
        return true;
    }

    /** Public entry point to make a player a spectator (e.g. from GUI). */
    public void setSpectatorMode(Player player) {
        makeSpectator(player);
    }

    /** Returns true if the grace period (no-PvP at game start) is active. */
    public boolean isInGracePeriod() {
        return gracePeriod;
    }

    public BedwarsTeam getOrCreateTeam(TeamColor color) {
        for (BedwarsTeam team : teams) {
            if (team.getColor() == color) return team;
        }
        BedwarsTeam team = new BedwarsTeam(color);
        teams.add(team);
        return team;
    }

    public BedwarsTeam getPlayerTeam(UUID uuid) {
        return playerTeamMap.get(uuid);
    }

    public BedwarsTeam getTeamByBedLocation(Location location) {
        for (BedwarsTeam team : teams) {
            Location bedLoc = team.getBedLocation();
            if (bedLoc != null && isSameBlock(bedLoc, location)) {
                return team;
            }
        }
        return null;
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() &&
                a.getBlockY() == b.getBlockY() &&
                a.getBlockZ() == b.getBlockZ() &&
                a.getWorld() != null && b.getWorld() != null &&
                a.getWorld().equals(b.getWorld());
    }

    public void broadcastToTeam(BedwarsTeam team, String message) {
        for (UUID uuid : team.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public void broadcast(String message) {
        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    // ============================================================
    // GENERATOR MANAGEMENT
    // ============================================================

    public void addGeneratorLocation(GeneratorType type, Location location, BedwarsTeam team) {
        pendingGeneratorLocations.add(new GeneratorLocation(type, location, team));
        ResourceGenerator gen = new ResourceGenerator(this, location, type, team);
        generators.add(gen);
        if (state == GameState.PLAYING) {
            gen.start(); // hot-add during a live game
        }
    }

    // ============================================================
    // BLOCK TRACKING
    // ============================================================

    public void trackPlacedBlock(Location location) {
        placedBlocks.add(location.clone());
    }

    public boolean isPlacedBlock(Location location) {
        return placedBlocks.contains(location);
    }

    public void removePlacedBlock(Location location) {
        placedBlocks.remove(location);
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public BedwarsPlugin getPlugin() { return plugin; }
    public String getArenaName() { return arenaName; }
    public World getWorld() { return world; }
    public GameState getGameState() { return state; }
    public List<BedwarsTeam> getTeams() { return teams; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCountdown() { return countdown; }
    public int getElapsedSeconds() { return elapsedSeconds; }
    public BedwarsTeam getWinner() { return winner; }
    public ShopManager getShopManager() { return shopManager; }
    public UpgradeShopManager getUpgradeShopManager() { return upgradeShopManager; }
    public List<ResourceGenerator> getGenerators() { return generators; }
    public GameScoreboard getScoreboard() { return scoreboard; }

    public Location getLobbyLocation() { return lobbyLocation; }
    public void setLobbyLocation(Location location) { this.lobbyLocation = location; }
    public Location getSpectatorLocation() { return spectatorLocation; }
    public void setSpectatorLocation(Location location) { this.spectatorLocation = location; }

    public Set<UUID> getAllPlayers() {
        Set<UUID> all = new HashSet<>();
        for (BedwarsTeam team : teams) {
            all.addAll(team.getPlayers());
        }
        return all;
    }

    /** Returns all UUIDs who participated in the current/recent game, including eliminated players. */
    public Set<UUID> getAllParticipants() {
        return Collections.unmodifiableSet(playerTeamMap.keySet());
    }

    public int getPlayerCount() {
        return getAllPlayers().size();
    }

    public int getPlayerKills(UUID uuid) {
        return playerKills.getOrDefault(uuid, 0);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.getOrDefault(uuid, false);
    }

    public boolean isInGame(UUID uuid) {
        return playerTeamMap.containsKey(uuid) || spectators.containsKey(uuid);
    }

    // ============================================================
    // DREAM DEFENDER
    // ============================================================

    public void addDreamDefender(IronGolem golem, BedwarsTeam team) {
        golem.setCustomName(team.getColor().getDisplayName() + " &rDream Defender");
        golem.setCustomNameVisible(true);
        golem.setMaxHealth(100.0);
        golem.setHealth(100.0);
        dreamDefenders.put(golem.getUniqueId(), team);
    }

    public BedwarsTeam getDreamDefenderTeam(UUID uuid) {
        return dreamDefenders.get(uuid);
    }

    public boolean isDreamDefender(UUID uuid) {
        return dreamDefenders.containsKey(uuid);
    }

    // ============================================================
    // FEATURE HELPER METHODS
    // ============================================================

    /** Feature 1: Give kill reward items to the killer. */
    private void giveKillReward(Player killer, boolean finalKill) {
        if (finalKill) {
            killer.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 1));
            killer.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 4));
            MessageUtils.sendActionBar(killer, MessageUtils.color("&6+1 Gold &7+ &f+4 Iron &e(Final Kill Reward)"));
        } else {
            killer.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 2));
            MessageUtils.sendActionBar(killer, MessageUtils.color("&f+2 Iron &e(Kill Reward)"));
        }
    }

    /** Feature 2: Grant 3-second spawn shield to a player. */
    public void activateSpawnShield(Player player) {
        spawnShieldPlayers.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60, 255, false, false));
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnShieldPlayers.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 60L);
    }

    public boolean hasSpawnShield(UUID uuid) {
        return spawnShieldPlayers.contains(uuid);
    }

    /** Feature 7: Auto Team Balance — distribute players evenly across teams. */
    private void autoBalanceTeams() {
        if (teams.size() < 2) return;
        int totalPlayers = getAllPlayers().size();
        int maxPerTeam = (int) Math.ceil((double) totalPlayers / teams.size());
        List<UUID> overflow = new ArrayList<>();
        for (BedwarsTeam team : teams) {
            while (team.getSize() > maxPerTeam) {
                List<UUID> players = new ArrayList<>(team.getPlayers());
                if (players.isEmpty()) break;
                UUID moved = players.get(players.size() - 1);
                team.removePlayer(moved);
                playerTeamMap.remove(moved);
                overflow.add(moved);
            }
        }
        for (UUID uuid : overflow) {
            BedwarsTeam smallest = getSmallestTeam();
            if (smallest != null) {
                smallest.addPlayer(uuid);
                playerTeamMap.put(uuid, smallest);
            }
        }
    }

    /** Feature 9: Place 1-block-thick wool guard around each team's bed. */
    private void placeBedGuardBlocks() {
        int radius = plugin.getConfig().getInt("game.bed-guard-radius", 1);
        for (BedwarsTeam team : teams) {
            Location bedLoc = team.getBedLocation();
            if (bedLoc == null) continue;
            Material guardMat = team.getColor().getWoolMaterial();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location guardLoc = bedLoc.clone().add(dx, 0, dz);
                    Block block = guardLoc.getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(guardMat);
                        bedGuardBlocks.add(guardLoc.clone());
                    }
                }
            }
        }
    }

    // Feature 6: Rush Mode getters/setters
    public boolean isRushMode() { return rushMode; }
    public void setRushMode(boolean rushMode) { this.rushMode = rushMode; }

    // Feature 10: Private Game Password
    public boolean hasPassword() { return password != null && !password.isEmpty(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = (password == null || password.isEmpty()) ? null : password; }
    public boolean checkPassword(String attempt) { return !hasPassword() || password.equals(attempt); }

    private record GeneratorLocation(GeneratorType type, Location location, BedwarsTeam team) {}
}
