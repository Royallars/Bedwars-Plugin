package com.bedwars;

import com.bedwars.commands.BedwarsCommand;
import com.bedwars.commands.JoinCommand;
import com.bedwars.commands.LeaderboardCommand;
import com.bedwars.commands.ReplayCommand;
import com.bedwars.commands.StatsCommand;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.GameManager;
import com.bedwars.gui.GUIManager;
import com.bedwars.listeners.BedListener;
import com.bedwars.listeners.GameListener;
import com.bedwars.listeners.PlayerListener;
import com.bedwars.listeners.ShopListener;
import com.bedwars.replay.ReplayManager;
import com.bedwars.stats.StatsManager;
import com.bedwars.utils.ConfigManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BedwarsPlugin extends JavaPlugin {

    private static BedwarsPlugin instance;
    private GameManager gameManager;
    private ConfigManager configManager;
    private GUIManager guiManager;
    private StatsManager statsManager;
    private ReplayManager replayManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configs
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        gameManager = new GameManager(this);
        guiManager = new GUIManager(this);
        statsManager = new StatsManager(this);
        statsManager.initialize();

        replayManager = new ReplayManager(this);
        replayManager.start();

        // Load saved arenas
        loadArenas();

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        getLogger().info("BedwarsPlugin has been enabled! Ready to play Bed Wars!");
    }

    @Override
    public void onDisable() {
        // Cleanly end all games
        if (gameManager != null) {
            gameManager.shutdown();
        }

        // Save all arenas
        if (configManager != null) {
            for (BedwarsGame game : gameManager.getGames()) {
                configManager.saveArena(game);
            }
        }

        // Close database connection
        if (statsManager != null) {
            statsManager.shutdown();
        }

        if (replayManager != null) {
            replayManager.cleanup();
        }

        getLogger().info("BedwarsPlugin has been disabled. Goodbye!");
    }

    private void loadArenas() {
        List<BedwarsGame> arenas = configManager.loadArenas();
        for (BedwarsGame arena : arenas) {
            gameManager.registerGame(arena);
            getLogger().info("Loaded arena: " + arena.getArenaName());
        }
        getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    private void registerCommands() {
        BedwarsCommand bwCommand = new BedwarsCommand(this);
        getCommand("bedwars").setExecutor(bwCommand);
        getCommand("bedwars").setTabCompleter(bwCommand);

        JoinCommand joinCommand = new JoinCommand(this, false);
        getCommand("bwjoin").setExecutor(joinCommand);
        getCommand("bwjoin").setTabCompleter(joinCommand);

        JoinCommand leaveCommand = new JoinCommand(this, true);
        getCommand("bwleave").setExecutor(leaveCommand);

        StatsCommand statsCommand = new StatsCommand(this);
        getCommand("bwstats").setExecutor(statsCommand);

        LeaderboardCommand topCommand = new LeaderboardCommand(this);
        getCommand("bwtop").setExecutor(topCommand);
        getCommand("bwtop").setTabCompleter(topCommand);

        ReplayCommand replayCommand = new ReplayCommand(this);
        getCommand("bwreplay").setExecutor(replayCommand);
        getCommand("bwreplay").setTabCompleter(replayCommand);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new BedListener(this), this);
        pm.registerEvents(new ShopListener(this), this);
        pm.registerEvents(new GameListener(this), this);
        pm.registerEvents(guiManager, this); // Handles all GUI inventory + hotbar clicks
    }

    public static BedwarsPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public ReplayManager getReplayManager() {
        return replayManager;
    }
}
