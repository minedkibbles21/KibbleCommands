package com.minedkibbles21.kibblecommands;

// Config model for a registered command alias.
// Simple POJO to keep alias attributes in one place.
public class AliasConfig {
    private final String name;
    private final String target;
    private final String desc;
    private final String permission;
    private final String permMessage;
    private final boolean consoleOnly;
    private final boolean playerOnly;
    private final int cooldown;
    private final boolean passArgs;
    private final String executeAs;

    public AliasConfig(String name, String target, String desc, String permission, String permMessage, boolean consoleOnly, boolean playerOnly, int cooldown, boolean passArgs, String executeAs) {
        this.name = name;
        this.target = target;
        this.desc = desc != null ? desc : "";
        this.permission = permission != null ? permission : "";
        this.permMessage = permMessage != null ? permMessage : "&cYou do not have permission to use this command.";
        this.consoleOnly = consoleOnly;
        this.playerOnly = playerOnly;
        this.cooldown = Math.max(0, cooldown);
        this.passArgs = passArgs;
        
        // Execute-as fallback to 'sender'
        this.executeAs = "console".equalsIgnoreCase(executeAs) ? "console" : "sender";
    }

    public String getName() { return name; }
    public String getTarget() { return target; }
    public String getDesc() { return desc; }
    public String getPermission() { return permission; }
    public String getPermMessage() { return permMessage; }
    public boolean isConsoleOnly() { return consoleOnly; }
    public boolean isPlayerOnly() { return playerOnly; }
    public int getCooldown() { return cooldown; }
    public boolean isPassArgs() { return passArgs; }
    public String getExecuteAs() { return executeAs; }
    
    public boolean isConsoleExec() {
        return "console".equals(executeAs);
    }

    public boolean hasPerm() {
        return permission != null && !permission.isBlank();
    }

    @Override
    public String toString() {
        return "AliasConfig{name='" + name + "', target='" + target + "'}";
    }
}
