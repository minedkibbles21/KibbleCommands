package com.minedkibbles21.kibblecommands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Keeps track of when players use cooldown-restricted commands.
// Uses system time milliseconds for comparisons.
public class Cooldowns {
    // Map: Alias Name -> (Player UUID -> Last execution timestamp)
    private final Map<String, Map<UUID, Long>> activeCooldowns = new HashMap<>();

    public long getRemaining(String alias, UUID uuid, int seconds) {
        Map<UUID, Long> playerMap = activeCooldowns.get(alias);
        if (playerMap == null) return 0;
        
        Long lastUse = playerMap.get(uuid);
        if (lastUse == null) return 0;
        
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        long left = seconds - elapsed;
        return Math.max(0, left);
    }

    public void update(String alias, UUID uuid) {
        activeCooldowns.computeIfAbsent(alias, k -> new HashMap<>()).put(uuid, System.currentTimeMillis());
    }

    public void reset() {
        activeCooldowns.clear();
    }
}
