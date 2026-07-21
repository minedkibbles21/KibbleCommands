package com.minedkibbles21.kibblecommands.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<String, Map<UUID, Long>>();

    public long getRemainingCooldown(String alias, UUID uuid, int cooldownSec) {
        Map<UUID, Long> map = this.cooldowns.get(alias);
        if (map == null) {
            return 0L;
        }
        Long last = map.get(uuid);
        if (last == null) {
            return 0L;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        long remaining = (long)cooldownSec - elapsed;
        return remaining > 0L ? remaining : 0L;
    }

    public void recordUse(String alias, UUID uuid) {
        this.cooldowns.computeIfAbsent(alias, k -> new HashMap()).put(uuid, System.currentTimeMillis());
    }

    public void clearAll() {
        this.cooldowns.clear();
    }
}

