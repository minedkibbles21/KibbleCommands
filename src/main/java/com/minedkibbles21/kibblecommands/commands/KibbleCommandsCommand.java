package com.minedkibbles21.kibblecommands.commands;

import com.minedkibbles21.kibblecommands.KibbleCommands;
import com.minedkibbles21.kibblecommands.managers.AliasManager;
import com.minedkibbles21.kibblecommands.models.AliasDefinition;
import com.minedkibbles21.kibblecommands.utils.MessageUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class KibbleCommandsCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "remove", "list", "info", "reload", "gui", "help");
    private final KibbleCommands plugin;

    public KibbleCommandsCommand(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!this.canUseSubcommand(sender, subcommand)) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cYou do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player && this.canUseGui(player)) {
                this.plugin.getAdminGuiListener().openMain(player);
            } else {
                this.sendHelp(sender, label);
            }
            return true;
        }
        switch (subcommand) {
            case "help" -> this.sendHelp(sender, label);
            case "list" -> this.handleList(sender);
            case "reload" -> this.handleReload(sender);
            case "add" -> this.handleAdd(sender, args);
            case "remove" -> this.handleRemove(sender, args);
            case "info" -> this.handleInfo(sender, args);
            case "gui" -> this.handleGui(sender);
            default -> MessageUtil.send(sender, this.plugin.prefix() + "&cUnknown subcommand. Use &e/" + label + " help&c.");
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        String command = "/" + label;
        MessageUtil.send(sender, "&8&m                                        ");
        MessageUtil.send(sender, "  &6&lKibbleCommands &7Admin &8| &eby MinedKibbles21");
        MessageUtil.send(sender, "&8&m                                        ");
        MessageUtil.send(sender, "  &e" + command + " gui &7- Open the admin GUI.");
        MessageUtil.send(sender, "  &e" + command + " list &7- List active aliases.");
        MessageUtil.send(sender, "  &e" + command + " info &7<alias> &7- Show alias details.");
        MessageUtil.send(sender, "  &e" + command + " add &7<alias> <command...> &7- Add an alias.");
        MessageUtil.send(sender, "  &e" + command + " remove &7<alias> &7- Remove an alias.");
        MessageUtil.send(sender, "  &e" + command + " reload &7- Reload config.yml.");
        MessageUtil.send(sender, "&8&m                                        ");
        MessageUtil.send(sender, "  &7Permissions: &ekibblecommands.admin&7, &ekibblecommands.gui&7, &ekibblecommands.reload");
        MessageUtil.send(sender, "&8&m                                        ");
    }

    private void handleList(CommandSender sender) {
        Map<String, AliasDefinition> definitions = this.plugin.getAliasManager().getDefinitions();
        if (definitions.isEmpty()) {
            MessageUtil.send(sender, this.plugin.prefix() + "&7No aliases are currently registered.");
            MessageUtil.send(sender, this.plugin.prefix() + "&7Add one with &e/kc add <alias> <command...>&7.");
            return;
        }
        MessageUtil.send(sender, "&8&m                                        ");
        MessageUtil.send(sender, "  &6Registered Aliases &8(" + definitions.size() + ")");
        MessageUtil.send(sender, "&8&m                                        ");
        for (AliasDefinition definition : definitions.values()) {
            String flags = this.buildFlags(definition);
            MessageUtil.send(sender, "  &e/" + definition.getAlias() + " &8-> &a" + definition.getCommand() + (flags.isBlank() ? "" : " &7" + flags));
        }
        MessageUtil.send(sender, "&8&m                                        ");
    }

    private void handleReload(CommandSender sender) {
        this.plugin.reloadConfig();
        this.plugin.getCooldownManager().clearAll();
        this.plugin.getAliasManager().reload();
        int count = this.plugin.getAliasManager().getDefinitions().size();
        MessageUtil.send(sender, this.plugin.prefix() + "&aConfiguration reloaded. &e" + count + " alias(es) active.");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cUsage: &e/kc add <alias> <command...>");
            MessageUtil.send(sender, this.plugin.prefix() + "&7Example: &e/kc add gmc gamemode creative");
            return;
        }
        String alias = args[1].toLowerCase(Locale.ROOT);
        String targetCommand = AliasManager.normalizeTargetCommand(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (targetCommand.isBlank()) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cThe target command cannot be empty.");
            return;
        }
        String problem = this.plugin.getAliasManager().getAliasBlockReason(alias);
        if (problem != null) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cCannot create &e/" + alias + "&c: " + problem + ".");
            return;
        }
        boolean added = this.plugin.getAliasManager().addAlias(alias, targetCommand, "", "", false, false, 0, true);
        if (added) {
            MessageUtil.send(sender, this.plugin.prefix() + "&aAlias &e/" + alias + " &a-> &e" + targetCommand + " &aregistered.");
            MessageUtil.send(sender, this.plugin.prefix() + "&7Edit &econfig.yml &7for permissions, cooldowns, placeholders, or console execution.");
        } else {
            MessageUtil.send(sender, this.plugin.prefix() + "&cAlias &e/" + alias + " &ccould not be registered.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cUsage: &e/kc remove <alias>");
            return;
        }
        String alias = args[1].toLowerCase(Locale.ROOT);
        boolean removed = this.plugin.getAliasManager().removeAlias(alias);
        if (removed) {
            MessageUtil.send(sender, this.plugin.prefix() + "&aAlias &e/" + alias + " &ahas been removed.");
        } else {
            MessageUtil.send(sender, this.plugin.prefix() + "&cNo alias named &e/" + alias + " &cfound.");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cUsage: &e/kc info <alias>");
            return;
        }
        String alias = args[1].toLowerCase(Locale.ROOT);
        AliasDefinition definition = this.plugin.getAliasManager().getDefinition(alias);
        if (definition == null) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cNo alias named &e/" + alias + " &cfound.");
            return;
        }
        MessageUtil.send(sender, "&8&m                                        ");
        MessageUtil.send(sender, "  &6Alias Info: &e/" + definition.getAlias());
        MessageUtil.send(sender, "&8&m                                        ");
        MessageUtil.send(sender, "  &7Command  &8> &a" + definition.getCommand());
        MessageUtil.send(sender, "  &7Desc     &8> &f" + (definition.getDescription().isBlank() ? "&8(none)" : definition.getDescription()));
        MessageUtil.send(sender, "  &7Perm     &8> &f" + (definition.hasPermission() ? definition.getPermission() : "&8(none)"));
        MessageUtil.send(sender, "  &7PassArgs &8> &f" + (definition.isPassArgs() ? "&ayes" : "&cno"));
        MessageUtil.send(sender, "  &7Cooldown &8> &f" + (definition.getCooldown() > 0 ? definition.getCooldown() + "s" : "&8disabled"));
        MessageUtil.send(sender, "  &7Executes &8> &f" + definition.getExecuteAs());
        MessageUtil.send(sender, "  &7Flags    &8> &f" + this.buildFlags(definition));
        MessageUtil.send(sender, "&8&m                                        ");
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, this.plugin.prefix() + "&cThe GUI can only be opened by a player.");
            return;
        }
        this.plugin.getAdminGuiListener().openMain(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> this.canUseSubcommand(sender, sub))
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (subcommand.equals("remove") || subcommand.equals("info")) {
                return this.plugin.getAliasManager().getDefinitions().keySet().stream()
                        .filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private boolean canUseSubcommand(CommandSender sender, String subcommand) {
        if (this.hasAdminAccess(sender)) {
            return true;
        }
        return switch (subcommand) {
            case "reload" -> sender.hasPermission("kibblecommands.reload");
            case "", "gui" -> sender instanceof Player player && this.canUseGui(player);
            default -> false;
        };
    }

    private boolean hasAdminAccess(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("kibblecommands.admin");
    }

    private boolean canUseGui(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("kibblecommands.admin") || sender.hasPermission("kibblecommands.gui");
    }

    private String buildFlags(AliasDefinition definition) {
        List<String> flags = new ArrayList<>();
        if (definition.isPlayerOnly()) {
            flags.add("[player-only]");
        }
        if (definition.isConsoleOnly()) {
            flags.add("[console-only]");
        }
        if (definition.getCooldown() > 0) {
            flags.add("[cooldown:" + definition.getCooldown() + "s]");
        }
        if (definition.hasPermission()) {
            flags.add("[permission]");
        }
        if (definition.isExecuteAsConsole()) {
            flags.add("[runs-as-console]");
        }
        return flags.isEmpty() ? "(none)" : String.join(" ", flags);
    }
}
