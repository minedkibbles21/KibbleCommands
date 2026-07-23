package com.minedkibbles21.kibblecommands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Keeps track of player command cooldowns.
// Uses an in-memory cache synchronized with the SQL database.
public class Cooldowns {
    private final KibbleCommands plugin;

    // Outer Map: Player UUID -> Inner Map: Alias Name -> Last execution timestamp
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();

    public Cooldowns(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    public long getRemaining(String alias, UUID uuid, int seconds) {
        Map<String, Long> playerMap = cache.get(uuid);
        if (playerMap == null) return 0;
        
        Long lastUse = playerMap.get(alias);
        if (lastUse == null) return 0;
        
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        long left = seconds - elapsed;
        return Math.max(0, left);
    }

    public void update(String alias, UUID uuid) {
        long now = System.currentTimeMillis();
        // Update local memory cache
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(alias, now);
        
        // Sync to SQLite / MySQL database asynchronously
        plugin.getDatabase().saveCooldownAsync(uuid, alias, now);
    }

    // Loads a player's cooldown records from the database into the memory cache.
    // This is run when the player joins the server.
    public void loadPlayer(UUID uuid) {
        Map<String, Long> databaseData = plugin.getDatabase().loadPlayerCooldowns(uuid);
        if (!databaseData.isEmpty()) {
            cache.put(uuid, new ConcurrentHashMap<>(databaseData));
        }
    }

    // Unloads a player's cache from memory when they quit to prevent memory leaks.
    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public void reset() {
        cache.clear();
    }
}
