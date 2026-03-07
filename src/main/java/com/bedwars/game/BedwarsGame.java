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
import org.bukkit.entity.Player;
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

    // Track respawning players
    private final Map<UUID, Integer> respawnCountdowns = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

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
            broadcast(MessageUtils.color("&c&l" + killer.getName() + " &r&eFinally killed &c&l" + player.getName() + "&r&e!"));

            MessageUtils.sendTitle(killer, "&6&lFINAL KILL", "&e+" + 1 + " Kill", 10, 40, 10);
            MessageUtils.playSound(killer, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        } else {
            broadcast(MessageUtils.color("&7&l" + player.getName() + " &r&7was eliminated!"));
        }

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
        }

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

        MessageUtils.sendTitle(player, "&a&lRESPAWNED!", "", 10, 20, 10);
        MessageUtils.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT);
        scoreboard.update(player);
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

        // Cancel countdown task
        if (countdownTask != null) countdownTask.cancel();

        // Start resource generators
        for (ResourceGenerator gen : generators) {
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

        // Start game timer task
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                elapsedSeconds++;
                applyOngoingEffects();

                // Check max time
                int maxTime = plugin.getConfig().getInt("game.max-time", 40);
                if (maxTime > 0 && elapsedSeconds >= maxTime * 60) {
                    // End game by most kills
                    endGameByKills();
                    cancel();
                    return;
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

            // Void damage - if player falls below a certain Y level
            if (player.getLocation().getY() < -64) {
                player.setHealth(0);
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

        // Schedule restart
        new BukkitRunnable() {
            @Override
            public void run() {
                resetGame();
            }
        }.runTaskLater(plugin, 200L); // 10 seconds
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

        // Clean up placed blocks
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            block.setType(Material.AIR);
        }
        placedBlocks.clear();

        // Reset team data
        for (BedwarsTeam team : teams) {
            team.getPlayers().clear();
            team.getEliminatedPlayers().clear();
            if (!team.isBedAlive()) {
                // Re-place bed
                restoreBed(team);
            }
        }
        // Recreate teams fresh
        teams.clear();

        playerTeamMap.clear();
        playerKills.clear();
        spectators.clear();
        respawnCountdowns.clear();
        respawnTasks.values().forEach(BukkitTask::cancel);
        respawnTasks.clear();
        winner = null;
        elapsedSeconds = 0;

        scoreboard.cleanup();
        scoreboard = new GameScoreboard(this);
        shopManager = new ShopManager(this);
        upgradeShopManager = new UpgradeShopManager(this);

        // Re-register generators from pending locations
        for (GeneratorLocation gl : pendingGeneratorLocations) {
            ResourceGenerator gen = new ResourceGenerator(this, gl.location(), gl.type(), null);
            generators.add(gen);
        }

        state = GameState.WAITING;
        plugin.getConfigManager().loadArenas(); // Reload teams
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
        if (state == GameState.PLAYING) {
            ResourceGenerator gen = new ResourceGenerator(this, location, type, team);
            gen.start();
            generators.add(gen);
        } else {
            ResourceGenerator gen = new ResourceGenerator(this, location, type, team);
            generators.add(gen);
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

    private record GeneratorLocation(GeneratorType type, Location location, BedwarsTeam team) {}
}
