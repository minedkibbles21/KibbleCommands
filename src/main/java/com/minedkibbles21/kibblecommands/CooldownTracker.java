package com.minedkibbles21.kibblecommands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownTracker {
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    public long getRemainingCooldown(String alias, UUID uuid, int cooldownSec) {
        Map<UUID, Long> map = cooldowns.get(alias);
        if (map == null) return 0;
        
        Long last = map.get(uuid);
        if (last == null) return 0;
        
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        long remaining = cooldownSec - elapsed;
        return Math.max(0, remaining);
    }

    public void recordUse(String alias, UUID uuid) {
        cooldowns.computeIfAbsent(alias, k -> new HashMap<>()).put(uuid, System.currentTimeMillis());
    }

    public void clearAll() {
        cooldowns.clear();
    }
}
