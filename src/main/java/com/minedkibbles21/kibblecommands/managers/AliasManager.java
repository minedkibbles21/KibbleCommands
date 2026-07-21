package com.minedkibbles21.kibblecommands.managers;

import com.minedkibbles21.kibblecommands.KibbleCommands;
import com.minedkibbles21.kibblecommands.commands.DynamicAliasCommand;
import com.minedkibbles21.kibblecommands.models.AliasDefinition;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;

public class AliasManager {
    private static final String ALIAS_PATTERN = "[a-z0-9_-]+";
    private final KibbleCommands plugin;
    private final Map<String, DynamicAliasCommand> registeredCommands = new LinkedHashMap<>();
    private final Map<String, AliasDefinition> definitions = new LinkedHashMap<>();

    public AliasManager(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.unregisterAll();
        this.definitions.clear();
        ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("aliases");
        if (section == null) {
            this.plugin.getLogger().info("No aliases defined in config.yml.");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection aliasSection = section.getConfigurationSection(key);
            if (aliasSection == null) continue;
            String alias = key.toLowerCase(Locale.ROOT);
            String problem = this.getAliasBlockReason(alias);
            if (problem != null) {
                this.plugin.getLogger().warning("Alias '" + key + "' skipped: " + problem);
                continue;
            }
            String command = AliasManager.normalizeTargetCommand(aliasSection.getString("command", ""));
            if (command.isBlank()) {
                this.plugin.getLogger().warning("Alias '" + key + "' skipped: command is empty.");
                continue;
            }
            AliasDefinition definition = new AliasDefinition(
                    alias,
                    command,
                    aliasSection.getString("description", ""),
                    aliasSection.getString("permission", ""),
                    aliasSection.getString("permission-message", "&cYou do not have permission to use this command."),
                    aliasSection.getBoolean("console-only", false),
                    aliasSection.getBoolean("player-only", false),
                    aliasSection.getInt("cooldown", 0),
                    aliasSection.getBoolean("pass-args", true),
                    this.readExecutor(aliasSection)
            );
            if (!this.registerDynamic(definition)) continue;
            this.definitions.put(definition.getAlias(), definition);
        }
        this.plugin.getLogger().info("Loaded " + this.definitions.size() + " alias(es).");
    }

    public boolean addAlias(String alias, String command, String description, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs) {
        return this.addAlias(alias, command, description, permission, playerOnly, consoleOnly, cooldown, passArgs, "sender");
    }

    public boolean addAlias(String alias, String command, String description, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs, String executeAs) {
        String key = alias.toLowerCase(Locale.ROOT);
        if (this.getAliasBlockReason(key) != null) {
            return false;
        }
        AliasDefinition definition = new AliasDefinition(
                key,
                AliasManager.normalizeTargetCommand(command),
                description,
                permission,
                "&cYou do not have permission to use this command.",
                consoleOnly,
                playerOnly,
                cooldown,
                passArgs,
                executeAs
        );
        if (definition.getCommand().isBlank()) {
            return false;
        }
        if (!this.registerDynamic(definition)) {
            return false;
        }
        String path = "aliases." + key;
        this.plugin.getConfig().set(path + ".command", definition.getCommand());
        if (!definition.getDescription().isBlank()) {
            this.plugin.getConfig().set(path + ".description", definition.getDescription());
        }
        if (definition.hasPermission()) {
            this.plugin.getConfig().set(path + ".permission", definition.getPermission());
            this.plugin.getConfig().set(path + ".permission-message", definition.getPermissionMessage());
        }
        this.plugin.getConfig().set(path + ".player-only", definition.isPlayerOnly());
        this.plugin.getConfig().set(path + ".console-only", definition.isConsoleOnly());
        this.plugin.getConfig().set(path + ".cooldown", definition.getCooldown());
        this.plugin.getConfig().set(path + ".pass-args", definition.isPassArgs());
        this.plugin.getConfig().set(path + ".execute-as", definition.getExecuteAs());
        this.plugin.saveConfig();
        this.definitions.put(key, definition);
        return true;
    }

    public boolean removeAlias(String alias) {
        String key = alias.toLowerCase(Locale.ROOT);
        if (!this.definitions.containsKey(key)) {
            return false;
        }
        this.definitions.remove(key);
        this.unregisterDynamic(key);
        this.plugin.getConfig().set("aliases." + key, null);
        this.plugin.saveConfig();
        return true;
    }

