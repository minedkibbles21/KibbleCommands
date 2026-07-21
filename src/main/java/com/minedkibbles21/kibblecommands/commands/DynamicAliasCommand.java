package com.minedkibbles21.kibblecommands.commands;

import com.minedkibbles21.kibblecommands.KibbleCommands;
import com.minedkibbles21.kibblecommands.managers.AliasManager;
import com.minedkibbles21.kibblecommands.models.AliasDefinition;
import com.minedkibbles21.kibblecommands.utils.MessageUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DynamicAliasCommand extends Command {
    private static final Pattern ARG_PATTERN = Pattern.compile("\\{arg:(\\d+)}");
    private final KibbleCommands plugin;
    private final AliasDefinition definition;

    public DynamicAliasCommand(KibbleCommands plugin, AliasDefinition definition) {
        super(definition.getAlias());
        this.plugin = plugin;
        this.definition = definition;
        this.setDescription(definition.getDescription().isBlank() ? "Alias for: " + definition.getCommand() : definition.getDescription());
        if (definition.hasPermission()) {
            this.setPermission(definition.getPermission());
            this.setPermissionMessage(definition.getPermissionMessage());
        }
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (this.definition.hasPermission() && !sender.hasPermission(this.definition.getPermission())) {
            MessageUtil.send(sender, this.plugin.prefix() + this.definition.getPermissionMessage());
            return true;
        }
        if (this.definition.isPlayerOnly() && !(sender instanceof Player)) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cThis command can only be used by players.");
            return true;
        }
        if (this.definition.isConsoleOnly() && sender instanceof Player) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cThis command can only be used from the console.");
            return true;
        }
        if (this.plugin.getConfig().getBoolean("require-use-permission", false) && !sender.hasPermission("kibblecommands.use")) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cYou do not have permission to use alias commands.");
            return true;
        }
        if (this.definition.getCooldown() > 0 && sender instanceof Player player) {
            long remaining = this.plugin.getCooldownManager().getRemainingCooldown(this.definition.getAlias(), player.getUniqueId(), this.definition.getCooldown());
            if (remaining > 0L) {
                MessageUtil.send(sender, this.plugin.prefix() + "&cYou must wait &e" + remaining + "s &cbefore using this command again.");
                return true;
            }
            this.plugin.getCooldownManager().recordUse(this.definition.getAlias(), player.getUniqueId());
        }
        String targetCommand = this.buildTargetCommand(sender, commandLabel, args);
        if (targetCommand.isBlank()) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cThis alias has no command configured.");
            return true;
        }
        if (this.plugin.getConfig().getBoolean("notify-on-alias-use", false)) {
            String message = this.plugin.getConfig().getString("notify-message", "&7Alias &e{alias}&7 -> &e{target}");
            MessageUtil.send(sender, this.plugin.prefix() + message, "{alias}", commandLabel, "{target}", targetCommand);
        }
        CommandSender dispatcher = this.definition.isExecuteAsConsole() ? this.plugin.getServer().getConsoleSender() : sender;
        try {
            this.plugin.getServer().dispatchCommand(dispatcher, targetCommand);
        } catch (Exception exception) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cFailed to execute the aliased command. Check console.");
            this.plugin.getLogger().severe("Error dispatching alias '/" + this.definition.getAlias() + "' -> " + targetCommand);
            this.plugin.getLogger().severe(exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        try {
            String target = AliasManager.normalizeTargetCommand(this.definition.getCommand());
            if (target.isBlank() || target.contains("{")) {
                return List.of();
            }
            String[] parts = target.split("\\s+");
            Command targetCommand = this.plugin.getServer().getCommandMap().getCommand(parts[0]);
            if (targetCommand == null || targetCommand == this) {
                return List.of();
            }
            List<String> targetArgs = new ArrayList<>();
            if (parts.length > 1) {
                targetArgs.addAll(Arrays.asList(parts).subList(1, parts.length));
            }
            if (this.definition.isPassArgs()) {
                targetArgs.addAll(Arrays.asList(args));
            }
            return targetCommand.tabComplete(sender, parts[0], targetArgs.toArray(new String[0]));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String buildTargetCommand(CommandSender sender, String commandLabel, String[] args) {
        String configuredCommand = AliasManager.normalizeTargetCommand(this.definition.getCommand());
        String joinedArgs = String.join(" ", args);
        String target = this.replaceBasicPlaceholders(sender, configuredCommand, commandLabel, joinedArgs);
        target = this.replaceArgumentPlaceholders(target, args);
        boolean commandConsumesArgs = configuredCommand.contains("{args}") || configuredCommand.contains("%args%") || ARG_PATTERN.matcher(configuredCommand).find();
        if (this.definition.isPassArgs() && args.length > 0 && !commandConsumesArgs) {
            target = target + " " + joinedArgs;
        }
        return AliasManager.normalizeTargetCommand(target);
    }

    private String replaceBasicPlaceholders(CommandSender sender, String command, String commandLabel, String joinedArgs) {
        String playerName;
        String worldName;
        String uuid;
        if (sender instanceof Player player) {
            playerName = player.getName();
            worldName = player.getWorld().getName();
            uuid = player.getUniqueId().toString();
        } else {
            playerName = "CONSOLE";
            worldName = "";
            uuid = "";
        }
        return command.replace("{alias}", commandLabel)
                .replace("{sender}", sender.getName())
                .replace("{player}", playerName)
                .replace("%player%", playerName)
                .replace("{uuid}", uuid)
                .replace("{world}", worldName)
                .replace("{args}", joinedArgs)
                .replace("%args%", joinedArgs);
    }

    private String replaceArgumentPlaceholders(String command, String[] args) {
        Matcher matcher = ARG_PATTERN.matcher(command);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            String replacement = index >= 0 && index < args.length ? args[index] : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
