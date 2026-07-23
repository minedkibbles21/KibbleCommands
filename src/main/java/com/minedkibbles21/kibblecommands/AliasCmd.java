package com.minedkibbles21.kibblecommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
        
        // 4. Cooldown tracker check
        if (cfg.getCooldown() > 0 && sender instanceof Player p) {
            long remaining = plugin.getCooldowns().getRemaining(cfg.getName(), p.getUniqueId(), cfg.getCooldown());
            if (remaining > 0) {
                send(sender, plugin.prefix() + "&cYou must wait &e" + remaining + "s &cbefore doing that again.");
                return true;
            }
            plugin.getCooldowns().update(cfg.getName(), p.getUniqueId());
        }
        
        // 5. Build and execute target command
        String parsedCmd = buildTarget(sender, label, args);
        if (parsedCmd.isBlank()) {
            send(sender, plugin.prefix() + "&cTarget command is invalid/empty.");
            return true;
        }
        
        if (plugin.getConfig().getBoolean("notify-on-alias-use", false)) {
            String notify = plugin.getConfig().getString("notify-message", "&7Alias &e{alias}&7 -> &e{target}");
            String formatted = notify.replace("{alias}", label).replace("{target}", parsedCmd);
            send(sender, plugin.prefix() + formatted);
        }
        
        CommandSender runner = cfg.isConsoleExec() ? plugin.getServer().getConsoleSender() : sender;
        try {
            plugin.getServer().dispatchCommand(runner, parsedCmd);
        } catch (Exception ex) {
            send(sender, plugin.prefix() + "&cError dispathing command. Check logs.");
            plugin.getLogger().severe("Fail to dispatch command: /" + cfg.getName() + " -> " + parsedCmd);
            plugin.getLogger().severe(ex.getMessage());
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
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

    private String buildTarget(CommandSender sender, String label, String[] args) {
        String base = KibbleCommands.cleanCmd(cfg.getTarget());
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
