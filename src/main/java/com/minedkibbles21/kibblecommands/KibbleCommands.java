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

// KibbleCommands - Custom alias manager for modern Minecraft servers.
// Loads aliases from config and registers them dynamically on Bukkit's command map.
public final class KibbleCommands extends JavaPlugin {
    private static final String MATCH_REGEX = "[a-z0-9_-]+";

    private final Map<String, AliasCmd> commandsMap = new LinkedHashMap<>();
    private final Map<String, AliasConfig> definitions = new LinkedHashMap<>();
    
    private Cooldowns cooldowns;
    private AdminMenu adminMenu;
    private String msgPrefix;

    @Override
    public void onEnable() {
        // Save resource config if not exists
        saveDefaultConfig();
        loadPrefix();
        
        cooldowns = new Cooldowns();
        adminMenu = new AdminMenu(this);
        
        PluginCommand mainCmd = getCommand("kibblecommands");
        if (mainCmd == null) {
            getLogger().severe("Fail to locate kibblecommands in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        AdminCmd executor = new AdminCmd(this);
        mainCmd.setExecutor(executor);
        mainCmd.setTabCompleter(executor);
        
        // Listeners
        getServer().getPluginManager().registerEvents(adminMenu, this);
        reloadAliases();
        checkCompat();
        
        getLogger().info("KibbleCommands enabled (v" + getDescription().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        unregisterAll();
        getLogger().info("KibbleCommands disabled.");
    }

    public void reloadAliases() {
        unregisterAll();
        definitions.clear();
        
        ConfigurationSection root = getConfig().getConfigurationSection("aliases");
        if (root == null) {
            getLogger().info("No custom aliases found in config.yml.");
            return;
        }
        
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            
            String name = key.toLowerCase(Locale.ROOT);
            String issue = getAliasBlockReason(name);
            if (issue != null) {
                getLogger().warning("Skipping alias '" + key + "': " + issue);
                continue;
            }
            
            String cmdString = cleanCmd(sec.getString("command", ""));
            if (cmdString.isBlank()) {
                getLogger().warning("Skipping alias '" + key + "': command field is empty.");
                continue;
            }
            
            AliasConfig cfg = new AliasConfig(
                    name,
                    cmdString,
                    sec.getString("description", ""),
                    sec.getString("permission", ""),
                    sec.getString("permission-message", "&cYou do not have permission to use this command."),
                    sec.getBoolean("console-only", false),
                    sec.getBoolean("player-only", false),
                    sec.getInt("cooldown", 0),
                    sec.getBoolean("pass-args", true),
                    readExec(sec)
            );
            
            if (bind(cfg)) {
                definitions.put(cfg.getName(), cfg);
            }
        }
        getLogger().info("Registered " + definitions.size() + " alias(es).");
    }

    public boolean addAlias(String name, String target, String desc, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs) {
        return addAlias(name, target, desc, permission, playerOnly, consoleOnly, cooldown, passArgs, "sender");
    }

    public boolean addAlias(String name, String target, String desc, String permission, boolean playerOnly, boolean consoleOnly, int cooldown, boolean passArgs, String executeAs) {
        String key = name.toLowerCase(Locale.ROOT);
        if (getAliasBlockReason(key) != null) return false;
        
        AliasConfig cfg = new AliasConfig(
                key,
                cleanCmd(target),
                desc,
                permission,
                "&cYou do not have permission to use this command.",
                consoleOnly,
                playerOnly,
                cooldown,
                passArgs,
                executeAs
        );
        
        if (cfg.getTarget().isBlank() || !bind(cfg)) {
            return false;
        }
        
        // Write back to config file
        String path = "aliases." + key;
        getConfig().set(path + ".command", cfg.getTarget());
        if (!cfg.getDesc().isBlank()) {
            getConfig().set(path + ".description", cfg.getDesc());
        }
        if (cfg.hasPerm()) {
            getConfig().set(path + ".permission", cfg.getPermission());
            getConfig().set(path + ".permission-message", cfg.getPermMessage());
        }
        getConfig().set(path + ".player-only", cfg.isPlayerOnly());
        getConfig().set(path + ".console-only", cfg.isConsoleOnly());
        getConfig().set(path + ".cooldown", cfg.getCooldown());
        getConfig().set(path + ".pass-args", cfg.isPassArgs());
        getConfig().set(path + ".execute-as", cfg.getExecuteAs());
        saveConfig();
        
        definitions.put(key, cfg);
        return true;
    }

    public boolean removeAlias(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!definitions.containsKey(key)) return false;
        
        definitions.remove(key);
        unbind(key);
        getConfig().set("aliases." + key, null);
        saveConfig();
        return true;
    }

    public void unregisterAll() {
        CommandMap map = getMap();
        for (AliasCmd cmd : commandsMap.values()) {
            removeCmd(map, cmd);
        }
        commandsMap.clear();
    }

    public String getAliasBlockReason(String name) {
        if (!name.matches(MATCH_REGEX)) {
            return "name must be alphanumeric/hyphens/underscores only";
        }
        if (definitions.containsKey(name)) {
            return "alias name already registered";
        }
        if (name.equals("kibblecommands") || name.equals("kc") || name.equals("kibblecmd")) {
            return "name is reserved by plugin";
        }
        List<String> list = getConfig().getStringList("blocked-aliases");
        if (list.stream().anyMatch(name::equalsIgnoreCase)) {
            return "name blocked by server config";
        }
        
        CommandMap map = getMap();
        Command existing = map != null ? map.getCommand(name) : null;
        if (existing != null) {
            String origin = (existing instanceof PluginCommand pc) ? pc.getPlugin().getName() : existing.getClass().getSimpleName();
            return "command already managed by " + origin;
        }
        return null;
    }

    // Helper to strip leading slashes from targeted commands.
    public static String cleanCmd(String cmd) {
        if (cmd == null) return "";
        String s = cmd.trim();
        while (s.startsWith("/")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    private String readExec(ConfigurationSection sec) {
        String exec = sec.getString("execute-as", "");
        if (exec.isBlank() && sec.getBoolean("run-as-console", false)) {
            return "console";
        }
        return "console".equalsIgnoreCase(exec) ? "console" : "sender";
    }

    private boolean bind(AliasConfig cfg) {
        try {
            CommandMap map = getMap();
            if (map == null) {
                getLogger().severe("Bukkit CommandMap is not accessible.");
                return false;
            }
            AliasCmd cmd = new AliasCmd(this, cfg);
            map.register(getName().toLowerCase(Locale.ROOT), cmd);
            commandsMap.put(cfg.getName(), cmd);
            getLogger().info("Bound dynamic alias: /" + cfg.getName() + " -> " + cfg.getTarget());
            return true;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to bind alias " + cfg.getName(), ex);
            return false;
        }
    }

    private void unbind(String name) {
        AliasCmd cmd = commandsMap.remove(name);
        if (cmd != null) {
            removeCmd(getMap(), cmd);
        }
    }

    // Dynamic unregistration utilizing Java Reflection to update Bukkit's internal SimpleCommandMap map.
    private void removeCmd(CommandMap map, AliasCmd cmd) {
        if (map == null) return;
        cmd.unregister(map);
        
        if (map instanceof SimpleCommandMap smap) {
            try {
                Field knownField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> known = (Map<String, Command>) knownField.get(smap);
                known.entrySet().removeIf(entry -> entry.getValue() == cmd);
            } catch (ReflectiveOperationException ex) {
                getLogger().log(Level.WARNING, "Failed to clean commands map reference for /" + cmd.getName(), ex);
            }
        }
    }

    private CommandMap getMap() {
        try {
            return Bukkit.getServer().getCommandMap();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to fetch Bukkit CommandMap", ex);
            return null;
        }
    }

    private void checkCompat() {
        checkHook("LuckPerms");
        checkHook("Vault");
        checkHook("Essentials");
    }

    private void checkHook(String pName) {
        Plugin other = getServer().getPluginManager().getPlugin(pName);
        if (other != null && other.isEnabled()) {
            getLogger().info("Detected " + pName + "; hook established.");
        }
    }

    public Map<String, AliasConfig> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public Cooldowns getCooldowns() {
        return cooldowns;
    }

    public AdminMenu getAdminMenu() {
        return adminMenu;
    }

    public String prefix() {
        return msgPrefix;
    }

    private void loadPrefix() {
        msgPrefix = getConfig().getString("message-prefix", "&8[&6KibbleCommands&8]&r ");
    }
}
