package com.minedkibbles21.kibblecommands;

import com.minedkibbles21.kibblecommands.commands.KibbleCommandsCommand;
import com.minedkibbles21.kibblecommands.gui.AdminGuiListener;
import com.minedkibbles21.kibblecommands.managers.AliasManager;
import com.minedkibbles21.kibblecommands.managers.CooldownManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class KibbleCommands
extends JavaPlugin {
    private AliasManager aliasManager;
    private CooldownManager cooldownManager;
    private AdminGuiListener adminGuiListener;
    private String prefix;

    public void onEnable() {
        this.saveDefaultConfig();
        this.refreshPrefix();
        this.cooldownManager = new CooldownManager();
        this.aliasManager = new AliasManager(this);
        this.adminGuiListener = new AdminGuiListener(this);
        PluginCommand command = this.getCommand("kibblecommands");
        if (command == null) {
            this.getLogger().severe("The kibblecommands command is missing from plugin.yml.");
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        KibbleCommandsCommand executor = new KibbleCommandsCommand(this);
        command.setExecutor((CommandExecutor)executor);
        command.setTabCompleter((TabCompleter)executor);
        this.getServer().getPluginManager().registerEvents((Listener)this.adminGuiListener, (Plugin)this);
        this.aliasManager.reload();
        this.logCompatibilityState();
        this.getLogger().info("KibbleCommands v" + this.getDescription().getVersion() + " by MinedKibbles21 has been enabled.");
        this.getLogger().info("Use /kc help or /kc gui for management.");
    }

    public void onDisable() {
        if (this.aliasManager != null) {
            this.aliasManager.unregisterAll();
        }
        this.getLogger().info("KibbleCommands has been disabled.");
    }

    public void reloadConfig() {
        super.reloadConfig();
        this.refreshPrefix();
    }

    private void refreshPrefix() {
        this.prefix = this.getConfig().getString("message-prefix", "&8[&6KibbleCommands&8]&r ");
    }

    private void logCompatibilityState() {
        this.logPluginState("LuckPerms");
        this.logPluginState("Vault");
        this.logPluginState("Essentials");
    }

    private void logPluginState(String pluginName) {
        Plugin otherPlugin = this.getServer().getPluginManager().getPlugin(pluginName);
        if (otherPlugin != null && otherPlugin.isEnabled()) {
            this.getLogger().info("Detected " + pluginName + "; Bukkit permissions and command dispatch will use it normally.");
        }
    }

    public AliasManager getAliasManager() {
        return this.aliasManager;
    }

    public CooldownManager getCooldownManager() {
        return this.cooldownManager;
    }

    public AdminGuiListener getAdminGuiListener() {
        return this.adminGuiListener;
    }

    public String prefix() {
        return this.prefix;
    }
}

