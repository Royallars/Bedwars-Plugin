package com.bedwars.stats;

import com.bedwars.BedwarsPlugin;
import com.bedwars.game.BedwarsGame;
import com.bedwars.game.BedwarsTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages persistent player statistics using either MySQL or H2 (embedded).
 *
 * <p>The active backend is chosen from config.yml:
 * <pre>
 * database:
 *   type: h2    # or mysql
 * </pre>
 *
 * <p>Stat writes are always dispatched asynchronously to avoid blocking the main thread.
 */
public class StatsManager {

    // ------------------------------------------------------------------ column names
    private static final String COL_UUID   = "uuid";
    private static final String COL_NAME   = "username";
    private static final String COL_GAMES  = "games_played";
    private static final String COL_WINS   = "wins";
    private static final String COL_KILLS  = "kills";
    private static final String COL_FINALS = "final_kills";
    private static final String COL_BEDS   = "beds_broken";
    private static final String COL_DEATHS = "deaths";

    private static final String TABLE = "bedwars_stats";

    private final BedwarsPlugin plugin;
    private volatile Connection connection;
    private boolean useMysql;

    public StatsManager(BedwarsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    public void initialize() {
        String type = plugin.getConfig().getString("database.type", "h2").toLowerCase();
        useMysql = type.equals("mysql");

        try {
            if (useMysql) {
                initMySQL();
            } else {
                initH2();
            }
            createTable();
            plugin.getLogger().info("[Stats] Database connected (" + (useMysql ? "MySQL" : "H2") + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Stats] Failed to connect to " + (useMysql ? "MySQL" : "H2") + " database! Attempting H2 fallback.", e);
            if (useMysql) {
                useMysql = false;
                try {
                    initH2();
                    createTable();
                    plugin.getLogger().info("[Stats] Fell back to H2 embedded database.");
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "[Stats] H2 fallback also failed. Stats will not be saved.", ex);
                }
            }
        }
    }

