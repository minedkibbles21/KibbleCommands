package com.minedkibbles21.kibblecommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "remove", "list", "info", "reload", "gui", "help");
    private final KibbleCommands plugin;

    public AdminCommand(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!canUseSubcommand(sender, subcommand)) {
            sendMsg(sender, plugin.prefix() + "&cYou do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player && canUseGui(player)) {
                plugin.getAdminGui().openMain(player);
            } else {
                sendHelp(sender, label);
            }
            return true;
        }
        switch (subcommand) {
            case "help" -> sendHelp(sender, label);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info" -> handleInfo(sender, args);
            case "gui" -> handleGui(sender);
            default -> sendMsg(sender, plugin.prefix() + "&cUnknown subcommand. Use &e/" + label + " help&c.");
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        String command = "/" + label;
        sendMsg(sender, "&8&m                                        ");
        sendMsg(sender, "  &6&lKibbleCommands &7Admin &8| &eby MinedKibbles21");
        sendMsg(sender, "&8&m                                        ");
        sendMsg(sender, "  &e" + command + " gui &7- Open the admin GUI.");
        sendMsg(sender, "  &e" + command + " list &7- List active aliases.");
        sendMsg(sender, "  &e" + command + " info &7<alias> &7- Show alias details.");
        sendMsg(sender, "  &e" + command + " add &7<alias> <command...> &7- Add an alias.");
        sendMsg(sender, "  &e" + command + " remove &7<alias> &7- Remove an alias.");
        sendMsg(sender, "  &e" + command + " reload &7- Reload config.yml.");
        sendMsg(sender, "&8&m                                        ");
        sendMsg(sender, "  &7Permissions: &ekibblecommands.admin&7, &ekibblecommands.gui&7, &ekibblecommands.reload");
        sendMsg(sender, "&8&m                                        ");
    }

    private void handleList(CommandSender sender) {
        Map<String, AliasDefinition> definitions = plugin.getDefinitions();
        if (definitions.isEmpty()) {
            sendMsg(sender, plugin.prefix() + "&7No aliases are currently registered.");
            sendMsg(sender, plugin.prefix() + "&7Add one with &e/kc add <alias> <command...>&7.");
            return;
        }
        sendMsg(sender, "&8&m                                        ");
        sendMsg(sender, "  &6Registered Aliases &8(" + definitions.size() + ")");
        sendMsg(sender, "&8&m                                        ");
        for (AliasDefinition definition : definitions.values()) {
            String flags = buildFlags(definition);
            sendMsg(sender, "  &e/" + definition.getAlias() + " &8-> &a" + definition.getCommand() + (flags.isBlank() ? "" : " &7" + flags));
        }
        sendMsg(sender, "&8&m                                        ");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getCooldownTracker().clearAll();
        plugin.reloadAliases();
        int count = plugin.getDefinitions().size();
        sendMsg(sender, plugin.prefix() + "&aConfiguration reloaded. &e" + count + " alias(es) active.");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMsg(sender, plugin.prefix() + "&cUsage: &e/kc add <alias> <command...>");
            sendMsg(sender, plugin.prefix() + "&7Example: &e/kc add gmc gamemode creative");
            return;
        }
        String alias = args[1].toLowerCase(Locale.ROOT);
        String targetCommand = KibbleCommands.normalizeTargetCommand(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (targetCommand.isBlank()) {
            sendMsg(sender, plugin.prefix() + "&cThe target command cannot be empty.");
            return;
        }
        String problem = plugin.getAliasBlockReason(alias);
        if (problem != null) {
            sendMsg(sender, plugin.prefix() + "&cCannot create &e/" + alias + "&c: " + problem + ".");
            return;
        }
        boolean added = plugin.addAlias(alias, targetCommand, "", "", false, false, 0, true);
        if (added) {
            sendMsg(sender, plugin.prefix() + "&aAlias &e/" + alias + " &a-> &e" + targetCommand + " &aregistered.");
            sendMsg(sender, plugin.prefix() + "&7Edit &econfig.yml &7for permissions, cooldowns, placeholders, or console execution.");
        } else {
            sendMsg(sender, plugin.prefix() + "&cAlias &e/" + alias + " &ccould not be registered.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMsg(sender, plugin.prefix() + "&cUsage: &e/kc remove <alias>");
            return;
        }
        String alias = args[1].toLowerCase(Locale.ROOT);
        boolean removed = plugin.removeAlias(alias);
        if (removed) {
            sendMsg(sender, plugin.prefix() + "&aAlias &e/" + alias + " &ahas been removed.");
        } else {
            sendMsg(sender, plugin.prefix() + "&cNo alias named &e/" + alias + " &cfound.");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMsg(sender, plugin.prefix() + "&cUsage: &e/kc info <alias>");
            return;
        }
        String alias = args[1].toLowerCase(Locale.ROOT);
        AliasDefinition definition = plugin.getDefinitions().get(alias);
        if (definition == null) {
            sendMsg(sender, plugin.prefix() + "&cNo alias named &e/" + alias + " &cfound.");
            return;
        }
        sendMsg(sender, "&8&m                                        ");
        sendMsg(sender, "  &6Alias Info: &e/" + definition.getAlias());
        sendMsg(sender, "&8&m                                        ");
        sendMsg(sender, "  &7Command  &8> &a" + definition.getCommand());
        sendMsg(sender, "  &7Desc     &8> &f" + (definition.getDescription().isBlank() ? "&8(none)" : definition.getDescription()));
        sendMsg(sender, "  &7Perm     &8> &f" + (definition.hasPermission() ? definition.getPermission() : "&8(none)"));
        sendMsg(sender, "  &7PassArgs &8> &f" + (definition.isPassArgs() ? "&ayes" : "&cno"));
        sendMsg(sender, "  &7Cooldown &8> &f" + (definition.getCooldown() > 0 ? definition.getCooldown() + "s" : "&8disabled"));
        sendMsg(sender, "  &7Executes &8> &f" + definition.getExecuteAs());
        sendMsg(sender, "  &7Flags    &8> &f" + buildFlags(definition));
        sendMsg(sender, "&8&m                                        ");
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, plugin.prefix() + "&cThe GUI can only be opened by a player.");
            return;
        }
        plugin.getAdminGui().openMain(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> canUseSubcommand(sender, sub))
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (subcommand.equals("remove") || subcommand.equals("info")) {
                return plugin.getDefinitions().keySet().stream()
                        .filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private boolean canUseSubcommand(CommandSender sender, String subcommand) {
        if (hasAdminAccess(sender)) {
            return true;
        }
        return switch (subcommand) {
            case "reload" -> sender.hasPermission("kibblecommands.reload");
            case "", "gui" -> sender instanceof Player player && canUseGui(player);
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

    private void sendMsg(CommandSender sender, String msg) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
