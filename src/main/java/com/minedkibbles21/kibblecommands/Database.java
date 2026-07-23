package com.minedkibbles21.kibblecommands;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.scheduler.BukkitRunnable;

// Handles SQLite / MySQL database persistence.
// Runs query commands asynchronously to prevent blocking the main server thread.
public class Database {
    private final KibbleCommands plugin;
    private final String type;
    private final String host;
    private final int port;
    private final String name;
    private final String user;
    private final String pass;

    private Connection conn = null;

    public Database(KibbleCommands plugin) {
        this.plugin = plugin;
        
        // Load database settings from config.yml
        this.type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        this.host = plugin.getConfig().getString("database.host", "localhost");
        this.port = plugin.getConfig().getInt("database.port", 3306);
        this.name = plugin.getConfig().getString("database.name", "minecraft");
        this.user = plugin.getConfig().getString("database.username", "root");
        this.pass = plugin.getConfig().getString("database.password", "");
    }

    public synchronized void connect() {
        try {
            if (conn != null && !conn.isClosed()) return;

            if ("mysql".equals(type)) {
                // Ensure MySQL Driver is loaded
                Class.forName("com.mysql.cj.jdbc.Driver");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&allowPublicKeyRetrieval=true";
                conn = DriverManager.getConnection(url, user, pass);
                plugin.getLogger().info("Connected to MySQL database: " + name);
            } else {
                // Default to SQLite local file database
                Class.forName("org.sqlite.JDBC");
                File folder = plugin.getDataFolder();
                if (!folder.exists()) folder.mkdirs();
                
                File file = new File(folder, "cooldowns.db");
                String url = "jdbc:sqlite:" + file.getAbsolutePath();
                conn = DriverManager.getConnection(url);
                plugin.getLogger().info("Connected to SQLite database: " + file.getName());
            }

            createTables();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database!", ex);
        }
    }

    public synchronized void disconnect() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", ex);
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS kibble_cooldowns (" +
                     "player_uuid VARCHAR(36) NOT NULL," +
                     "alias_name VARCHAR(64) NOT NULL," +
                     "last_used BIGINT NOT NULL," +
                     "PRIMARY KEY (player_uuid, alias_name)" +
                     ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables!", ex);
        }
    }

    // Loads a player's cooldown records from the database.
    // Runs synchronously to fetch results before player is fully loaded on the server.
    public synchronized Map<String, Long> loadPlayerCooldowns(UUID uuid) {
        Map<String, Long> map = new HashMap<>();
        if (conn == null) return map;

        String sql = "SELECT alias_name, last_used FROM kibble_cooldowns WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("alias_name"), rs.getLong("last_used"));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error loading player cooldowns for " + uuid, ex);
        }
        return map;
    }

    // Save cooldown asynchronously to DB.
    public void saveCooldownAsync(UUID uuid, String alias, long timestamp) {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveCooldown(uuid, alias, timestamp);
            }
        }.runTaskAsynchronously(plugin);
    }

    private synchronized void saveCooldown(UUID uuid, String alias, long timestamp) {
        if (conn == null) return;

        // Upsert record using standard SQL syntax or query patterns
        String sql = "mysql".equals(type)
            ? "INSERT INTO kibble_cooldowns (player_uuid, alias_name, last_used) VALUES (?, ?, ?) " +
              "ON DUPLICATE KEY UPDATE last_used = VALUES(last_used)"
            : "INSERT OR REPLACE INTO kibble_cooldowns (player_uuid, alias_name, last_used) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, alias);
            ps.setLong(3, timestamp);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error saving player cooldown record to DB", ex);
        }
    }
}
