package com.minedkibbles21.kibblecommands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

// Built-in Inventory menu for administrative management.
// Implements Listener to handle click events dynamically.
public class AdminMenu implements Listener {
    private final KibbleCommands plugin;

    public AdminMenu(KibbleCommands plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!hasAccess(p)) {
            send(p, plugin.prefix() + "&cYou do not have permission to open this GUI.");
            return;
        }
        MenuHolder holder = new MenuHolder(ViewMode.LISTING, null);
        Inventory inv = Bukkit.createInventory(holder, 54, getTitle("KibbleCommands Admin"));
        holder.setInventory(inv);
        
        List<AliasConfig> list = new ArrayList<>(plugin.getDefinitions().values());
        list.sort(Comparator.comparing(AliasConfig::getName));
        
        int slot = 0;
        for (AliasConfig cfg : list) {
            if (slot >= 45) break;
            inv.setItem(slot, buildTag(cfg));
            holder.slots.put(slot, cfg.getName());
            slot++;
        }
        
        // Control buttons
        inv.setItem(45, buildBtn(Material.BOOK, "Help", NamedTextColor.YELLOW, "Print admin commands to chat."));
        inv.setItem(46, buildBtn(Material.ANVIL, "Add Alias", NamedTextColor.GREEN, "Use /kc add <alias> <command...>."));
        inv.setItem(48, buildBtn(Material.EMERALD_BLOCK, "Reload", NamedTextColor.GREEN, "Reload config.yml and aliases."));
        inv.setItem(49, buildBtn(Material.COMPASS, "Refresh", NamedTextColor.AQUA, "Refresh this menu."));
        inv.setItem(53, buildBtn(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu."));
        p.openInventory(inv);
    }

    private void openDetails(Player p, String name) {
        AliasConfig cfg = plugin.getDefinitions().get(name);
        if (cfg == null) {
            send(p, plugin.prefix() + "&cThat alias no longer exists.");
            open(p);
            return;
        }
        MenuHolder holder = new MenuHolder(ViewMode.DETAILS, name);
        Inventory inv = Bukkit.createInventory(holder, 27, getTitle("Alias: /" + name));
        holder.setInventory(inv);
        
        String targetText = cfg.getTargets().size() > 1 ? cfg.getTargets().size() + " commands configured" : "Target: /" + cfg.getTarget();
        inv.setItem(10, buildBtn(Material.COMMAND_BLOCK, "/" + cfg.getName(), NamedTextColor.GOLD, targetText, "Executes as: " + cfg.getExecuteAs()));
        inv.setItem(12, buildBtn(Material.TRIPWIRE_HOOK, "Permissions & Cost", NamedTextColor.YELLOW, "Permission: " + (cfg.hasPerm() ? cfg.getPermission() : "None"), "Cost: " + (cfg.getCost() > 0 ? "$" + cfg.getCost() : "Free"), "Global use permission optional in config."));
        inv.setItem(14, buildBtn(Material.CLOCK, "Rules", NamedTextColor.AQUA, "Pass args: " + getYesNo(cfg.isPassArgs()), "Cooldown: " + (cfg.getCooldown() > 0 ? cfg.getCooldown() + "s" : "disabled"), "Player only: " + getYesNo(cfg.isPlayerOnly()), "Console only: " + getYesNo(cfg.isConsoleOnly())));
        inv.setItem(16, buildBtn(Material.REDSTONE_BLOCK, "Remove Alias", NamedTextColor.RED, "Open confirmation before deleting."));
        inv.setItem(22, buildBtn(Material.ARROW, "Back", NamedTextColor.GRAY, "Return to the alias list."));
        p.openInventory(inv);
    }

    private void openConfirm(Player p, String name) {
        MenuHolder holder = new MenuHolder(ViewMode.CONFIRMATION, name);
        Inventory inv = Bukkit.createInventory(holder, 27, getTitle("Remove /" + name + "?"));
        holder.setInventory(inv);
        
        inv.setItem(11, buildBtn(Material.LIME_WOOL, "Confirm Remove", NamedTextColor.GREEN, "Delete /" + name + " from config.yml."));
        inv.setItem(15, buildBtn(Material.RED_WOOL, "Cancel", NamedTextColor.RED, "Keep this alias."));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder invHolder = e.getView().getTopInventory().getHolder();
        if (!(invHolder instanceof MenuHolder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (!hasAccess(p)) {
            p.closeInventory();
            send(p, plugin.prefix() + "&cYou do not have permission to use this GUI.");
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) {
            return;
        }
        switch (holder.view) {
            case LISTING -> handleListing(p, holder, slot);
            case DETAILS -> handleDetails(p, holder, slot);
            case CONFIRMATION -> handleConfirmation(p, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            e.setCancelled(true);
        }
    }

    private void handleListing(Player p, MenuHolder holder, int slot) {
        String name = holder.slots.get(slot);
        if (name != null) {
            openDetails(p, name);
            return;
        }
        switch (slot) {
            case 45 -> {
                p.closeInventory();
                printHelp(p);
            }
            case 46 -> {
                p.closeInventory();
                printAddInstructions(p);
            }
            case 48 -> doReload(p);
            case 49 -> open(p);
            case 53 -> p.closeInventory();
        }
    }

    private void handleDetails(Player p, MenuHolder holder, int slot) {
        switch (slot) {
            case 16 -> openConfirm(p, holder.aliasName);
            case 22 -> open(p);
        }
    }

    private void handleConfirmation(Player p, MenuHolder holder, int slot) {
        switch (slot) {
            case 11 -> {
                boolean ok = plugin.removeAlias(holder.aliasName);
                if (ok) {
                    send(p, plugin.prefix() + "&aAlias &e/" + holder.aliasName + " &aremoved successfully.");
                } else {
                    send(p, plugin.prefix() + "&cCould not remove &e/" + holder.aliasName + "&c.");
                }
                open(p);
            }
            case 15 -> openDetails(p, holder.aliasName);
        }
    }

    private void doReload(Player p) {
        if (!p.isOp() && !p.hasPermission("kibblecommands.admin") && !p.hasPermission("kibblecommands.reload")) {
            send(p, plugin.prefix() + "&cYou do not have permission to reload.");
            return;
        }
        plugin.reloadConfig();
        plugin.getCooldowns().reset();
        plugin.reloadAliases();
        send(p, plugin.prefix() + "&aConfiguration reloaded.");
        open(p);
    }

    private void printHelp(Player p) {
        send(p, "&8&m                                        ");
        send(p, "  &6&lKibbleCommands &7Admin Commands");
        send(p, "&8&m                                        ");
        send(p, "  &e/kc gui &7- Open this GUI.");
        send(p, "  &e/kc list &7- List active aliases.");
        send(p, "  &e/kc info <alias> &7- Show alias details.");
        send(p, "  &e/kc add <alias> <command...> &7- Add an alias.");
        send(p, "  &e/kc remove <alias> &7- Remove an alias.");
        send(p, "  &e/kc reload &7- Reload config.yml.");
        send(p, "&8&m                                        ");
    }

    private void printAddInstructions(Player p) {
        send(p, plugin.prefix() + "&7Use &e/kc add <alias> <command...> &7to create an alias.");
        send(p, plugin.prefix() + "&7Example: &e/kc add healme essentials:heal {player}");
        send(p, plugin.prefix() + "&7Edit &econfig.yml &7afterwards for permissions, cooldowns, and console execution.");
    }

    private ItemStack buildTag(AliasConfig cfg) {
        List<Component> lore = new ArrayList<>();
        String targetText = cfg.getTargets().size() > 1 ? cfg.getTargets().size() + " commands configured" : "Target: /" + cfg.getTarget();
        lore.add(getLine(targetText, NamedTextColor.GRAY));
        lore.add(getLine("Executes as: " + cfg.getExecuteAs(), NamedTextColor.GRAY));
        if (cfg.getCost() > 0) {
            lore.add(getLine("Cost: $" + cfg.getCost(), NamedTextColor.GOLD));
        }
        if (cfg.hasPerm()) {
            lore.add(getLine("Permission: " + cfg.getPermission(), NamedTextColor.YELLOW));
        }
        if (cfg.getCooldown() > 0) {
            lore.add(getLine("Cooldown: " + cfg.getCooldown() + "s", NamedTextColor.AQUA));
        }
        lore.add(getLine("Click to view details.", NamedTextColor.GREEN));
        return buildItem(Material.NAME_TAG, getComponent("/" + cfg.getName(), NamedTextColor.GOLD), lore);
    }

    private ItemStack buildBtn(Material m, String label, NamedTextColor c, String... desc) {
        List<Component> lore = new ArrayList<>();
        for (String s : desc) {
            lore.add(getLine(s, NamedTextColor.GRAY));
        }
        return buildItem(m, getComponent(label, c), lore);
    }

    private ItemStack buildItem(Material m, Component display, List<Component> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(display.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component getTitle(String s) {
        return getComponent(s, NamedTextColor.GOLD);
    }

    private Component getComponent(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private Component getLine(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private String getYesNo(boolean b) {
        return b ? "yes" : "no";
    }

    private boolean hasAccess(Player p) {
        return p.isOp() || p.hasPermission("kibblecommands.admin") || p.hasPermission("kibblecommands.gui");
    }

    private void send(CommandSender target, String text) {
        target.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }

    private static final class MenuHolder implements InventoryHolder {
        private final ViewMode view;
        private final String aliasName;
        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(ViewMode view, String aliasName) {
            this.view = view;
            this.aliasName = aliasName;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum ViewMode {
        LISTING,
        DETAILS,
        CONFIRMATION
    }
}