    public void unregisterAll() {
        CommandMap commandMap = this.getCommandMap();
        for (DynamicAliasCommand command : this.registeredCommands.values()) {
            this.unregisterCommand(commandMap, command);
        }
        this.registeredCommands.clear();
    }

    public Map<String, AliasDefinition> getDefinitions() {
        return Collections.unmodifiableMap(this.definitions);
    }

    public AliasDefinition getDefinition(String alias) {
        return this.definitions.get(alias.toLowerCase(Locale.ROOT));
    }

    public boolean hasAlias(String alias) {
        return this.definitions.containsKey(alias.toLowerCase(Locale.ROOT));
    }

    public boolean isValidAliasName(String alias) {
        return alias != null && alias.toLowerCase(Locale.ROOT).matches(ALIAS_PATTERN);
    }

    public String getAliasBlockReason(String alias) {
        if (!this.isValidAliasName(alias)) {
            return "names may only contain lowercase letters, numbers, hyphens, and underscores";
        }
        String key = alias.toLowerCase(Locale.ROOT);
        if (this.definitions.containsKey(key)) {
            return "that alias already exists";
        }
        if (this.isReservedAlias(key)) {
            return "that name is reserved by KibbleCommands";
        }
        CommandMap commandMap = this.getCommandMap();
        Command existing = commandMap != null ? commandMap.getCommand(key) : null;
        if (existing != null) {
            return "that command is already registered by " + this.describeCommandOwner(existing);
        }
        return null;
    }

    public static String normalizeTargetCommand(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private String readExecutor(ConfigurationSection aliasSection) {
        String executeAs = aliasSection.getString("execute-as", "");
        if (executeAs.isBlank() && aliasSection.getBoolean("run-as-console", false)) {
            return "console";
        }
        return "console".equalsIgnoreCase(executeAs) ? "console" : "sender";
    }

    private boolean registerDynamic(AliasDefinition definition) {
        try {
            CommandMap commandMap = this.getCommandMap();
            if (commandMap == null) {
                this.plugin.getLogger().severe("Could not access CommandMap; alias '" + definition.getAlias() + "' was not registered.");
                return false;
            }
            DynamicAliasCommand command = new DynamicAliasCommand(this.plugin, definition);
            commandMap.register(this.plugin.getName().toLowerCase(Locale.ROOT), command);
            this.registeredCommands.put(definition.getAlias(), command);
            this.plugin.getLogger().info("Registered alias: /" + definition.getAlias() + " -> " + definition.getCommand());
            return true;
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to register alias '" + definition.getAlias() + "'", exception);
            return false;
        }
    }

    private void unregisterDynamic(String alias) {
        DynamicAliasCommand command = this.registeredCommands.remove(alias);
        if (command != null) {
            this.unregisterCommand(this.getCommandMap(), command);
        }
    }

    private void unregisterCommand(CommandMap commandMap, DynamicAliasCommand command) {
        if (commandMap == null) {
            return;
        }
        command.unregister(commandMap);
        this.removeKnownCommandEntries(commandMap, command);
    }

    @SuppressWarnings("unchecked")
    private void removeKnownCommandEntries(CommandMap commandMap, DynamicAliasCommand command) {
        if (!(commandMap instanceof SimpleCommandMap simpleCommandMap)) {
            return;
        }
        try {
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(simpleCommandMap);
            knownCommands.entrySet().removeIf(entry -> entry.getValue() == command);
        } catch (ReflectiveOperationException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Could not fully clean command map entry for /" + command.getName(), exception);
        }
    }

    private boolean isReservedAlias(String alias) {
        if (alias.equals("kibblecommands") || alias.equals("kc") || alias.equals("kibblecmd")) {
            return true;
        }
        List<String> blockedAliases = this.plugin.getConfig().getStringList("blocked-aliases");
        return blockedAliases.stream().anyMatch(alias::equalsIgnoreCase);
    }

    private String describeCommandOwner(Command command) {
        if (command instanceof PluginCommand pluginCommand) {
            return pluginCommand.getPlugin().getName();
        }
        return command.getClass().getSimpleName();
    }

    private CommandMap getCommandMap() {
        try {
            return Bukkit.getServer().getCommandMap();
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to get CommandMap", exception);
            return null;
        }
    }
}
