package com.minedkibbles21.kibblecommands;

public class AliasDefinition {
    public static final String EXECUTOR_SENDER = "sender";
    public static final String EXECUTOR_CONSOLE = "console";

    private final String alias;
    private final String command;
    private final String description;
    private final String permission;
    private final String permissionMessage;
    private final boolean consoleOnly;
    private final boolean playerOnly;
    private final int cooldown;
    private final boolean passArgs;
    private final String executeAs;

    public AliasDefinition(String alias, String command, String description, String permission, String permissionMessage, boolean consoleOnly, boolean playerOnly, int cooldown, boolean passArgs, String executeAs) {
        this.alias = alias;
        this.command = command;
        this.description = description != null ? description : "";
        this.permission = permission != null ? permission : "";
        this.permissionMessage = permissionMessage != null ? permissionMessage : "&cYou do not have permission to use this command.";
        this.consoleOnly = consoleOnly;
        this.playerOnly = playerOnly;
        this.cooldown = Math.max(0, cooldown);
        this.passArgs = passArgs;
        this.executeAs = EXECUTOR_CONSOLE.equalsIgnoreCase(executeAs) ? EXECUTOR_CONSOLE : EXECUTOR_SENDER;
    }

    public String getAlias() { return alias; }
    public String getCommand() { return command; }
    public String getDescription() { return description; }
    public String getPermission() { return permission; }
    public String getPermissionMessage() { return permissionMessage; }
    public boolean isConsoleOnly() { return consoleOnly; }
    public boolean isPlayerOnly() { return playerOnly; }
    public int getCooldown() { return cooldown; }
    public boolean isPassArgs() { return passArgs; }
    public String getExecuteAs() { return executeAs; }
    
    public boolean isExecuteAsConsole() {
        return EXECUTOR_CONSOLE.equals(executeAs);
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }

    @Override
    public String toString() {
        return "AliasDefinition{alias='" + alias + "', command='" + command + "'}";
    }
}
