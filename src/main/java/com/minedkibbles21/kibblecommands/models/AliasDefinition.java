package com.minedkibbles21.kibblecommands.models;

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

    public String getAlias() {
        return this.alias;
    }

    public String getCommand() {
        return this.command;
    }

    public String getDescription() {
        return this.description;
    }

    public String getPermission() {
        return this.permission;
    }

    public String getPermissionMessage() {
        return this.permissionMessage;
    }

    public boolean isConsoleOnly() {
        return this.consoleOnly;
    }

    public boolean isPlayerOnly() {
        return this.playerOnly;
    }

    public int getCooldown() {
        return this.cooldown;
    }

    public boolean isPassArgs() {
        return this.passArgs;
    }

    public String getExecuteAs() {
        return this.executeAs;
    }

    public boolean isExecuteAsConsole() {
        return EXECUTOR_CONSOLE.equals(this.executeAs);
    }

    public boolean hasPermission() {
        return this.permission != null && !this.permission.isBlank();
    }

    public String toString() {
        return "AliasDefinition{alias='" + this.alias + "', command='" + this.command + "'}";
    }
}

