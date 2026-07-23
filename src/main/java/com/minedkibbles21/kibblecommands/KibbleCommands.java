package com.minedkibbles21.kibblecommands;

import com.minedkibbles21.kibblecommands.commands.KibbleCommandsCommand;
import com.minedkibbles21.kibblecommands.gui.AdminGuiListener;
import com.minedkibbles21.kibblecommands.managers.AliasManager;
import com.minedkibbles21.kibblecommands.managers.CooldownManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class KibbleCommands extends JavaPlugin {
    private AliasManager aliasManager;
    private CooldownManager cooldownManager;
    private AdminGuiListener adminGuiListener;
    private String prefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshPrefix();
        cooldownManager = new CooldownManager();
        aliasManager = new AliasManager(this);
        adminGuiListener = new AdminGuiListener(this);
        PluginCommand command = getCommand("kibblecommands");
        if (command == null) {
            getLogger().severe("The kibblecommands command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        KibbleCommandsCommand executor = new KibbleCommandsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(adminGuiListener, this);
        aliasManager.reload();
        logCompatibilityState();
        getLogger().info("KibbleCommands v" + getDescription().getVersion() + " has been enabled.");
    }

    @Override
    public void onDisable() {
        if (aliasManager != null) {
            aliasManager.unregisterAll();
        }
        getLogger().info("KibbleCommands has been disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        refreshPrefix();
    }

    private void refreshPrefix() {
        prefix = getConfig().getString("message-prefix", "&8[&6KibbleCommands&8]&r ");
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

    public AliasManager getAliasManager() {
        return aliasManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public AdminGuiListener getAdminGuiListener() {
        return adminGuiListener;
    }

    public String prefix() {
        return prefix;
    }
}
