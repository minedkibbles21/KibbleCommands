package com.minedkibbles21.kibblecommands;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

// KibbleCommands - Custom alias manager for modern Minecraft servers.
// Loads aliases from config and registers them dynamically on Bukkit's command map.
public final class KibbleCommands extends JavaPlugin implements Listener {
    private static final String MATCH_REGEX = "[a-z0-9_-]+";

    private final Map<String, AliasCmd> commandsMap = new LinkedHashMap<>();
    private final Map<String, AliasConfig> definitions = new LinkedHashMap<>();
    
    private Cooldowns cooldowns;
    private AdminMenu adminMenu;
    private Database database;
    private Economy economy = null;
    private String msgPrefix;
    private File logFile;

    @Override
    public void onEnable() {
        // Save resource config if not exists
        saveDefaultConfig();
        loadPrefix();
        
        // Setup Database Connection (SQLite/MySQL)
        database = new Database(this);
        database.connect();
        
        cooldowns = new Cooldowns(this);
        adminMenu = new AdminMenu(this);
        
        // Setup Vault Economy Integration
        setupEconomy();
        
        // Setup Log File
        setupLogging();
        
        PluginCommand mainCmd = getCommand("kibblecommands");
        if (mainCmd == null) {
            getLogger().severe("Fail to locate kibblecommands in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        AdminCmd executor = new AdminCmd(this);
        mainCmd.setExecutor(executor);
        mainCmd.setTabCompleter(executor);
        
        // Register Event Listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(adminMenu, this);
        getServer().getPluginManager().registerEvents(new AliasCmd.WarmupListener(), this);
        
        reloadAliases();
        checkCompat();
        
        // If there are players already online (e.g. reload), load their cooldowns
        for (Player p : Bukkit.getOnlinePlayers()) {
            cooldowns.loadPlayer(p.getUniqueId());
        }
        
        getLogger().info("KibbleCommands enabled (v" + getDescription().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        unregisterAll();
        if (database != null) {
            database.disconnect();
        }
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
            
            // Read target commands (supporting single 'command' string or 'commands' list)
            List<String> targetList = new ArrayList<>();
            if (sec.isList("commands")) {
                targetList.addAll(sec.getStringList("commands"));
            } else {
                String cmd = sec.getString("command", "");
                if (!cmd.isBlank()) {
                    targetList.add(cmd);
                }
            }
            
            // Optional custom tab suggestions
            List<String> tabList = sec.isList("tab-complete") ? sec.getStringList("tab-complete") : Collections.emptyList();
            
            // Read Vault cost, warmup, and custom message action feedback
            double cost = sec.getDouble("cost", 0.0);
            int warmup = sec.getInt("warmup", 0);
            String actionMessage = sec.getString("send-message", "");
            
            // Ignore alias if targets list is empty AND send-message is also empty
            if (targetList.isEmpty() && actionMessage.isBlank()) {
                getLogger().warning("Skipping alias '" + key + "': has no target command nor send-message action configured.");
                continue;
            }
            
            AliasConfig cfg = new AliasConfig(
                    name,
                    targetList.stream().map(KibbleCommands::cleanCmd).toList(),
                    sec.getString("description", ""),
                    sec.getString("permission", ""),
                    sec.getString("permission-message", "&cYou do not have permission to use this command."),
                    sec.getBoolean("console-only", false),
                    sec.getBoolean("player-only", false),
                    sec.getInt("cooldown", 0),
                    sec.getBoolean("pass-args", true),
                    readExec(sec),
                    tabList,
                    cost,
                    warmup,
                    actionMessage
            );
            
            if (bind(cfg)) {
                definitions.put(cfg.getName(), cfg);
            }
        }
        
        // Sync registered autocomplete lists to all online client players
        syncCommands();
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
                Collections.singletonList(cleanCmd(target)),
                desc,
                permission,
                "&cYou do not have permission to use this command.",
                consoleOnly,
                playerOnly,
                cooldown,
                passArgs,
                executeAs,
                Collections.emptyList(),
                0.0,
                0,
                ""
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
        syncCommands();
        return true;
    }

    public boolean removeAlias(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!definitions.containsKey(key)) return false;
        
        definitions.remove(key);
        unbind(key);
        getConfig().set("aliases." + key, null);
        saveConfig();
        syncCommands();
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

    // Sends command registration updates to all online clients dynamically.
    public void syncCommands() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateCommands();
        }
    }

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
            getLogger().info("Bound dynamic alias: /" + cfg.getName() + " -> " + cfg.getTargets());
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

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    private void setupLogging() {
        File logsDir = new File(getDataFolder(), "logs");
        if (!logsDir.exists()) logsDir.mkdirs();
        
        logFile = new File(logsDir, "use.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not create execution logs file!", ex);
            }
        }
    }

    public synchronized void logExecution(String sender, String alias, List<String> targets, double cost) {
        if (logFile == null) return;
        
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String costStr = cost > 0 ? "Cost: $" + cost : "Free";
        String logLine = String.format("[%s] User %s run alias /%s -> Targets: %s (%s)", timeStr, sender, alias, targets, costStr);
        
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(logLine);
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Failed to write audit logs record", ex);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.loadPlayer(event.getPlayer().getUniqueId());
        // Trigger command sync when player logs in
        syncCommands();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.unloadPlayer(event.getPlayer().getUniqueId());
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

    public Database getDatabase() {
        return database;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public String prefix() {
        return msgPrefix;
    }

    private void loadPrefix() {
        msgPrefix = getConfig().getString("message-prefix", "&8[&6KibbleCommands&8]&r ");
    }
}
