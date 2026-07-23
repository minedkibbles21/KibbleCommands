package com.minedkibbles21.kibblecommands;

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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class KibbleCommands extends JavaPlugin {
    private static final String ALIAS_PATTERN = "[a-z0-9_-]+";

    private final Map<String, AliasCommand> registeredCommands = new LinkedHashMap<>();
    private final Map<String, AliasDefinition> definitions = new LinkedHashMap<>();
    
    private CooldownTracker cooldownTracker;
    private AdminGui adminGui;
    private String prefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshPrefix();
        
        cooldownTracker = new CooldownTracker();
        adminGui = new AdminGui(this);
        
        PluginCommand command = getCommand("kibblecommands");
        if (command == null) {
            getLogger().severe("The kibblecommands command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        AdminCommand executor = new AdminCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        
        getServer().getPluginManager().registerEvents(adminGui, this);
        reloadAliases();
        logCompatibilityState();
        
        getLogger().info("KibbleCommands v" + getDescription().getVersion() + " has been enabled.");
    }

    @Override
    public void onDisable() {
        unregisterAll();
        getLogger().info("KibbleCommands has been disabled.");
    }

    public void reloadAliases() {
        unregisterAll();
        definitions.clear();
        
        ConfigurationSection section = getConfig().getConfigurationSection("aliases");
        if (section == null) {
            getLogger().info("No aliases defined in config.yml.");
            return;
        }
        
        for (String key : section.getKeys(false)) {
            ConfigurationSection aliasSection = section.getConfigurationSection(key);
            if (aliasSection == null) continue;
            
            String alias = key.toLowerCase(Locale.ROOT);
            String problem = getAliasBlockReason(alias);
            if (problem != null) {
                getLogger().warning("Alias '" + key + "' skipped: " + problem);
                continue;
            }
            
            String command = normalizeTargetCommand(aliasSection.getString("command", ""));
            if (command.isBlank()) {
                getLogger().warning("Alias '" + key + "' skipped: command is empty.");
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
            
            if (registerDynamic(definition)) {
                definitions.put(definition.getAlias(), definition);
            }
        }
        getLogger().info("Loaded " + definitions.size() + " alias(es).");
    }

    public boolean addAlias(String alias, String command, String description, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs) {
        return addAlias(alias, command, description, permission, playerOnly, consoleOnly, cooldown, passArgs, "sender");
    }

    public boolean addAlias(String alias, String command, String description, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs, String executeAs) {
        String key = alias.toLowerCase(Locale.ROOT);
        if (getAliasBlockReason(key) != null) return false;
        
        AliasDefinition definition = new AliasDefinition(
                key,
                normalizeTargetCommand(command),
                description,
                permission,
                "&cYou do not have permission to use this command.",
                consoleOnly,
                playerOnly,
                cooldown,
                passArgs,
                executeAs
        );
        
        if (definition.getCommand().isBlank() || !registerDynamic(definition)) {
            return false;
        }
        
        String path = "aliases." + key;
        getConfig().set(path + ".command", definition.getCommand());
        if (!definition.getDescription().isBlank()) {
            getConfig().set(path + ".description", definition.getDescription());
        }
        if (definition.hasPermission()) {
            getConfig().set(path + ".permission", definition.getPermission());
            getConfig().set(path + ".permission-message", definition.getPermissionMessage());
        }
        getConfig().set(path + ".player-only", definition.isPlayerOnly());
        getConfig().set(path + ".console-only", definition.isConsoleOnly());
        getConfig().set(path + ".cooldown", definition.getCooldown());
        getConfig().set(path + ".pass-args", definition.isPassArgs());
        getConfig().set(path + ".execute-as", definition.getExecuteAs());
        saveConfig();
        
        definitions.put(key, definition);
        return true;
    }

    public boolean removeAlias(String alias) {
        String key = alias.toLowerCase(Locale.ROOT);
        if (!definitions.containsKey(key)) return false;
        
        definitions.remove(key);
        unregisterDynamic(key);
        getConfig().set("aliases." + key, null);
        saveConfig();
        return true;
    }

    public void unregisterAll() {
        CommandMap commandMap = getCommandMap();
        for (AliasCommand command : registeredCommands.values()) {
            unregisterCommand(commandMap, command);
        }
        registeredCommands.clear();
    }

    public String getAliasBlockReason(String alias) {
        if (!alias.matches(ALIAS_PATTERN)) {
            return "names may only contain lowercase letters, numbers, hyphens, and underscores";
        }
        if (definitions.containsKey(alias)) {
            return "that alias already exists";
        }
        if (alias.equals("kibblecommands") || alias.equals("kc") || alias.equals("kibblecmd")) {
            return "that name is reserved by KibbleCommands";
        }
        List<String> blockedAliases = getConfig().getStringList("blocked-aliases");
        if (blockedAliases.stream().anyMatch(alias::equalsIgnoreCase)) {
            return "that name is reserved by server configuration";
        }
        CommandMap commandMap = getCommandMap();
        Command existing = commandMap != null ? commandMap.getCommand(alias) : null;
        if (existing != null) {
            return "that command is already registered by " + (existing instanceof PluginCommand pc ? pc.getPlugin().getName() : existing.getClass().getSimpleName());
        }
        return null;
    }

    public static String normalizeTargetCommand(String command) {
        if (command == null) return "";
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
                getLogger().severe("Could not access CommandMap; alias '" + definition.getAlias() + "' was not registered.");
                return false;
            }
            AliasCommand command = new AliasCommand(this, definition);
            commandMap.register(getName().toLowerCase(Locale.ROOT), command);
            registeredCommands.put(definition.getAlias(), command);
            getLogger().info("Registered alias: /" + definition.getAlias() + " -> " + definition.getCommand());
            return true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to register alias '" + definition.getAlias() + "'", exception);
            return false;
        }
    }

    private void unregisterDynamic(String alias) {
        AliasCommand command = registeredCommands.remove(alias);
        if (command != null) {
            unregisterCommand(getCommandMap(), command);
        }
    }

    private void unregisterCommand(CommandMap commandMap, AliasCommand command) {
        if (commandMap == null) return;
        command.unregister(commandMap);
        
        if (commandMap instanceof SimpleCommandMap simpleCommandMap) {
            try {
                Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(simpleCommandMap);
                knownCommands.entrySet().removeIf(entry -> entry.getValue() == command);
            } catch (ReflectiveOperationException exception) {
                getLogger().log(Level.WARNING, "Could not fully clean command map entry for /" + command.getName(), exception);
            }
        }
    }

    private CommandMap getCommandMap() {
        try {
            return Bukkit.getServer().getCommandMap();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to get CommandMap", exception);
            return null;
        }
    }

    private void logCompatibilityState() {
        logPluginState("LuckPerms");
        logPluginState("Vault");
        logPluginState("Essentials");
    }

    private void logPluginState(String pluginName) {
        Plugin otherPlugin = getServer().getPluginManager().getPlugin(pluginName);
        if (otherPlugin != null && otherPlugin.isEnabled()) {
            getLogger().info("Detected " + pluginName + "; integration active.");
        }
    }

    public Map<String, AliasDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public CooldownTracker getCooldownTracker() {
        return cooldownTracker;
    }

    public AdminGui getAdminGui() {
        return adminGui;
    }

    public String prefix() {
        return prefix;
    }

    private void refreshPrefix() {
        prefix = getConfig().getString("message-prefix", "&8[&6KibbleCommands&8]&r ");
    }
}
