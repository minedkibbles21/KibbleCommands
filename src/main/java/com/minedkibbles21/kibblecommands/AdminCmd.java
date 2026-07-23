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

// Main Command Router for /kibblecommands and its aliases.
// Implements CommandExecutor and TabCompleter directly.
public class AdminCmd implements CommandExecutor, TabCompleter {
    private static final List<String> SUB_LIST = Arrays.asList("add", "remove", "list", "info", "reload", "gui", "help");
    private final KibbleCommands plugin;

    public AdminCmd(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        
        // Quick permission safety check
        if (!hasSubPerm(sender, sub)) {
            send(sender, plugin.prefix() + "&cYou do not have permission to use this command.");
            return true;
        }
        
        // If no arguments, default to gui (for players) or help text
        if (args.length == 0) {
            if (sender instanceof Player p && hasGuiAccess(p)) {
                plugin.getAdminMenu().open(p);
            } else {
                showHelp(sender, label);
            }
            return true;
        }
        
        switch (sub) {
            case "help" -> showHelp(sender, label);
            case "list" -> handleListing(sender);
            case "reload" -> handleReload(sender);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info" -> handleInfo(sender, args);
            case "gui" -> {
                if (!(sender instanceof Player p)) {
                    send(sender, plugin.prefix() + "&cThe GUI can only be opened by a player.");
                    return true;
                }
                plugin.getAdminMenu().open(p);
            }
            default -> send(sender, plugin.prefix() + "&cUnknown subcommand. Try &e/" + label + " help&c.");
        }
        return true;
    }

    private void showHelp(CommandSender sender, String label) {
        String base = "/" + label;
        send(sender, "&8&m                                        ");
        send(sender, "  &6&lKibbleCommands &7Admin &8| &eby MinedKibbles21");
        send(sender, "&8&m                                        ");
        send(sender, "  &e" + base + " gui &7- Open the admin GUI.");
        send(sender, "  &e" + base + " list &7- List active aliases.");
        send(sender, "  &e" + base + " info &7<alias> &7- Show alias details.");
        send(sender, "  &e" + base + " add &7<alias> <command...> &7- Add an alias.");
        send(sender, "  &e" + base + " remove &7<alias> &7- Remove an alias.");
        send(sender, "  &e" + base + " reload &7- Reload config.yml.");
        send(sender, "&8&m                                        ");
        send(sender, "  &7Permissions: &ekibblecommands.admin&7, &ekibblecommands.gui&7, &ekibblecommands.reload");
        send(sender, "&8&m                                        ");
    }

    private void handleListing(CommandSender sender) {
        Map<String, AliasConfig> map = plugin.getDefinitions();
        if (map.isEmpty()) {
            send(sender, plugin.prefix() + "&7No aliases are currently registered.");
            send(sender, plugin.prefix() + "&7Add one with &e/kc add <alias> <command...>&7.");
            return;
        }
        send(sender, "&8&m                                        ");
        send(sender, "  &6Registered Aliases &8(" + map.size() + ")");
        send(sender, "&8&m                                        ");
        for (AliasConfig cfg : map.values()) {
            String flags = showFlags(cfg);
            send(sender, "  &e/" + cfg.getName() + " &8-> &a" + cfg.getTarget() + (flags.isBlank() ? "" : " &7" + flags));
        }
        send(sender, "&8&m                                        ");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getCooldowns().reset();
        plugin.reloadAliases();
        int count = plugin.getDefinitions().size();
        send(sender, plugin.prefix() + "&aConfiguration reloaded. &e" + count + " alias(es) active.");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, plugin.prefix() + "&cUsage: &e/kc add <alias> <command...>");
            send(sender, plugin.prefix() + "&7Example: &e/kc add gmc gamemode creative");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        String target = KibbleCommands.cleanCmd(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (target.isBlank()) {
            send(sender, plugin.prefix() + "&cThe target command cannot be empty.");
            return;
        }
        String err = plugin.getAliasBlockReason(name);
        if (err != null) {
            send(sender, plugin.prefix() + "&cCannot create &e/" + name + "&c: " + err + ".");
            return;
        }
        boolean ok = plugin.addAlias(name, target, "", "", false, false, 0, true);
        if (ok) {
            send(sender, plugin.prefix() + "&aAlias &e/" + name + " &a-> &e" + target + " &aregistered.");
            send(sender, plugin.prefix() + "&7Edit &econfig.yml &7for permissions, cooldowns, or console execution.");
        } else {
            send(sender, plugin.prefix() + "&cAlias &e/" + name + " &ccould not be registered.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, plugin.prefix() + "&cUsage: &e/kc remove <alias>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        boolean ok = plugin.removeAlias(name);
        if (ok) {
            send(sender, plugin.prefix() + "&aAlias &e/" + name + " &ahas been removed.");
        } else {
            send(sender, plugin.prefix() + "&cNo alias named &e/" + name + " &cfound.");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, plugin.prefix() + "&cUsage: &e/kc info <alias>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        AliasConfig cfg = plugin.getDefinitions().get(name);
        if (cfg == null) {
            send(sender, plugin.prefix() + "&cNo alias named &e/" + name + " &cfound.");
            return;
        }
        send(sender, "&8&m                                        ");
        send(sender, "  &6Alias Info: &e/" + cfg.getName());
        send(sender, "&8&m                                        ");
        send(sender, "  &7Command  &8> &a" + cfg.getTarget());
        send(sender, "  &7Desc     &8> &f" + (cfg.getDesc().isBlank() ? "&8(none)" : cfg.getDesc()));
        send(sender, "  &7Perm     &8> &f" + (cfg.hasPerm() ? cfg.getPermission() : "&8(none)"));
        send(sender, "  &7PassArgs &8> &f" + (cfg.isPassArgs() ? "&ayes" : "&cno"));
        send(sender, "  &7Cooldown &8> &f" + (cfg.getCooldown() > 0 ? cfg.getCooldown() + "s" : "&8disabled"));
        send(sender, "  &7Executes &8> &f" + cfg.getExecuteAs());
        send(sender, "  &7Flags    &8> &f" + showFlags(cfg));
        send(sender, "&8&m                                        ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUB_LIST.stream()
                    .filter(sub -> hasSubPerm(sender, sub))
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

    private boolean hasSubPerm(CommandSender sender, String sub) {
        if (sender.isOp() || sender.hasPermission("kibblecommands.admin")) {
            return true;
        }
        return switch (sub) {
            case "reload" -> sender.hasPermission("kibblecommands.reload");
            case "", "gui" -> sender instanceof Player p && hasGuiAccess(p);
            default -> false;
        };
    }

    private boolean hasGuiAccess(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("kibblecommands.admin") || sender.hasPermission("kibblecommands.gui");
    }

    private String showFlags(AliasConfig cfg) {
        List<String> list = new ArrayList<>();
        if (cfg.isPlayerOnly()) list.add("[player-only]");
        if (cfg.isConsoleOnly()) list.add("[console-only]");
        if (cfg.getCooldown() > 0) list.add("[cooldown:" + cfg.getCooldown() + "s]");
        if (cfg.hasPerm()) list.add("[permission]");
        if (cfg.isConsoleExec()) list.add("[runs-as-console]");
        return list.isEmpty() ? "" : String.join(" ", list);
    }

    private void send(CommandSender target, String text) {
        target.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }
}