    private void initMySQL() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String host     = plugin.getConfig().getString("database.host", "localhost");
        int    port     = plugin.getConfig().getInt("database.port", 3306);
        String name     = plugin.getConfig().getString("database.name", "bedwars");
        String user     = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";
        connection = DriverManager.getConnection(url, user, password);
    }

    private void initH2() throws SQLException, ClassNotFoundException {
        // H2 is shaded into the plugin JAR under com.bedwars.libs.h2
        Class.forName("com.bedwars.libs.h2.Driver");
        File dbFile = new File(plugin.getDataFolder(), "stats");
        String url = "jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE";
        connection = DriverManager.getConnection(url, "sa", "");
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                COL_UUID   + " VARCHAR(36) PRIMARY KEY," +
                COL_NAME   + " VARCHAR(16) NOT NULL," +
                COL_GAMES  + " INT NOT NULL DEFAULT 0," +
                COL_WINS   + " INT NOT NULL DEFAULT 0," +
                COL_KILLS  + " INT NOT NULL DEFAULT 0," +
                COL_FINALS + " INT NOT NULL DEFAULT 0," +
                COL_BEDS   + " INT NOT NULL DEFAULT 0," +
                COL_DEATHS + " INT NOT NULL DEFAULT 0" +
                ")"
            );
        }
    }

    public void shutdown() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Stats] Error closing database connection.", e);
            }
        }
    }

    // ------------------------------------------------------------------ public API

    /**
     * Records statistics for all players at the end of a game.
     * Runs asynchronously.
     */
    public void recordGameEnd(BedwarsGame game, BedwarsTeam winner) {
        if (connection == null) return;

        // Snapshot player data on the main thread before going async
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        java.util.Map<UUID, Boolean> wonMap = new java.util.HashMap<>();
        java.util.Map<UUID, Integer> killsMap = new java.util.HashMap<>();

        for (UUID uuid : game.getAllParticipants()) {
            Player p = Bukkit.getPlayer(uuid);
            names.put(uuid, p != null ? p.getName() : "Unknown");
            wonMap.put(uuid, winner != null && winner.hasPlayer(uuid));
            killsMap.put(uuid, game.getPlayerKills(uuid));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID uuid : names.keySet()) {
                String name = names.get(uuid);
                boolean won  = wonMap.getOrDefault(uuid, false);
                int kills    = killsMap.getOrDefault(uuid, 0);

                try {
                    upsertStats(uuid, name, won ? 1 : 0, kills, 0, 0, 0, 1);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "[Stats] Failed to save stats for " + name, e);
                }
            }
        });
    }

    /** Returns a {@link PlayerStats} snapshot, or null if the database is unavailable or no record exists. */
    public PlayerStats getStats(UUID uuid) {
        if (connection == null) return null;
        String sql = "SELECT * FROM " + TABLE + " WHERE " + COL_UUID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerStats(
                    rs.getString(COL_NAME),
                    rs.getInt(COL_GAMES),
                    rs.getInt(COL_WINS),
                    rs.getInt(COL_KILLS),
                    rs.getInt(COL_FINALS),
                    rs.getInt(COL_BEDS),
                    rs.getInt(COL_DEATHS)
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Stats] Failed to read stats for " + uuid, e);
        }
        return null;
    }

    /**
     * Supported sort columns for the leaderboard.
     */
    public enum LeaderboardStat {
        WINS(COL_WINS, "Wins"),
        KILLS(COL_KILLS, "Kills"),
        FINALS(COL_FINALS, "Final Kills"),
        BEDS(COL_BEDS, "Beds Broken"),
        GAMES(COL_GAMES, "Games Played");

        public final String column;
        public final String displayName;
        LeaderboardStat(String column, String displayName) {
            this.column = column;
            this.displayName = displayName;
        }
    }

    /**
     * Returns the top {@code limit} players sorted by the given stat.
     * Returns an empty list if the database is unavailable.
     * <b>Must be called from an async thread.</b>
     */
    public java.util.List<PlayerStats> getTopPlayers(LeaderboardStat stat, int limit) {
        java.util.List<PlayerStats> result = new java.util.ArrayList<>();
        if (connection == null) return result;

        String sql = "SELECT * FROM " + TABLE + " ORDER BY " + stat.column + " DESC LIMIT ?";
        try {
            ensureConnected();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(new PlayerStats(
                        rs.getString(COL_NAME),
                        rs.getInt(COL_GAMES),
                        rs.getInt(COL_WINS),
                        rs.getInt(COL_KILLS),
                        rs.getInt(COL_FINALS),
                        rs.getInt(COL_BEDS),
                        rs.getInt(COL_DEATHS)
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Stats] Failed to fetch leaderboard for " + stat.column, e);
        }
        return result;
    }

    // ------------------------------------------------------------------ internals

    private void upsertStats(UUID uuid, String name, int winsAdd, int killsAdd,
                             int finalsAdd, int bedsAdd, int deathsAdd, int gamesAdd) throws SQLException {
        ensureConnected();
        // INSERT ... ON DUPLICATE KEY UPDATE works in both MySQL and H2 (MODE=MySQL)
        String sql =
            "INSERT INTO " + TABLE +
            " (" + COL_UUID + "," + COL_NAME + "," + COL_GAMES + "," + COL_WINS + "," +
             COL_KILLS + "," + COL_FINALS + "," + COL_BEDS + "," + COL_DEATHS + ")" +
            " VALUES (?,?,?,?,?,?,?,?)" +
            " ON DUPLICATE KEY UPDATE" +
            " " + COL_NAME   + " = VALUES(" + COL_NAME   + ")," +
            " " + COL_GAMES  + " = " + COL_GAMES  + " + VALUES(" + COL_GAMES  + ")," +
            " " + COL_WINS   + " = " + COL_WINS   + " + VALUES(" + COL_WINS   + ")," +
            " " + COL_KILLS  + " = " + COL_KILLS  + " + VALUES(" + COL_KILLS  + ")," +
            " " + COL_FINALS + " = " + COL_FINALS + " + VALUES(" + COL_FINALS + ")," +
            " " + COL_BEDS   + " = " + COL_BEDS   + " + VALUES(" + COL_BEDS   + ")," +
            " " + COL_DEATHS + " = " + COL_DEATHS + " + VALUES(" + COL_DEATHS + ")";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, gamesAdd);
            ps.setInt(4, winsAdd);
            ps.setInt(5, killsAdd);
            ps.setInt(6, finalsAdd);
            ps.setInt(7, bedsAdd);
            ps.setInt(8, deathsAdd);
            ps.executeUpdate();
        }
    }

    /** Reconnects if the connection has been lost (e.g. MySQL gone-away). */
    private void ensureConnected() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                plugin.getLogger().warning("[Stats] Database connection lost. Reconnecting...");
                if (useMysql) {
                    initMySQL();
                } else {
                    initH2();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Stats] Reconnection failed.", e);
        }
    }

    // ------------------------------------------------------------------ record

    public record PlayerStats(
        String username,
        int gamesPlayed,
        int wins,
        int kills,
        int finalKills,
        int bedsDestroyed,
        int deaths
    ) {
        public double winRate() {
            return gamesPlayed == 0 ? 0.0 : (double) wins / gamesPlayed * 100.0;
        }
    }
}
