package com.bedwars.utils;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import com.bedwars.game.TeamColor;
import com.bedwars.generators.GeneratorType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {

    private final BedwarsPlugin plugin;
    private File arenasFile;
    private FileConfiguration arenasConfig;

    public ConfigManager(BedwarsPlugin plugin) {
        this.plugin = plugin;
        loadArenasFile();
    }

    private void loadArenasFile() {
        arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            plugin.saveResource("arenas.yml", false);
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
    }

    public void saveArenasFile() {
        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml!", e);
        }
    }

    public FileConfiguration getArenasConfig() {
        return arenasConfig;
    }

    public void saveArena(BedwarsGame game) {
        String path = "arenas." + game.getArenaName();
        arenasConfig.set(path + ".name", game.getArenaName());
        arenasConfig.set(path + ".world", game.getWorld().getName());
        arenasConfig.set(path + ".min-players", game.getMinPlayers());
        arenasConfig.set(path + ".max-players", game.getMaxPlayers());

        // Save lobby location
        Location lobby = game.getLobbyLocation();
        if (lobby != null) {
            saveLocation(path + ".lobby", lobby);
        }

        // Save spectator location
        Location spectator = game.getSpectatorLocation();
        if (spectator != null) {
            saveLocation(path + ".spectator", spectator);
        }

        // Save team data
        for (BedwarsTeam team : game.getTeams()) {
            String teamPath = path + ".teams." + team.getColor().getConfigName();
            if (team.getSpawnLocation() != null) {
                saveLocation(teamPath + ".spawn", team.getSpawnLocation());
            }
            if (team.getBedLocation() != null) {
                saveLocation(teamPath + ".bed", team.getBedLocation());
            }
        }

        // Save generator locations
        ConfigurationSection genSection = arenasConfig.createSection(path + ".generators");
        List<String> ironGens = new ArrayList<>();
        List<String> goldGens = new ArrayList<>();
        List<String> diamondGens = new ArrayList<>();
        List<String> emeraldGens = new ArrayList<>();

        for (var gen : game.getGenerators()) {
            String locStr = serializeLocation(gen.getLocation());
            switch (gen.getType()) {
                case IRON -> ironGens.add(locStr);
                case GOLD -> goldGens.add(locStr);
                case DIAMOND -> diamondGens.add(locStr);
                case EMERALD -> emeraldGens.add(locStr);
            }
        }

        genSection.set("iron", ironGens);
        genSection.set("gold", goldGens);
        genSection.set("diamond", diamondGens);
        genSection.set("emerald", emeraldGens);

        saveArenasFile();
    }

    public List<BedwarsGame> loadArenas() {
        List<BedwarsGame> games = new ArrayList<>();
        ConfigurationSection arenas = arenasConfig.getConfigurationSection("arenas");
        if (arenas == null) return games;

        for (String arenaName : arenas.getKeys(false)) {
            try {
                BedwarsGame game = loadArena(arenas.getConfigurationSection(arenaName), arenaName);
                if (game != null) {
                    games.add(game);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load arena: " + arenaName, e);
            }
        }

        return games;
    }

    private BedwarsGame loadArena(ConfigurationSection section, String name) {
        if (section == null) return null;

        String worldName = section.getString("world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' not found for arena: " + name);
            return null;
        }

        int minPlayers = section.getInt("min-players", 2);
        int maxPlayers = section.getInt("max-players", 16);

        BedwarsGame game = new BedwarsGame(plugin, name, world, minPlayers, maxPlayers);

        // Load lobby
        ConfigurationSection lobbySection = section.getConfigurationSection("lobby");
        if (lobbySection != null) {
            game.setLobbyLocation(loadLocation(lobbySection, world));
        }

        // Load spectator
        ConfigurationSection spectatorSection = section.getConfigurationSection("spectator");
        if (spectatorSection != null) {
            game.setSpectatorLocation(loadLocation(spectatorSection, world));
        }

        // Load teams
        ConfigurationSection teamsSection = section.getConfigurationSection("teams");
        if (teamsSection != null) {
            for (String colorName : teamsSection.getKeys(false)) {
                TeamColor color = TeamColor.fromString(colorName);
                if (color == null) continue;

                BedwarsTeam team = game.getOrCreateTeam(color);
                ConfigurationSection teamSection = teamsSection.getConfigurationSection(colorName);
                if (teamSection == null) continue;

                ConfigurationSection spawnSection = teamSection.getConfigurationSection("spawn");
                if (spawnSection != null) {
                    team.setSpawnLocation(loadLocation(spawnSection, world));
                }

                ConfigurationSection bedSection = teamSection.getConfigurationSection("bed");
                if (bedSection != null) {
                    team.setBedLocation(loadLocation(bedSection, world));
                }
            }
        }

        // Load generators
        ConfigurationSection genSection = section.getConfigurationSection("generators");
        if (genSection != null) {
            loadGeneratorLocations(game, genSection, "iron", GeneratorType.IRON, world);
            loadGeneratorLocations(game, genSection, "gold", GeneratorType.GOLD, world);
            loadGeneratorLocations(game, genSection, "diamond", GeneratorType.DIAMOND, world);
            loadGeneratorLocations(game, genSection, "emerald", GeneratorType.EMERALD, world);
        }

        return game;
    }

    private void loadGeneratorLocations(BedwarsGame game, ConfigurationSection section,
                                         String key, GeneratorType type, World world) {
        List<String> locations = section.getStringList(key);
        for (String locStr : locations) {
            Location loc = deserializeLocation(locStr, world);
            if (loc != null) {
                game.addGeneratorLocation(type, loc, null);
            }
        }
    }

    private void saveLocation(String path, Location loc) {
        arenasConfig.set(path + ".x", loc.getX());
        arenasConfig.set(path + ".y", loc.getY());
        arenasConfig.set(path + ".z", loc.getZ());
        arenasConfig.set(path + ".yaw", loc.getYaw());
        arenasConfig.set(path + ".pitch", loc.getPitch());
    }

    private Location loadLocation(ConfigurationSection section, World world) {
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private String serializeLocation(Location loc) {
        return loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    private Location deserializeLocation(String str, World world) {
        String[] parts = str.split(",");
        if (parts.length < 3) return null;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Location getLobbyLocation() {
        ConfigurationSection lobby = plugin.getConfig().getConfigurationSection("lobby");
        if (lobby == null) return null;
        String worldName = lobby.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                lobby.getDouble("x"),
                lobby.getDouble("y"),
                lobby.getDouble("z"),
                (float) lobby.getDouble("yaw"),
                (float) lobby.getDouble("pitch"));
    }

    public void setLobbyLocation(Location location) {
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", location.getYaw());
        plugin.getConfig().set("lobby.pitch", location.getPitch());
        plugin.saveConfig();
    }
}
