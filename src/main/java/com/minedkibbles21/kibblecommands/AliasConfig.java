package com.minedkibbles21.kibblecommands;

import java.util.Collections;
import java.util.List;

// Config model for a registered command alias.
// Simple POJO to keep alias attributes in one place.
public class AliasConfig {
    private final String name;
    private final List<String> targets;
    private final String desc;
    private final String permission;
    private final String permMessage;
    private final boolean consoleOnly;
    private final boolean playerOnly;
    private final int cooldown;
    private final boolean passArgs;
    private final String executeAs;
    private final List<String> tabSuggestions;
    private final double cost;
    private final int warmup;
    private final String actionMessage;

    public AliasConfig(String name, List<String> targets, String desc, String permission, String permMessage, boolean consoleOnly, boolean playerOnly, int cooldown, boolean passArgs, String executeAs, List<String> tabSuggestions, double cost, int warmup, String actionMessage) {
        this.name = name;
        this.targets = targets != null ? targets : Collections.emptyList();
        this.desc = desc != null ? desc : "";
        this.permission = permission != null ? permission : "";
        this.permMessage = permMessage != null ? permMessage : "&cYou do not have permission to use this command.";
        this.consoleOnly = consoleOnly;
        this.playerOnly = playerOnly;
        this.cooldown = Math.max(0, cooldown);
        this.passArgs = passArgs;
        this.executeAs = "console".equalsIgnoreCase(executeAs) ? "console" : "sender";
        this.tabSuggestions = tabSuggestions != null ? tabSuggestions : Collections.emptyList();
        this.cost = Math.max(0.0, cost);
        this.warmup = Math.max(0, warmup);
        this.actionMessage = actionMessage != null ? actionMessage : "";
    }

    public String getName() { return name; }
    public List<String> getTargets() { return targets; }
    
    // Helper to get first/primary target command string
    public String getTarget() {
        return targets.isEmpty() ? "" : targets.get(0);
    }
    
    public String getDesc() { return desc; }
    public String getPermission() { return permission; }
    public String getPermMessage() { return permMessage; }
    public boolean isConsoleOnly() { return consoleOnly; }
    public boolean isPlayerOnly() { return playerOnly; }
    public int getCooldown() { return cooldown; }
    public boolean isPassArgs() { return passArgs; }
    public String getExecuteAs() { return executeAs; }
    public List<String> getTabSuggestions() { return tabSuggestions; }
    public double getCost() { return cost; }
    public int getWarmup() { return warmup; }
    public String getActionMessage() { return actionMessage; }
    
    public boolean isConsoleExec() {
        return "console".equals(executeAs);
    }

    public boolean hasPerm() {
        return permission != null && !permission.isBlank();
    }

    @Override
    public String toString() {
        return "AliasConfig{name='" + name + "', targets=" + targets + "}";
    }
}
