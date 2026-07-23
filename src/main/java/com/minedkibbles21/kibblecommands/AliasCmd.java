package com.minedkibbles21.kibblecommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// Represents a dynamically registered alias command.
// Extends the standard Bukkit Command class.
public class AliasCmd extends Command {
    private static final Pattern ARG_REGEX = Pattern.compile("\\{arg:(\\d+)}");
    private final KibbleCommands plugin;
    private final AliasConfig cfg;

    public AliasCmd(KibbleCommands plugin, AliasConfig cfg) {
        super(cfg.getName());
        this.plugin = plugin;
        this.cfg = cfg;
        
        setDescription(cfg.getDesc().isBlank() ? "Alias command: /" + cfg.getTarget() : cfg.getDesc());
        if (cfg.hasPerm()) {
            setPermission(cfg.getPermission());
            setPermissionMessage(cfg.getPermMessage());
        }
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // 1. Permission checks
        if (cfg.hasPerm() && !sender.hasPermission(cfg.getPermission())) {
            send(sender, plugin.prefix() + cfg.getPermMessage());
            return true;
        }
        
        // 2. Sender type restrictions
        if (cfg.isPlayerOnly() && !(sender instanceof Player)) {
            send(sender, plugin.prefix() + "&cThis command can only be run by a player.");
            return true;
        }
        if (cfg.isConsoleOnly() && sender instanceof Player) {
            send(sender, plugin.prefix() + "&cThis command can only be run from the console.");
            return true;
        }
        
        // 3. Global usage permission check if configured
        if (plugin.getConfig().getBoolean("require-use-permission", false) && !sender.hasPermission("kibblecommands.use")) {
            send(sender, plugin.prefix() + "&cYou do not have permission to run alias commands.");
            return true;
        }

        // 4. Vault Economy check (if cost configured & sender is player)
        if (cfg.getCost() > 0 && sender instanceof Player p) {
            if (!plugin.hasEconomy()) {
                send(sender, plugin.prefix() + "&cVault economy is not active on this server.");
                return true;
            }
            double bal = plugin.getEconomy().getBalance(p);
            if (bal < cfg.getCost()) {
                send(sender, plugin.prefix() + "&cYou need at least &e$" + cfg.getCost() + " &cto run this command (current: &e$" + bal + "&c).");
                return true;
            }
        }
        
        // 5. Cooldown tracker check
        if (cfg.getCooldown() > 0 && sender instanceof Player p) {
            long remaining = plugin.getCooldowns().getRemaining(cfg.getName(), p.getUniqueId(), cfg.getCooldown());
            if (remaining > 0) {
                send(sender, plugin.prefix() + "&cYou must wait &e" + remaining + "s &cbefore doing that again.");
                return true;
            }
        }

        // Charge the Vault cost now that we passed checks
        if (cfg.getCost() > 0 && sender instanceof Player p) {
            plugin.getEconomy().withdrawPlayer(p, cfg.getCost());
            send(sender, plugin.prefix() + "&aCharged &e$" + cfg.getCost() + " &afor using this command.");
        }

        // Update cooldown tracking timestamp
        if (cfg.getCooldown() > 0 && sender instanceof Player p) {
            plugin.getCooldowns().update(cfg.getName(), p.getUniqueId());
        }
        
        // 6. Build and execute target commands sequentially
        CommandSender runner = cfg.isConsoleExec() ? plugin.getServer().getConsoleSender() : sender;
        for (String targetStr : cfg.getTargets()) {
            String parsedCmd = buildTarget(sender, label, args, targetStr);
            if (parsedCmd.isBlank()) continue;
            
            if (plugin.getConfig().getBoolean("notify-on-alias-use", false)) {
                String notify = plugin.getConfig().getString("notify-message", "&7Alias &e{alias}&7 -> &e{target}");
                String formatted = notify.replace("{alias}", label).replace("{target}", parsedCmd);
                send(sender, plugin.prefix() + formatted);
            }
            
            try {
                plugin.getServer().dispatchCommand(runner, parsedCmd);
            } catch (Exception ex) {
                send(sender, plugin.prefix() + "&cError dispatching command. Check logs.");
                plugin.getLogger().severe("Fail to dispatch command: /" + cfg.getName() + " -> " + parsedCmd);
                plugin.getLogger().severe(ex.getMessage());
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // If custom tab-completion list is configured, parse suggestions
        if (!cfg.getTabSuggestions().isEmpty()) {
            String currentArg = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> rawSuggestions = cfg.getTabSuggestions();
            List<String> finalSuggestions = new ArrayList<>();
            
            for (String suggestion : rawSuggestions) {
                if ("<player>".equalsIgnoreCase(suggestion)) {
                    // Populate online players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        finalSuggestions.add(player.getName());
                    }
                } else {
                    finalSuggestions.add(suggestion);
                }
            }
            
            return finalSuggestions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        // Fallback: Dynamic tab completion based on primary target command
        try {
            String target = KibbleCommands.cleanCmd(cfg.getTarget());
            if (target.isBlank() || target.contains("{")) return List.of();
            
            String[] split = target.split("\\s+");
            Command command = plugin.getServer().getCommandMap().getCommand(split[0]);
            if (command == null || command == this) return List.of();
            
            List<String> combinedArgs = new ArrayList<>();
            if (split.length > 1) {
                combinedArgs.addAll(Arrays.asList(split).subList(1, split.length));
            }
            if (cfg.isPassArgs()) {
                combinedArgs.addAll(Arrays.asList(args));
            }
            return command.tabComplete(sender, split[0], combinedArgs.toArray(new String[0]));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String buildTarget(CommandSender sender, String label, String[] args, String targetCmd) {
        String base = KibbleCommands.cleanCmd(targetCmd);
        String joined = String.join(" ", args);
        String swap = swapVars(sender, base, label, joined);
        swap = swapArgs(swap, args);
        
        // Pass trailing arguments if not manually placed via {args} or {arg:x}
        boolean hasArgToken = base.contains("{args}") || base.contains("%args%") || ARG_REGEX.matcher(base).find();
        if (cfg.isPassArgs() && args.length > 0 && !hasArgToken) {
            swap = swap + " " + joined;
        }
        return KibbleCommands.cleanCmd(swap);
    }

    private String swapVars(CommandSender sender, String input, String label, String joined) {
        String name = "CONSOLE", world = "", uid = "";
        if (sender instanceof Player p) {
            name = p.getName();
            world = p.getWorld().getName();
            uid = p.getUniqueId().toString();
        }
        return input.replace("{alias}", label)
                .replace("{sender}", sender.getName())
                .replace("{player}", name)
                .replace("%player%", name)
                .replace("{uuid}", uid)
                .replace("{world}", world)
                .replace("{args}", joined)
                .replace("%args%", joined);
    }

    private String swapArgs(String input, String[] args) {
        Matcher match = ARG_REGEX.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (match.find()) {
            int pos = Integer.parseInt(match.group(1)) - 1;
            String val = (pos >= 0 && pos < args.length) ? args[pos] : "";
            match.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        match.appendTail(sb);
        return sb.toString();
    }

    private void send(CommandSender target, String text) {
        target.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }
}
