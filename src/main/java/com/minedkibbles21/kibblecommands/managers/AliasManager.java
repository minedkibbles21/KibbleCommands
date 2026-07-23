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
        unregisterAll();
        definitions.clear();
        ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("aliases");
        if (section == null) {
            plugin.getLogger().info("No aliases defined in config.yml.");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection aliasSection = section.getConfigurationSection(key);
            if (aliasSection == null) continue;
            String alias = key.toLowerCase(Locale.ROOT);
            String problem = getAliasBlockReason(alias);
            if (problem != null) {
                plugin.getLogger().warning("Alias '" + key + "' skipped: " + problem);
                continue;
            }
            String command = AliasManager.normalizeTargetCommand(aliasSection.getString("command", ""));
            if (command.isBlank()) {
                plugin.getLogger().warning("Alias '" + key + "' skipped: command is empty.");
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
                    readExecutor(aliasSection)
            );
            if (!registerDynamic(definition)) continue;
            definitions.put(definition.getAlias(), definition);
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " alias(es).");
    }

    public boolean addAlias(String alias, String command, String description, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs) {
        return addAlias(alias, command, description, permission, playerOnly, consoleOnly, cooldown, passArgs, "sender");
    }

    public boolean addAlias(String alias, String command, String description, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs, String executeAs) {
        String key = alias.toLowerCase(Locale.ROOT);
        if (getAliasBlockReason(key) != null) {
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
        if (!registerDynamic(definition)) {
            return false;
        }
        String path = "aliases." + key;
        plugin.getConfig().set(path + ".command", definition.getCommand());
        if (!definition.getDescription().isBlank()) {
            plugin.getConfig().set(path + ".description", definition.getDescription());
        }
        if (definition.hasPermission()) {
            plugin.getConfig().set(path + ".permission", definition.getPermission());
            plugin.getConfig().set(path + ".permission-message", definition.getPermissionMessage());
        }
        plugin.getConfig().set(path + ".player-only", definition.isPlayerOnly());
        plugin.getConfig().set(path + ".console-only", definition.isConsoleOnly());
        plugin.getConfig().set(path + ".cooldown", definition.getCooldown());
        plugin.getConfig().set(path + ".pass-args", definition.isPassArgs());
        plugin.getConfig().set(path + ".execute-as", definition.getExecuteAs());
        plugin.saveConfig();
        definitions.put(key, definition);
        return true;
    }

    public boolean removeAlias(String alias) {
        String key = alias.toLowerCase(Locale.ROOT);
        if (!definitions.containsKey(key)) {
            return false;
        }
        definitions.remove(key);
        unregisterDynamic(key);
        plugin.getConfig().set("aliases." + key, null);
        plugin.saveConfig();
        return true;
    }

    public void unregisterAll() {
        CommandMap commandMap = this.getCommandMap();
        for (DynamicAliasCommand command : registeredCommands.values()) {
            unregisterCommand(commandMap, command);
        }
        registeredCommands.clear();
    }

    public Map<String, AliasDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public AliasDefinition getDefinition(String alias) {
        return definitions.get(alias.toLowerCase(Locale.ROOT));
    }

    public boolean hasAlias(String alias) {
        return definitions.containsKey(alias.toLowerCase(Locale.ROOT));
    }

    public boolean isValidAliasName(String alias) {
        return alias != null && alias.toLowerCase(Locale.ROOT).matches(ALIAS_PATTERN);
    }

    public String getAliasBlockReason(String alias) {
        if (!isValidAliasName(alias)) {
            return "names may only contain lowercase letters, numbers, hyphens, and underscores";
        }
        String key = alias.toLowerCase(Locale.ROOT);
        if (definitions.containsKey(key)) {
            return "that alias already exists";
        }
        if (isReservedAlias(key)) {
            return "that name is reserved by KibbleCommands";
        }
        CommandMap commandMap = getCommandMap();
        Command existing = commandMap != null ? commandMap.getCommand(key) : null;
        if (existing != null) {
            return "that command is already registered by " + describeCommandOwner(existing);
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
            CommandMap commandMap = getCommandMap();
            if (commandMap == null) {
                plugin.getLogger().severe("Could not access CommandMap; alias '" + definition.getAlias() + "' was not registered.");
                return false;
            }
            DynamicAliasCommand command = new DynamicAliasCommand(plugin, definition);
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
            registeredCommands.put(definition.getAlias(), command);
            plugin.getLogger().info("Registered alias: /" + definition.getAlias() + " -> " + definition.getCommand());
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register alias '" + definition.getAlias() + "'", exception);
            return false;
        }
    }

    private void unregisterDynamic(String alias) {
        DynamicAliasCommand command = registeredCommands.remove(alias);
        if (command != null) {
            unregisterCommand(getCommandMap(), command);
        }
    }

    private void unregisterCommand(CommandMap commandMap, DynamicAliasCommand command) {
        if (commandMap == null) {
            return;
        }
        command.unregister(commandMap);
        removeKnownCommandEntries(commandMap, command);
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
            plugin.getLogger().log(Level.WARNING, "Could not fully clean command map entry for /" + command.getName(), exception);
        }
    }

    private boolean isReservedAlias(String alias) {
        if (alias.equals("kibblecommands") || alias.equals("kc") || alias.equals("kibblecmd")) {
            return true;
        }
        List<String> blockedAliases = plugin.getConfig().getStringList("blocked-aliases");
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
            plugin.getLogger().log(Level.SEVERE, "Failed to get CommandMap", exception);
            return null;
        }
    }
}
